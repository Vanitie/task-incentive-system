package com.whu.graduation.taskincentive.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.common.enums.UserTaskStatus;
import com.whu.graduation.taskincentive.common.error.BusinessException;
import com.whu.graduation.taskincentive.common.error.ErrorCode;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * 用户任务进度服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTaskInstanceServiceImpl extends ServiceImpl<UserTaskInstanceMapper, UserTaskInstance>
        implements UserTaskInstanceService {

    private final UserTaskInstanceMapper userTaskInstanceMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TaskConfigService taskConfigService;

    private static final String TASK_TOPIC = "task-persist-topic";

    private String buildUserTaskKey(Long userId, Long taskId) {
        return "userTask:" + userId + ":" + taskId;
    }

    private String buildUserAcceptedSetKey(Long userId) {
        return com.whu.graduation.taskincentive.constant.CacheKeys.USER_ACCEPTED_PREFIX + userId;
    }

    @Override
    public boolean save(UserTaskInstance progress) {
        progress.setId(IdWorker.getId());
        return super.save(progress);
    }

    @Override
    public boolean update(UserTaskInstance progress) {
        boolean updated = super.updateById(progress);
        // 如果状态变为已接取，尽可能保证将 taskId 写入用户已接取集合（best-effort）
        if (updated && progress.getStatus() != null && progress.getStatus() > 0) {
            // 在事务提交后再执行 SADD 与缓存写入，避免事务回滚导致缓存脏数据
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try { redisTemplate.opsForSet().add(buildUserAcceptedSetKey(progress.getUserId()), String.valueOf(progress.getTaskId())); } catch (Exception e) { log.debug("sadd user accepted failed afterCommit, err={}", e.getMessage()); }
                        try { redisTemplate.opsForValue().set(buildUserTaskKey(progress.getUserId(), progress.getTaskId()), JSON.toJSONString(progress)); } catch (Exception e) { log.debug("redis set userTask failed afterCommit, err={}", e.getMessage()); }
                    }
                });
            } else {
                try { redisTemplate.opsForSet().add(buildUserAcceptedSetKey(progress.getUserId()), String.valueOf(progress.getTaskId())); } catch (Exception ignore) {}
                try { redisTemplate.opsForValue().set(buildUserTaskKey(progress.getUserId(), progress.getTaskId()), JSON.toJSONString(progress)); } catch (Exception ignore) {}
            }
        }
        return updated;
    }

    @Override
    public boolean deleteById(Long id) {
        // 先读取待删除实例以获知 userId/taskId
        UserTaskInstance inst = super.getById(id);
        boolean removed = super.removeById(id);
        if (!removed) return false;

        // 若该实例为已接取状态，事务提交后从用户集合移除并从缓存删除对应实例
        if (inst != null && inst.getUserId() != null && inst.getTaskId() != null && inst.getStatus() != null && inst.getStatus() > 0) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try { redisTemplate.opsForSet().remove(buildUserAcceptedSetKey(inst.getUserId()), String.valueOf(inst.getTaskId())); } catch (Exception e) { log.debug("srem user accepted failed afterCommit, err={}", e.getMessage()); }
                        try { redisTemplate.delete(buildUserTaskKey(inst.getUserId(), inst.getTaskId())); } catch (Exception e) { log.debug("redis delete userTask failed afterCommit, err={}", e.getMessage()); }
                    }
                });
            } else {
                try { redisTemplate.opsForSet().remove(buildUserAcceptedSetKey(inst.getUserId()), String.valueOf(inst.getTaskId())); } catch (Exception ignore) {}
                try { redisTemplate.delete(buildUserTaskKey(inst.getUserId(), inst.getTaskId())); } catch (Exception ignore) {}
            }
        }
        return true;
    }

    @Override
    public UserTaskInstance getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<UserTaskInstance> listAll() {
        return super.list();
    }

    @Override
    public List<UserTaskInstance> selectByUserId(Long userId) {
        // 1. 从 Redis set 获取用户已接取任务ID集合
        String acceptedSetKey = buildUserAcceptedSetKey(userId);
        List<UserTaskInstance> result = new java.util.ArrayList<>();
        java.util.Set<String> taskIdSet = null;
        try {
            taskIdSet = redisTemplate.opsForSet().members(acceptedSetKey);
            if (taskIdSet != null && !taskIdSet.isEmpty()) {
                // 2. 拼接所有缓存key
                java.util.List<String> keys = new java.util.ArrayList<>();
                for (String taskId : taskIdSet) {
                    keys.add(buildUserTaskKey(userId, Long.valueOf(taskId)));
                }
                // 3. 批量获取缓存
                java.util.List<String> values = redisTemplate.opsForValue().multiGet(keys);
                if (values != null) {
                    for (String json : values) {
                        if (json != null) {
                            try {
                                UserTaskInstance instance = JSON.parseObject(json, UserTaskInstance.class);
                                if (instance != null) result.add(instance);
                            } catch (Exception ignore) {}
                        }
                    }
                }
                // 4. 若全部命中缓存则直接返回
                if (result.size() == taskIdSet.size()) return result;
            }
        } catch (Exception e) {
            log.debug("redis batch read failed for userId={}, err={}", userId, e.getMessage());
        }
        // 5. 缓存未命中或部分未命中，回退到数据库查询
        List<UserTaskInstance> dbResult = userTaskInstanceMapper.selectByUserId(userId);
        if (dbResult != null && !dbResult.isEmpty()) {
            // 回写缓存（只写未命中的）
            Set<String> newTaskIdSet = new HashSet<>();
            for (UserTaskInstance instance : dbResult) {
                String key = buildUserTaskKey(userId, instance.getTaskId());
                try {
                    redisTemplate.opsForValue().set(key, JSON.toJSONString(instance), 10, java.util.concurrent.TimeUnit.MINUTES);
                } catch (Exception ignore) {}
                newTaskIdSet.add(String.valueOf(instance.getTaskId()));
            }
            // 回写set
            try {
                redisTemplate.opsForSet().add(acceptedSetKey, newTaskIdSet.toArray(new String[0]));
            } catch (Exception ignore) {}
        }
        return dbResult;
    }

    @Override
    public List<UserTaskInstance> selectByUserIdAndStatus(Long userId, Integer status) {
        return userTaskInstanceMapper.selectByUserIdAndStatus(userId, status);
    }

    @Override
    public Page<UserTaskInstance> selectByUserIdPage(com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserTaskInstance> page, Long userId, Integer status) {
        QueryWrapper<UserTaskInstance> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        if (status != null) wrapper.eq("status", status);
        wrapper.orderByDesc("update_time");
        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    public int updateWithVersion(UserTaskInstance instance) {
        return userTaskInstanceMapper.updateWithVersion(instance);
    }

    @Override
    public UserTaskInstance getOrCreate(Long userId, Long taskId) {

        UserTaskInstance instance =
                baseMapper.selectByUserAndTask(userId, taskId);

        if (instance != null) {
            return instance;
        }

        instance = new UserTaskInstance();
        instance.setId(IdWorker.getId());
        instance.setUserId(userId);
        instance.setTaskId(taskId);
        instance.setProgress(0);
        instance.setStatus(0);
        instance.setVersion(0);

        try {
            baseMapper.insert(instance);
        } catch (Exception e) {
            // 并发创建时回退到查询以确保幂等
            instance = baseMapper.selectByUserAndTask(userId, taskId);
        }

        return instance;
    }

    @Override
    public UserTaskInstance getOrCreateWithCache(Long userId, Long taskId) {
        String key = buildUserTaskKey(userId, taskId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return JSON.parseObject(json, UserTaskInstance.class);
            }
        } catch (Exception e) {
            log.debug("redis read failed for key={}, err={}", key, e.getMessage());
        }

        UserTaskInstance instance = this.getOrCreate(userId, taskId);
        if (instance != null) {
            // 为避免事务回滚后缓存脏数据：在事务提交后再回写缓存；无事务则直接写
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                UserTaskInstance toCache = instance;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try { redisTemplate.opsForValue().set(key, JSON.toJSONString(toCache)); } catch (Exception e) { log.debug("redis write failed for key={}, err={}", key, e.getMessage()); }
                    }
                });
            } else {
                try { redisTemplate.opsForValue().set(key, JSON.toJSONString(instance)); } catch (Exception e) { log.debug("redis write failed for key={}, err={}", key, e.getMessage()); }
            }
        }
        return instance;
    }

    @Override
    public void updateAndPublish(UserTaskInstance instance) {
        String key = buildUserTaskKey(instance.getUserId(), instance.getTaskId());
        // 准备要写入缓存和发送到 Kafka 的负载
        String payload = null;
        try {
            instance.setUpdateTime(new Date());
            payload = JSON.toJSONString(instance);
        } catch (Exception e) {
            log.debug("serialize instance failed, err={}", e.getMessage());
        }

        final String finalPayload = payload;
        // 若当前在事务中，则把 Redis 写和 Kafka 发送安排到事务提交后执行，避免未提交的数据被广播
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (finalPayload != null) {
                        try { redisTemplate.opsForValue().set(key, finalPayload); } catch (Exception e) { log.debug("redis write failed for key={}, err={}", key, e.getMessage()); }
                    }

                    try {
                        JSONObject wrapper = new JSONObject();
                        wrapper.put("messageId", UUID.randomUUID().toString());
                        wrapper.put("instance", instance);
                        kafkaTemplate.send(TASK_TOPIC, instance.getUserId().toString(), wrapper.toJSONString());
                    } catch (Exception e) {
                        log.warn("kafka send failed for instance userId={}, taskId={}, err={}", instance.getUserId(), instance.getTaskId(), e.getMessage());
                    }
                }
            });
        } else {
            if (finalPayload != null) {
                try { redisTemplate.opsForValue().set(key, finalPayload); } catch (Exception e) { log.debug("redis write failed for key={}, err={}", key, e.getMessage()); }
            }
            try {
                JSONObject wrapper = new JSONObject();
                wrapper.put("messageId", UUID.randomUUID().toString());
                wrapper.put("instance", instance);
                kafkaTemplate.send(TASK_TOPIC, instance.getUserId().toString(), wrapper.toJSONString());
            } catch (Exception e) {
                log.warn("kafka send failed for instance userId={}, taskId={}, err={}", instance.getUserId(), instance.getTaskId(), e.getMessage());
            }
        }
    }

    @Override
    public UserTaskInstance getAcceptedInstance(Long userId, Long taskId) {
        String key = buildUserTaskKey(userId, taskId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                UserTaskInstance cached = JSON.parseObject(json, UserTaskInstance.class);
                if (cached != null && cached.getStatus() != null && cached.getStatus() > 0) {
                    return cached;
                }
                // 缓存存在但不是已接取状态，视为未接取
                return null;
            }
        } catch (Exception e) {
            log.debug("redis read failed for key={}, err={}", key, e.getMessage());
        }

        // 不创建新实例，仅从 DB 读取
        UserTaskInstance instance = baseMapper.selectByUserAndTask(userId, taskId);
        if (instance == null) return null;
        if (instance.getStatus() == null) return null;
        if (instance.getStatus() <= 0) return null;
        return instance;
    }

    @Override
    public UserTaskInstance acceptTask(Long userId, Long taskId) {
        // 校验任务存在且已启用/在时间窗口内
        TaskConfig config = taskConfigService.getTaskConfig(taskId);
        if (config == null) {
            log.warn("taskConfig not found for taskId={}", taskId);
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        if (config.getStatus() == null || config.getStatus() != 1) {
            log.warn("task {} is not enabled", taskId);
            throw new BusinessException(ErrorCode.TASK_NOT_ENABLED);
        }
        Date now = new Date();
        if (config.getStartTime() != null && now.before(config.getStartTime())) {
            log.warn("task {} not started yet", taskId);
            throw new BusinessException(ErrorCode.TASK_NOT_STARTED);
        }
        if (config.getEndTime() != null && now.after(config.getEndTime())) {
            log.warn("task {} already ended", taskId);
            throw new BusinessException(ErrorCode.TASK_ALREADY_ENDED);
        }

        // 尝试从 DB 获取现有实例
        UserTaskInstance instance = baseMapper.selectByUserAndTask(userId, taskId);
        if (instance != null) {
            // 已存在实例
            if (instance.getStatus() != null && instance.getStatus() > 0) {
                // 已接取或更高状态，幂等返回
                return instance; // already accepted or beyond
            }
            // 否则把状态更新为已接取并写缓存（在事务提交后执行）
            instance.setStatus(UserTaskStatus.ACCEPTED.getCode());
            instance.setUpdateTime(new Date());
            baseMapper.updateById(instance);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                UserTaskInstance toCache = instance;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try { redisTemplate.opsForValue().set(buildUserTaskKey(userId, taskId), JSON.toJSONString(toCache)); } catch (Exception e) { log.debug("redis write failed for key={}, err={}", buildUserTaskKey(userId, taskId), e.getMessage()); }
                        try { redisTemplate.opsForSet().add(buildUserAcceptedSetKey(userId), String.valueOf(taskId)); } catch (Exception e) { log.debug("sadd user accepted failed afterCommit, err={}", e.getMessage()); }
                    }
                });
            } else {
                try { redisTemplate.opsForValue().set(buildUserTaskKey(userId, taskId), JSON.toJSONString(instance)); } catch (Exception ignore){}
                try { redisTemplate.opsForSet().add(buildUserAcceptedSetKey(userId), String.valueOf(taskId)); } catch (Exception ignore){}
            }

            return instance;
        }

        // 创建新的已接取实例并写入 DB，随后在事务提交后写缓存与集合
        instance = new UserTaskInstance();
        instance.setId(IdWorker.getId());
        instance.setUserId(userId);
        instance.setTaskId(taskId);
        instance.setProgress(0);
        instance.setStatus(UserTaskStatus.ACCEPTED.getCode());
        instance.setVersion(0);
        instance.setCreateTime(new Date());
        instance.setUpdateTime(new Date());

        try {
            baseMapper.insert(instance);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                UserTaskInstance toCache = instance;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try { redisTemplate.opsForValue().set(buildUserTaskKey(userId, taskId), JSON.toJSONString(toCache)); } catch (Exception e) { log.debug("redis write failed for key={}, err={}", buildUserTaskKey(userId, taskId), e.getMessage()); }
                        try { redisTemplate.opsForSet().add(buildUserAcceptedSetKey(userId), String.valueOf(taskId)); } catch (Exception e) { log.debug("sadd user accepted failed afterCommit, err={}", e.getMessage()); }
                    }
                });
            } else {
                try { redisTemplate.opsForValue().set(buildUserTaskKey(userId, taskId), JSON.toJSONString(instance)); } catch (Exception ignore){}
                try { redisTemplate.opsForSet().add(buildUserAcceptedSetKey(userId), String.valueOf(taskId)); } catch (Exception ignore){}
            }

            return instance;
        } catch (Exception e) {
            // 并发创建回退逻辑
            log.warn("concurrent create detected, fallback to select: {}", e.getMessage());
            instance = baseMapper.selectByUserAndTask(userId, taskId);
            if (instance != null && instance.getStatus() != null && instance.getStatus() > 0) {
                return instance;
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "接取任务失败");
        }
    }

    @Override
    public Page<UserTaskInstance> listByConditions(Page<UserTaskInstance> page, Long userId, Long taskId, Integer status) {
        QueryWrapper<UserTaskInstance> wrapper = new QueryWrapper<>();
        if (userId != null) wrapper.eq("user_id", userId);
        if (taskId != null) wrapper.eq("task_id", taskId);
        if (status != null) wrapper.eq("status", status);
        wrapper.orderByDesc("update_time");
        return this.baseMapper.selectPage(page, wrapper);
    }
}
