package com.whu.graduation.taskincentive.service;

import com.whu.graduation.taskincentive.dao.entity.UserTaskProgress;

import java.util.List;

/**
 * 用户任务进度服务接口
 */
public interface UserTaskProgressService {

    boolean save(UserTaskProgress progress);
    boolean update(UserTaskProgress progress);
    boolean deleteById(Long id);
    UserTaskProgress getById(Long id);
    List<UserTaskProgress> listAll();

    /**
     * 按用户ID查询任务进度
     * @param userId 用户ID
     * @return 用户任务进度列表
     */
    List<UserTaskProgress> selectByUserId(Long userId);
}