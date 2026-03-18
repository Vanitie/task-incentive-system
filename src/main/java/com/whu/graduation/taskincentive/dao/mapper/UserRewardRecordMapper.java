package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserRewardRecordMapper extends BaseMapper<UserRewardRecord> {

    /**
     * 查询某用户所有奖励记录
     */
    @Select("SELECT * FROM user_reward_record WHERE user_id = #{userId}")
    List<UserRewardRecord> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户未领取实物奖励
     */
    @Select("SELECT * FROM user_reward_record WHERE user_id = #{userId} AND reward_type = 'REWARD_PHYSICAL' AND status = 0")
    List<UserRewardRecord> selectUnclaimedPhysicalRewards(@Param("userId") Long userId);

    /**
     * 统计今天领取过奖励的不重复用户数（基于 create_time）
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM user_reward_record WHERE DATE(create_time) = CURDATE()")
    long countDistinctUsersReceivedToday();

    /**
     * 按天统计在[start, end)期间每天领取奖励的去重用户数
     */
    @Select("SELECT DATE(create_time) as the_date, COUNT(DISTINCT user_id) as cnt FROM user_reward_record WHERE create_time >= #{start} AND create_time < #{end} GROUP BY DATE(create_time) ORDER BY DATE(create_time) ASC")
    List<Map<String, Object>> countReceivedGroupByDate(@Param("start") Date start, @Param("end") Date end);

    /**
     * 一次查询指定时间区间内所有奖励记录的日期（DATE(create_time)）和 user_id，服务端将基于此做滑动窗口并集计算
     */
    @Select("SELECT DATE(create_time) as the_date, user_id as user_id FROM user_reward_record WHERE create_time >= #{start} AND create_time < #{end}")
    List<Map<String, Object>> selectUserIdsByDate(@Param("start") Date start, @Param("end") Date end);

    /**
     * 按日期分组统计过去7天每天领取奖励的用户数（去重 user_id）
     */
    @Select("SELECT DATE(create_time) as the_date, COUNT(DISTINCT user_id) as cnt FROM user_reward_record WHERE create_time >= #{start} AND create_time < #{end} GROUP BY DATE(create_time) ORDER BY DATE(create_time) ASC")
    List<Map<String, Object>> countDistinctUserIdsGroupByDate(@Param("start") Date start, @Param("end") Date end);
}