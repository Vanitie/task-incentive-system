package com.whu.graduation.taskincentive.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;

import java.util.List;

/**
 * 用户任务进度服务接口
 */
public interface UserTaskInstanceService {

    boolean save(UserTaskInstance progress);
    boolean update(UserTaskInstance progress);
    boolean deleteById(Long id);
    UserTaskInstance getById(Long id);
    List<UserTaskInstance> listAll();

    /**
     * 按用户ID查询任务进度
     * @param userId 用户ID
     * @return 用户任务进度列表
     */
    List<UserTaskInstance> selectByUserId(Long userId);

    /**
     * 仅走数据库按用户ID查询任务进度（压测对照：无缓存）
     */
    List<UserTaskInstance> selectByUserIdDirect(Long userId);

    /**
     * 根据用户ID和状态查询任务进度（支持筛选：进行中/已完成/待审核等）
     */
    List<UserTaskInstance> selectByUserIdAndStatus(Long userId, Integer status);

    /**
     * 获取或创建用户任务实例
     */
    UserTaskInstance getOrCreate(Long userId, Long taskId);

    /**
     * 乐观锁更新
     * @return 影响行数
     */
    int updateWithVersion(UserTaskInstance instance);

    // ---- new service-level methods ----

    /**
     * 带本地/Redis 缓存包装的 getOrCreate
     */
    UserTaskInstance getOrCreateWithCache(Long userId, Long taskId);

    /**
     * 更新缓存并发布异步持久化消息（Kafka）
     */
    void updateAndPublish(UserTaskInstance instance);

    /**
     * 同步持久化更新实例（压测对照：无缓存、无Kafka）
     */
    int updateDirect(UserTaskInstance instance);

    /**
     * 仅获取用户已接取的任务实例；不自动创建新实例。
     * 返回 null 表示用户未接取该任务（或未找到实例）
     */
    UserTaskInstance getAcceptedInstance(Long userId, Long taskId);

    /**
     * 用户接取任务（幂等）：如果用户未接取则创建状态为 ACCEPTED 的实例并写缓存
     */
    UserTaskInstance acceptTask(Long userId, Long taskId);

    /**
     * 分页查询用户的任务实例，可按 status 过滤
     */
    Page<UserTaskInstance> selectByUserIdPage(com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.whu.graduation.taskincentive.dao.entity.UserTaskInstance> page, Long userId, Integer status);

    /**
     * 按用户ID、任务ID、状态组合条件分页查询任务实例列表
     */
    Page<UserTaskInstance> listByConditions(Page<UserTaskInstance> page, Long userId, Long taskId, Integer status);
}