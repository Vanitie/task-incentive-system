package com.whu.graduation.taskincentive.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dto.Reward;

import java.util.List;
import java.util.Map;

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

    /**
     * 分页查询某用户的奖励记录（支持按 status 过滤，如果 status 为 null 则不过滤）
     */
    Page<UserRewardRecord> selectByUserIdPage(Page<UserRewardRecord> page, Long userId, Integer status);

    /**
     * 统计今日领取过奖励的不重复用户数
     */
    long countUsersReceivedToday();

    /**
     * 获取过去 7 天每天的领取奖励去重用户数，顺序从 6 天前到今天
     */
    List<Long> getReceivedUsersLast7Days();

    /**
     * 分页条件查询用户奖励记录
     * @param page 分页对象
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param rewardType 奖励类型
     * @param status 奖励状态
     * @return 分页结果
     */
    Page<UserRewardRecord> listByConditions(Page<UserRewardRecord> page, Long userId, Long taskId, String rewardType, Integer status);

    UserRewardRecord selectByMessageId(String messageId);

    UserRewardRecord initRecordIfAbsent(String messageId, Long userId, Reward reward);

    boolean markProcessing(String messageId);

    boolean markSuccess(String messageId);

    boolean markFailedNewTx(String messageId, String errorMsg);

    Map<String, Object> reconcileSummary(int sampleLimit);

    /**
     * 统一奖励补偿预览（积分/徽章/实物 + 失败发奖）
     */
    Map<String, Object> previewReplayDiff(int sampleLimit);

    /**
     * 统一奖励补偿执行（积分校准 + 徽章补发 + 失败重试）
     */
    Map<String, Object> executeReplayCompensation();

    /**
     * 按奖励类型执行补偿（POINT/BADGE/ITEM）
     */
    Map<String, Object> executeReplayCompensation(String rewardType);

    Map<String, Object> previewPointReplayDiff(int sampleLimit);

    Map<String, Object> executePointReplayCompensation();
}