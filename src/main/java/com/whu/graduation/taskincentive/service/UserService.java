package com.whu.graduation.taskincentive.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.User;

import java.util.Date;
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

    /**
     * 注册用户（会对密码进行加密并存储角色）
     */
    boolean register(User user, String rawPassword, String roles);

    /**
     * 验证用户名密码并返回用户对象（不包含密码）
     */
    User authenticate(String username, String rawPassword);

    /** 分页查询用户 */
    Page<User> selectPage(Page<User> page);

    // ---------- 统计相关接口 ----------
    /**
     * 查询用户表总数
     */
    long countAllUsers();

    /**
     * 统计从指定时间点以来，接取过任务的不重复用户数
     */
    long countActiveUsersSince(Date since);

    /**
     * 统计今日接取过任务的不重复用户数
     */
    long countUsersToday();

    /**
     * 获取过去 7 天内每天的用户总数（按创建日期统计），顺序从 6 天前到今天
     */
    List<Long> getUserCountLast7Days();

    /**
     * 获取过去 7 天内每天的活跃用户数（接取任务的去重 user_id），顺序从 6 天前到今天
     */
    List<Long> getActiveUserCountLast7Days();

    /**
     * 获取过去 7 天内每天的接取过任务的去重用户数，顺序从 6 天前到今天
     */
    List<Long> getTaskReceiveUserCountLast7Days();
}