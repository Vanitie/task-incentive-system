package com.whu.graduation.taskincentive.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
   import com.whu.graduation.taskincentive.dao.entity.TaskConfigHistory;
import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.dao.mapper.TaskConfigMapper;
import com.whu.graduation.taskincentive.dao.mapper.TaskConfigHistoryMapper;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.TaskStockService;
import com.whu.graduation.taskincentive.dto.StairRuleConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Date;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * 任务配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskConfigServiceImpl extends ServiceImpl<TaskConfigMapper, TaskConfig>
        implements TaskConfigService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private TaskStockService taskStockService;

    @Autowired(required = false)
    private TaskConfigHistoryMapper taskConfigHistoryMapper;

    private final StringRedisTemplate stringRedisTemplate;
    private static final String TASK_CONFIG_CREATE_TIME_PREFIX = "task_config_create_time:";

    // 本地缓存
    private final Cache<Long, TaskConfig> localTaskConfigCache = Caffeine.newBuilder()
            .maximumSize(1024)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    /**
     * 事务提交后写入缓存，避免在事务中执行可能失败的 Redis 操作导致事务回滚后缓存不一致问题
     * @param taskConfig 任务配置对象
     */
    private void writeCacheAfterCommit(TaskConfig taskConfig) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    String key = CacheKeys.TASK_CONFIG_PREFIX + taskConfig.getId();
                    try {
                        redisTemplate.opsForValue().set(key, JSON.toJSONString(taskConfig), 60, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("write taskConfig to redis failed afterCommit, key={}, err={}", key, e.getMessage());
                    }
                    try { localTaskConfigCache.put(taskConfig.getId(), taskConfig); } catch (Exception e) { log.warn("write taskConfig to local cache failed afterCommit, id={}, err={}", taskConfig.getId(), e.getMessage()); }
                }
            });
        } else {
            String key = CacheKeys.TASK_CONFIG_PREFIX + taskConfig.getId();
            try { redisTemplate.opsForValue().set(key, JSON.toJSONString(taskConfig), 60, TimeUnit.SECONDS); } catch (Exception e) { log.warn("write taskConfig to redis failed, key={}, err={}", key, e.getMessage()); }
            localTaskConfigCache.put(taskConfig.getId(), taskConfig);
        }
    }

    /**
     * 事务提交后清除缓存，避免在事务中执行可能失败的 Redis 操作导致事务回滚后缓存不一致问题
     * @param id 任务配置 ID
     */
    private void evictCacheNow(Long id) {
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        try { redisTemplate.delete(key); } catch (Exception e) { log.warn("delete taskConfig from redis failed, key={}, err={}", key, e.getMessage()); }
        localTaskConfigCache.invalidate(id);
    }

    private void evictCacheAfterCommit(Long id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try { evictCacheNow(id); } catch (Exception e) { log.warn("evict taskConfig cache afterCommit failed for id={}, err={}", id, e.getMessage()); }
                    log.debug("skip event->task set cleanup for taskId={}", id);
                }
            });
        } else {
            evictCacheNow(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(TaskConfig taskConfig) {
        taskConfig.setId(IdWorker.getId());
        if (taskConfig.getRuleConfig() == null || taskConfig.getRuleConfig().trim().isEmpty()) {
            taskConfig.setRuleConfig("{}");
        }
        boolean saved = super.save(taskConfig);
        if (!saved) return false;
        // 打点：记录创建时间
        stringRedisTemplate.opsForValue().set(TASK_CONFIG_CREATE_TIME_PREFIX + taskConfig.getId(), String.valueOf(System.currentTimeMillis()));
        // 根据库存类型维护库存表
        if ("LIMITED".equalsIgnoreCase(taskConfig.getStockType())) {
            createTaskStock(taskConfig);
        } else {
            // 非限量任务清理库存表
            taskStockService.deleteById(taskConfig.getId());
        }
        recordHistorySnapshot(taskConfig, "CREATE");
        System.out.println("[打点] 任务配置创建: taskId=" + taskConfig.getId() + ", 时间=" + System.currentTimeMillis());
        writeCacheAfterCommit(taskConfig);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(TaskConfig taskConfig) {
        // 修正：ruleConfig 不能为空字符串，否则 MySQL JSON 字段会报错
        if (taskConfig.getRuleConfig() == null || taskConfig.getRuleConfig().trim().isEmpty()) {
            taskConfig.setRuleConfig("{}");
        }
        boolean updated = super.updateById(taskConfig);
        if (!updated) return false;
        // 打点：记录更新时间
        stringRedisTemplate.opsForValue().set(TASK_CONFIG_CREATE_TIME_PREFIX + taskConfig.getId(), String.valueOf(System.currentTimeMillis()));
        // 根据库存类型维护库存表
        if ("LIMITED".equalsIgnoreCase(taskConfig.getStockType())) {
            TaskStock stock = new TaskStock();
            stock.setTaskId(taskConfig.getId());
            stock.setAvailableStock(taskConfig.getTotalStock() != null ? taskConfig.getTotalStock() : 0);
            stock.setVersion(0);
            stock.setUpdateTime(new Date());
            taskStockService.update(stock);
        } else {
            taskStockService.deleteById(taskConfig.getId());
        }
        recordHistorySnapshot(taskConfig, "UPDATE");
        System.out.println("[打点] 任务配置更新: taskId=" + taskConfig.getId() + ", 时间=" + System.currentTimeMillis());
        writeCacheAfterCommit(taskConfig);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteById(Long id) {
        // 使用 mapper 直接删除，避免在轻量单测环境下依赖 MyBatis TableInfo 元数据
        boolean removed = this.baseMapper.deleteById(id) > 0;
        if (!removed) return false;

        // 删除库存记录（若存在）以及缓存失效，均在事务提交后执行
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try { taskStockService.deleteById(id); } catch (Exception e) { log.warn("delete taskStock after commit failed for taskId={}, err={}", id, e.getMessage()); }
                    try { evictCacheNow(id); } catch (Exception e) { log.warn("evict taskConfig cache after commit failed for taskId={}, err={}", id, e.getMessage()); }
                }
            });
        } else {
            try { taskStockService.deleteById(id); } catch (Exception ignore) {}
            evictCacheAfterCommit(id);
        }

        return true;
    }

    @Override
    public TaskConfig getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<TaskConfig> listAll() {
        return super.list();
    }

    @Override
    public List<TaskConfig> selectByTaskName(String taskName) {
        return this.baseMapper.selectByTaskName(taskName);
    }

    @Override
    public Page<TaskConfig> selectByTaskTypePage(Page<TaskConfig> page, String taskType) {
        page.setRecords(this.baseMapper.selectByTaskTypePage(taskType, page));
        return page;
    }

    @Override
    public Page<TaskConfig> selectByStatusPage(Page<TaskConfig> page, Integer status) {
        page.setRecords(this.baseMapper.selectByStatusPage(status, page));
        return page;
    }

    @Override
    public Page<TaskConfig> selectPage(Page<TaskConfig> page) {
        return this.baseMapper.selectPage(page, null);
    }

    @Override
    public TaskConfig getTaskConfig(Long taskId) {
        //1.本地缓存
        TaskConfig config = localTaskConfigCache.getIfPresent(taskId);
        if (config != null) return config;

        //2.Redis缓存
        String key = CacheKeys.TASK_CONFIG_PREFIX + taskId;
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            try {
                config = JSON.parseObject(json, TaskConfig.class);
                localTaskConfigCache.put(taskId, config);
                return config;
            } catch (Exception e) {
                log.warn("parse taskConfig json failed, key={}, err={}", key, e.getMessage());
            }
        }

        //3.DB查询并回填缓存
        config = super.getById(taskId);
        if (config != null) {
            try {
                redisTemplate.opsForValue().set(key, JSON.toJSONString(config), 60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("write taskConfig to redis failed, key={}, err={}", key, e.getMessage());
            }
            localTaskConfigCache.put(taskId, config);
        }
        return config;
    }

    @Override
    public void invalidateTaskConfig(Long taskId) {
        localTaskConfigCache.invalidate(taskId);
    }

    @Override
    public void refreshTaskConfig(Long taskId) {
        String key = CacheKeys.TASK_CONFIG_PREFIX + taskId;
        // 尝试从 Redis 获取最新配置并刷新本地缓存
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                TaskConfig config = JSON.parseObject(json, TaskConfig.class);
                localTaskConfigCache.put(taskId, config);
                return;
            }
        } catch (Exception e) {
            log.warn("refresh read from redis failed, key={}, err={}", key, e.getMessage());
        }

        // 从 DB 获取最新配置并刷新 Redis + 本地缓存
        TaskConfig config = super.getById(taskId);
        if (config != null) {
            try {
                redisTemplate.opsForValue().set(key, JSON.toJSONString(config), 60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("refresh write to redis failed, key={}, err={}", key, e.getMessage());
            }
            localTaskConfigCache.put(taskId, config);
        } else {
            // DB 中已不存在该配置，清除 Redis 和本地缓存
            localTaskConfigCache.invalidate(taskId);
        }
    }

    @Override
    public Set<String> getTaskIdsByEventType(String eventType) {
        String key = CacheKeys.EVENT_TASKS_PREFIX + eventType;
        try {
            Set<String> members = redisTemplate.opsForSet().members(key);
            if (members != null && !members.isEmpty()) return members;
        } catch (Exception e) {
            log.warn("read event->task set from redis failed, key={}, err={}", key, e.getMessage());
        }

        // 从 DB 查询关联的 taskId 列表并回填 Redis
        try {
            List<TaskConfig> list = this.baseMapper.selectList(new QueryWrapper<TaskConfig>().eq("trigger_event", eventType));
            if (list != null && !list.isEmpty()) {
                Set<String> ids = list.stream().map(tc -> String.valueOf(tc.getId())).collect(Collectors.toSet());
                try { redisTemplate.opsForSet().add(key, ids.toArray(new String[0])); } catch (Exception ignore) {}
                return ids;
            }
        } catch (Throwable t) {
            log.debug("db query for trigger_event failed: {}", t.getMessage());
        }

        return java.util.Collections.emptySet();
    }

    @Override
    public Set<String> getTaskIdsByEventTypeDirect(String eventType) {
        if (eventType == null || eventType.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        List<TaskConfig> list = this.baseMapper.selectList(new QueryWrapper<TaskConfig>().eq("trigger_event", eventType));
        if (list == null || list.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        return list.stream()
                .filter(java.util.Objects::nonNull)
                .map(TaskConfig::getId)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toSet());
    }

    @Override
    public Map<Long, TaskConfig> getTaskConfigsByIds(Set<Long> ids) {
        Map<Long, TaskConfig> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) return result;

        // 1.首先尝试从本地缓存获取，记录缺失的 id 列表
        Set<Long> missing = ids.stream().filter(id -> {
            TaskConfig v = localTaskConfigCache.getIfPresent(id);
            if (v != null) result.put(id, v);
            return v == null;
        }).collect(Collectors.toSet());

        if (missing.isEmpty()) return result;

        // 2. Redis multiGet 获取缺失的配置，回填本地缓存，记录仍然缺失的 id 列表
        List<String> keys = missing.stream().map(id -> CacheKeys.TASK_CONFIG_PREFIX + id).collect(Collectors.toList());
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            int idx = 0;
            Set<Long> stillMissing = new java.util.HashSet<>();
            for (Long id : missing) {
                String json = (values != null && idx < values.size()) ? values.get(idx) : null;
                idx++;
                if (json != null) {
                    try {
                        TaskConfig cfg = JSON.parseObject(json, TaskConfig.class);
                        if (cfg != null) {
                            result.put(id, cfg);
                            localTaskConfigCache.put(id, cfg);
                            continue;
                        }
                    } catch (Exception e) {
                        log.debug("parse taskConfig json failed for id={}, err={}", id, e.getMessage());
                    }
                }
                stillMissing.add(id);
            }
            missing = stillMissing;
        } catch (Exception e) {
            log.debug("redis multiGet failed, fallback to DB, err={}", e.getMessage());
        }

        if (missing.isEmpty()) return result;

        // 3. DB 批量查询缺失的配置，回填 Redis 和本地缓存
        try {
            List<TaskConfig> dbList = this.baseMapper.selectBatchIds(missing);
            if (dbList != null && !dbList.isEmpty()) {
                for (TaskConfig cfg : dbList) {
                    if (cfg != null && cfg.getId() != null) {
                        result.put(cfg.getId(), cfg);
                        // populate caches
                        try { redisTemplate.opsForValue().set(CacheKeys.TASK_CONFIG_PREFIX + cfg.getId(), JSON.toJSONString(cfg), 60, TimeUnit.SECONDS); } catch (Exception ignore) {}
                        localTaskConfigCache.put(cfg.getId(), cfg);
                        missing.remove(cfg.getId());
                    }
                }
            }
        } catch (Throwable t) {
            log.debug("db batch query for taskConfigs failed: {}", t.getMessage());
        }

        // 仍然缺失的 id 可视为无效，记录日志后忽略
        return result;
    }

    @Override
    public Map<Long, TaskConfig> getTaskConfigsByIdsDirect(Set<Long> ids) {
        Map<Long, TaskConfig> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<TaskConfig> dbList = this.baseMapper.selectBatchIds(ids);
        if (dbList == null || dbList.isEmpty()) {
            return result;
        }
        for (TaskConfig cfg : dbList) {
            if (cfg != null && cfg.getId() != null) {
                result.put(cfg.getId(), cfg);
            }
        }
        return result;
    }

    @Override
    public Page<TaskConfig> searchByConditions(String taskName, String taskType, Integer status, String rewardType, Page<TaskConfig> page) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TaskConfig> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (taskName != null && !taskName.isEmpty()) {
            wrapper.like("task_name", taskName);
        }
        if (taskType != null && !taskType.isEmpty()) {
            wrapper.eq("task_type", taskType);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (rewardType != null && !rewardType.isEmpty()) {
            wrapper.eq("reward_type", rewardType);
        }
        return super.page(page, wrapper);
    }

    @Override
    public List<TaskConfigHistory> listHistoryByTaskId(Long taskId) {
        if (taskId == null || taskConfigHistoryMapper == null) {
            return java.util.Collections.emptyList();
        }
        try {
            List<TaskConfigHistory> rows = taskConfigHistoryMapper.selectByTaskIdOrderByVersionDesc(taskId);
            return rows == null ? java.util.Collections.emptyList() : rows;
        } catch (Exception e) {
            log.warn("query task config history failed, taskId={}, err={}", taskId, e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public int warmupAllTaskConfigs(int batchSize, long redisTtlSeconds) {
        return warmupAllTaskConfigs(batchSize, redisTtlSeconds, true);
    }

    @Override
    public int warmupAllTaskConfigs(int batchSize, long redisTtlSeconds, boolean writeRedis) {
        int safeBatchSize = Math.max(1, batchSize);
        long safeRedisTtl = redisTtlSeconds > 0 ? redisTtlSeconds : 60L;

        List<TaskConfig> all = this.baseMapper.selectList(null);
        if (all == null || all.isEmpty()) {
            return 0;
        }

        Map<String, Set<String>> eventTaskIdMap = new HashMap<>();
        int warmed = 0;
        for (TaskConfig cfg : all) {
            if (cfg == null || cfg.getId() == null) {
                continue;
            }
            Long taskId = cfg.getId();
            String cacheKey = CacheKeys.TASK_CONFIG_PREFIX + taskId;

            localTaskConfigCache.put(taskId, cfg);
            if (writeRedis) {
                try {
                    redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(cfg), safeRedisTtl, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("warmup write taskConfig to redis failed, key={}, err={}", cacheKey, e.getMessage());
                }
            }

            if (writeRedis && cfg.getTriggerEvent() != null && !cfg.getTriggerEvent().trim().isEmpty()) {
                eventTaskIdMap.computeIfAbsent(cfg.getTriggerEvent(), k -> new HashSet<>()).add(String.valueOf(taskId));
            }

            warmed++;
            if (warmed % safeBatchSize == 0) {
                log.debug("task config warmup progress: {}/{}", warmed, all.size());
            }
        }

        if (writeRedis) {
            for (Map.Entry<String, Set<String>> e : eventTaskIdMap.entrySet()) {
                if (e.getValue().isEmpty()) {
                    continue;
                }
                String key = CacheKeys.EVENT_TASKS_PREFIX + e.getKey();
                try {
                    redisTemplate.opsForSet().add(key, e.getValue().toArray(new String[0]));
                    redisTemplate.expire(key, safeRedisTtl, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    log.warn("warmup write event->task set failed, key={}, err={}", key, ex.getMessage());
                }
            }
        }

        return warmed;
    }

    /**
     * 创建限量任务时，若为阶梯任务，为每个阶梯插入一条库存记录，其他任务插入一条stageIndex=1
     */
    private void createTaskStock(TaskConfig config) {
        if ("STAIR".equals(config.getTaskType()) && config.getStockType() != null && config.getStockType().equalsIgnoreCase("LIMITED") && config.getRuleConfig() != null) {
            StairRuleConfig ruleConfig= JSON.parseObject(config.getRuleConfig(), StairRuleConfig.class);
            if(ruleConfig.getStages() != null && !ruleConfig.getStages().isEmpty()){
                // 先删除旧库存记录（如果有），再创建新记录
                taskStockService.deleteById(config.getId());
            }
            for (int i = 0; i < ruleConfig.getStages().size(); i++) {
                TaskStock stock = TaskStock.builder()
                        .taskId(config.getId())
                        .stageIndex(i + 1)
                        .availableStock(config.getTotalStock()) // 可根据实际需求调整每阶梯库存
                        .version(0)
                        .build();
                taskStockService.save(stock);
            }
        } else if (config.getStockType() != null && config.getStockType().equalsIgnoreCase("LIMITED")) {
            // 普通限量任务
            TaskStock stock = TaskStock.builder()
                    .taskId(config.getId())
                    .stageIndex(1)
                    .availableStock(config.getTotalStock())
                    .version(0)
                    .build();
            taskStockService.save(stock);
        }
    }

    private void recordHistorySnapshot(TaskConfig config, String changeType) {
        if (config == null || config.getId() == null || taskConfigHistoryMapper == null) {
            return;
        }
        try {
            Integer maxVersion = taskConfigHistoryMapper.selectMaxVersionNo(config.getId());
            int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;
            TaskConfigHistory history = TaskConfigHistory.builder()
                    .id(IdWorker.getId())
                    .taskId(config.getId())
                    .versionNo(nextVersion)
                    .taskName(config.getTaskName())
                    .taskType(config.getTaskType())
                    .stockType(config.getStockType())
                    .triggerEvent(config.getTriggerEvent())
                    .ruleConfig(config.getRuleConfig())
                    .rewardType(config.getRewardType())
                    .rewardValue(config.getRewardValue())
                    .totalStock(config.getTotalStock())
                    .status(config.getStatus())
                    .startTime(config.getStartTime())
                    .endTime(config.getEndTime())
                    .sourceUpdateTime(config.getUpdateTime())
                    .changeType(changeType)
                    .changedBy("system")
                    .build();
            taskConfigHistoryMapper.insert(history);
        } catch (Exception e) {
            log.warn("record task config history failed, taskId={}, err={}", config.getId(), e.getMessage());
        }
    }
}
