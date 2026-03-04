package com.whu.graduation.taskincentive.service;

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
     * 获取或创建用户任务实例
     */
    UserTaskInstance getOrCreate(Long userId, Long taskId);

    /**
     * 乐观锁更新
     * @return 影响行数
     */
    int updateWithVersion(UserTaskInstance instance);
}