package com.whu.graduation.taskincentive.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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

import java.util.Date;
import java.util.List;
import java.util.UUID;

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

    @Override
    public boolean save(UserTaskInstance progress) {
        progress.setId(IdWorker.getId());
        return super.save(progress);
    }

    @Override
    public boolean update(UserTaskInstance progress) {
        return super.updateById(progress);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
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
        return userTaskInstanceMapper.selectByUserId(userId);
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
            // 说明已被其他线程创建
            instance = baseMapper.selectByUserAndTask(userId, taskId);
        }

        return instance;
    }

    // ---- new methods ----

    private String buildUserTaskKey(Long userId, Long taskId) {
        return "userTask:" + userId + ":" + taskId;
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
            try {
                redisTemplate.opsForValue().set(key, JSON.toJSONString(instance));
            } catch (Exception e) {
                log.debug("redis write failed for key={}, err={}", key, e.getMessage());
            }
        }
        return instance;
    }

    @Override
    public void updateAndPublish(UserTaskInstance instance) {
        String key = buildUserTaskKey(instance.getUserId(), instance.getTaskId());
        try {
            instance.setUpdateTime(new Date());
            redisTemplate.opsForValue().set(key, JSON.toJSONString(instance));
        } catch (Exception e) {
            log.debug("redis write failed for key={}, err={}", key, e.getMessage());
        }

        try {
            // include messageId for dedup in consumer
            JSONObject wrapper = new JSONObject();
            wrapper.put("messageId", UUID.randomUUID().toString());
            wrapper.put("instance", instance);
            kafkaTemplate.send(TASK_TOPIC, instance.getUserId().toString(), wrapper.toJSONString());
        } catch (Exception e) {
            log.warn("kafka send failed for instance userId={}, taskId={}, err={}", instance.getUserId(), instance.getTaskId(), e.getMessage());
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
                // cached exists but not accepted -> treat as not accepted
                return null;
            }
        } catch (Exception e) {
            log.debug("redis read failed for key={}, err={}", key, e.getMessage());
        }

        // do not create; only read DB
        UserTaskInstance instance = baseMapper.selectByUserAndTask(userId, taskId);
        if (instance == null) return null;
        if (instance.getStatus() == null) return null;
        if (instance.getStatus() <= 0) return null;
        return instance;
    }

    @Override
    public UserTaskInstance acceptTask(Long userId, Long taskId) {
        // validate task exists and is enabled
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

        // try fetch from DB
        UserTaskInstance instance = baseMapper.selectByUserAndTask(userId, taskId);
        if (instance != null) {
            // already exists
            if (instance.getStatus() != null && instance.getStatus() > 0) {
                // 已接取或更后状态，幂等返回
                return instance; // already accepted or beyond
            }
            // else update status to ACCEPTED
            instance.setStatus(UserTaskStatus.ACCEPTED.getCode());
            instance.setUpdateTime(new Date());
            baseMapper.updateById(instance);
            // refresh cache
            try { redisTemplate.opsForValue().set(buildUserTaskKey(userId, taskId), JSON.toJSONString(instance)); } catch (Exception ignore){}
            return instance;
        }

        // create new accepted instance
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
            // write cache
            try { redisTemplate.opsForValue().set(buildUserTaskKey(userId, taskId), JSON.toJSONString(instance)); } catch (Exception ignore){}
            return instance;
        } catch (Exception e) {
            log.warn("concurrent create detected, fallback to select: {}", e.getMessage());
            instance = baseMapper.selectByUserAndTask(userId, taskId);
            if (instance != null && instance.getStatus() != null && instance.getStatus() > 0) {
                return instance;
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "接取任务失败");
        }
    }
}
