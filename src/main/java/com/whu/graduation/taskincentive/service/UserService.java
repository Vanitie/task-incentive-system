package com.whu.graduation.taskincentive.service;

import com.whu.graduation.taskincentive.dao.entity.User;

import java.util.List;

/**
 * 用户服务接口
 * 提供用户CRUD及积分更新等方法
 */
public interface UserService {

    /**
     * 新增用户
     * 使用雪花ID生成用户ID
     * @param user 用户对象
     * @return true表示保存成功
     */
    boolean save(User user);

    boolean update(User user);
    boolean deleteById(Long id);
    User getById(Long id);
    List<User> listAll();

    /**
     * 按用户名查询用户
     * @param username 用户名
     * @return 用户对象
     */
    User selectByUsername(String username);

    /**
     * 更新用户积分
     * @param userId 用户ID
     * @param points 积分变化值，可为正或负
     * @return true表示更新成功
     */
    boolean updateUserPoints(Long userId, Integer points);
}