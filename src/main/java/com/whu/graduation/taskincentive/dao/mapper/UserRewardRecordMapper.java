package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户奖励记录 Mapper
 */
@Mapper
public interface UserRewardRecordMapper extends BaseMapper<UserRewardRecord> {

    // TODO: 可添加查询某用户奖励记录、按状态过滤未领取奖励等方法
}