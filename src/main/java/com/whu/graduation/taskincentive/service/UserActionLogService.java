package com.whu.graduation.taskincentive.service;

import com.whu.graduation.taskincentive.dao.entity.UserActionLog;

import java.util.List;

/**
 * 用户行为日志服务接口
 * 提供用户行为日志的增删改查及自定义查询统计方法
 */
public interface UserActionLogService {

    /**
     * 新增用户行为日志
     * 使用雪花ID生成唯一ID
     * @param log 用户行为日志对象
     * @return true表示保存成功
     */
    boolean save(UserActionLog log);

    /**
     * 更新用户行为日志
     * @param log 待更新对象
     * @return true表示更新成功
     */
    boolean update(UserActionLog log);

    /**
     * 按ID删除行为日志
     * @param id 行为日志ID
     * @return true表示删除成功
     */
    boolean deleteById(Long id);

    /**
     * 按ID获取行为日志
     * @param id 行为日志ID
     * @return 用户行为日志对象
     */
    UserActionLog getById(Long id);

    /**
     * 查询所有用户行为日志
     * @return 行为日志列表
     */
    List<UserActionLog> listAll();

    /**
     * 按用户ID查询行为日志
     * @param userId 用户ID
     * @return 用户行为日志列表
     */
    List<UserActionLog> selectByUserId(Long userId);

    /**
     * 按行为类型查询日志
     * @param actionType 行为类型，例如 USER_LEARN, USER_SIGN
     * @return 用户行为日志列表
     */
    List<UserActionLog> selectByActionType(String actionType);

    /**
     * 统计某用户的行为总量
     * @param userId 用户ID
     * @param actionType 行为类型，可为null表示统计所有行为
     * @return 行为总数
     */
    Integer countUserAction(Long userId, String actionType);
}