package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.TaskConfigHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskConfigHistoryMapper extends BaseMapper<TaskConfigHistory> {

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM task_config_history WHERE task_id = #{taskId}")
    Integer selectMaxVersionNo(@Param("taskId") Long taskId);

    @Select("SELECT * FROM task_config_history WHERE task_id = #{taskId} AND version_no = #{versionNo} LIMIT 1")
    TaskConfigHistory selectByTaskIdAndVersion(@Param("taskId") Long taskId, @Param("versionNo") Integer versionNo);

    @Select("SELECT * FROM task_config_history WHERE task_id = #{taskId} ORDER BY version_no DESC")
    List<TaskConfigHistory> selectByTaskIdOrderByVersionDesc(@Param("taskId") Long taskId);
}

