package com.whu.graduation.taskincentive.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.mapper.TaskConfigMapper;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    // 本地缓存
    private final Cache<Long, TaskConfig> localTaskConfigCache = Caffeine.newBuilder()
            .maximumSize(1024)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    @Override
    public boolean save(TaskConfig taskConfig) {
        taskConfig.setId(IdWorker.getId());
        return super.save(taskConfig);
    }

    @Override
    public boolean update(TaskConfig taskConfig) {
        return super.updateById(taskConfig);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
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
}
