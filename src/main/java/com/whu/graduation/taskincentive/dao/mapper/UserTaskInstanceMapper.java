package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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
}