package com.whu.graduation.taskincentive.service;

import com.whu.graduation.taskincentive.dao.entity.Badge;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;

import java.util.List;

/**
 * 用户奖励记录服务接口
 * 提供用户奖励的增删改查及自定义查询方法
 */
public interface UserRewardRecordService {

    /**
     * 新增用户奖励记录
     * 使用雪花ID生成唯一ID
     * @param record 用户奖励记录对象
     * @return true表示保存成功
     */
    boolean save(UserRewardRecord record);

    boolean update(UserRewardRecord record);
    boolean deleteById(Long id);
    UserRewardRecord getById(Long id);
    List<UserRewardRecord> listAll();

    /**
     * 查询某用户所有奖励记录
     * @param userId 用户ID
     * @return 奖励记录列表
     */
    List<UserRewardRecord> selectByUserId(Long userId);

    /**
     * 查询用户未领取的实物奖励
     * @param userId 用户ID
     * @return 未领取奖励列表
     */
    List<UserRewardRecord> selectUnclaimedPhysicalReward(Long userId);

    /**
     * 按状态查询用户奖励记录
     * @param userId 用户ID
     * @param status 奖励状态 0未领取 / 1已领取
     * @return 奖励记录列表
     */
    List<UserRewardRecord> selectByStatus(Long userId, Integer status);
}