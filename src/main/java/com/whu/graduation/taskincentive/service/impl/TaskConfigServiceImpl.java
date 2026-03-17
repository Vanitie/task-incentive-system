package com.whu.graduation.taskincentive.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.dao.mapper.TaskConfigMapper;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.TaskStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.HashMap;

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
    private void evictCacheAfterCommit(Long id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    String key = CacheKeys.TASK_CONFIG_PREFIX + id;
                    try { redisTemplate.delete(key); } catch (Exception e) { log.warn("delete taskConfig from redis failed afterCommit, key={}, err={}", key, e.getMessage()); }
                    try { localTaskConfigCache.invalidate(id); } catch (Exception e) { log.warn("invalidate local cache failed afterCommit, id={}, err={}", id, e.getMessage()); }
                    log.debug("skip event->task set cleanup for taskId={}", id);
                }
            });
        } else {
            String key = CacheKeys.TASK_CONFIG_PREFIX + id;
            try { redisTemplate.delete(key); } catch (Exception e) { log.warn("delete taskConfig from redis failed, key={}, err={}", key, e.getMessage()); }
            localTaskConfigCache.invalidate(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(TaskConfig taskConfig) {
        taskConfig.setId(IdWorker.getId());
        // 修正：ruleConfig 不能为空字符串，否则 MySQL JSON 字段会报错
        if (taskConfig.getRuleConfig() == null || taskConfig.getRuleConfig().trim().isEmpty()) {
            taskConfig.setRuleConfig("{}");
        }
        boolean saved = super.save(taskConfig);
        if (!saved) return false;

        // 若为限量任务，创建库存记录（taskId 使用 taskConfig.id）
        if ("LIMITED".equalsIgnoreCase(taskConfig.getTaskType()) && taskConfig.getTotalStock() != null) {
            TaskStock stock = TaskStock.builder()
                    .taskId(taskConfig.getId())
                    .availableStock(taskConfig.getTotalStock())
                    .version(0)
                    .build();
            // 延后在事务提交后持久化库存，保证一致性
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            taskStockService.save(stock);
                        } catch (Exception e) {
                            log.warn("create taskStock after commit failed for taskId={}, err={}", taskConfig.getId(), e.getMessage());
                        }
                    }
                });
            } else {
                try { taskStockService.save(stock); } catch (Exception e) { log.warn("create taskStock failed, err={}", e.getMessage()); }
            }
        }

        // 更新缓存（事务后执行）
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

        // 简化：统一在事务提交后刷新缓存
        writeCacheAfterCommit(taskConfig);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteById(Long id) {
        boolean removed = super.removeById(id);
        if (!removed) return false;

        // 删除库存记录（若存在）以及缓存失效，均在事务提交后执行
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try { taskStockService.deleteById(id); } catch (Exception e) { log.warn("delete taskStock after commit failed for taskId={}, err={}", id, e.getMessage()); }
                    evictCacheAfterCommit(id);
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
}
