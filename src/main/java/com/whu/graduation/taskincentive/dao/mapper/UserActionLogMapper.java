package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserActionLogMapper extends BaseMapper<UserActionLog> {

    /**
     * 按用户ID查询日志
     */
    @Select("SELECT * FROM user_action_log WHERE user_id = #{userId} ORDER BY create_time ASC")
    List<UserActionLog> selectByUserId(@Param("userId") Long userId);

    /**
     * 按行为类型查询日志
     */
    @Select("SELECT * FROM user_action_log WHERE action_type = #{actionType} ORDER BY create_time ASC")
    List<UserActionLog> selectByActionType(@Param("actionType") String actionType);

    /**
     * 按用户ID统计各行为类型总值
     */
    @Select("SELECT action_type, SUM(action_value) AS total_value " +
            "FROM user_action_log WHERE user_id = #{userId} GROUP BY action_type")
    List<Map<String, Object>> selectUserActionSummary(@Param("userId") Long userId);
}