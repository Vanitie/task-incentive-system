package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRewardRecordMapper extends BaseMapper<UserRewardRecord> {

    /**
     * 查询某用户所有奖励记录
     */
    @Select("SELECT * FROM user_reward_record WHERE user_id = #{userId}")
    List<UserRewardRecord> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户所有徽章
     */
    @Select("SELECT * FROM user_reward_record WHERE user_id = #{userId} AND reward_type = 'REWARD_BADGE'")
    List<UserRewardRecord> selectUserBadges(@Param("userId") Long userId);

    /**
     * 查询用户未领取实物奖励
     */
    @Select("SELECT * FROM user_reward_record WHERE user_id = #{userId} AND reward_type = 'REWARD_PHYSICAL' AND status = 0")
    List<UserRewardRecord> selectUnclaimedPhysicalRewards(@Param("userId") Long userId);
}