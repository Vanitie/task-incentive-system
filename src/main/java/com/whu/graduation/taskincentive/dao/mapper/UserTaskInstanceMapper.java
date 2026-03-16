package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserTaskInstanceMapper extends BaseMapper<UserTaskInstance> {

    /**
     * 按用户ID查询任务进度
     */
    @Select("SELECT * FROM user_task_instance WHERE user_id = #{userId}")
    List<UserTaskInstance> selectByUserId(@Param("userId") Long userId);

    /**
     * 按用户ID和状态查询任务进度
     */
    @Select("SELECT * FROM user_task_instance WHERE user_id = #{userId} AND status = #{status}")
    List<UserTaskInstance> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);

    @Update("""
    UPDATE user_task_instance
    SET progress = #{progress},
        status = #{status},
        extra_data = #{extraData},
        version = version + 1
    WHERE id = #{id}
      AND version = #{version}
""")
    int updateWithVersion(UserTaskInstance instance);

    @Select("""
    SELECT * FROM user_task_instance
    WHERE user_id = #{userId}
      AND task_id = #{taskId}
""")
    UserTaskInstance selectByUserAndTask(
            @Param("userId") Long userId,
            @Param("taskId") Long taskId);

    /**
     * 统计从指定时间点（包含）以来，接取过任务的不重复用户数
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM user_task_instance WHERE create_time >= #{since}")
    long countDistinctUsersSince(@Param("since") Date since);

    /**
     * 统计今天接取过任务的不重复用户数
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM user_task_instance WHERE DATE(create_time) = CURDATE()")
    long countDistinctUsersToday();

    /**
     * 在[start, end)期间去重统计用户数（用于滑动窗口统计）
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM user_task_instance WHERE create_time >= #{start} AND create_time < #{end}")
    long countDistinctUsersBetween(@Param("start") Date start, @Param("end") Date end);

    /**
     * 一次查询指定时间区间内所有记录的日期（DATE(create_time)）和 user_id，服务端将基于此做滑动窗口并集计算
     */
    @Select("SELECT DATE(create_time) as the_date, user_id as user_id FROM user_task_instance WHERE create_time >= #{start} AND create_time < #{end}")
    List<Map<String, Object>> selectUserIdsByDate(@Param("start") Date start, @Param("end") Date end);

    /**
     * 按天统计在[start, end)期间每天的活跃用户数（去重 user_id）
     */
    @Select("SELECT DATE(create_time) as the_date, COUNT(DISTINCT user_id) as cnt FROM user_task_instance WHERE create_time >= #{start} AND create_time < #{end} GROUP BY DATE(create_time) ORDER BY DATE(create_time) ASC")
    List<Map<String, Object>> countActiveUsersGroupByDate(@Param("start") Date start, @Param("end") Date end);
}

