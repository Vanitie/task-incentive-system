package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskConfigMapper extends BaseMapper<TaskConfig> {

    /**
     * 按任务名称查询，按结束时间升序
     */
    @Select("SELECT * FROM task_config WHERE task_name = #{taskName} ORDER BY end_time ASC")
    List<TaskConfig> selectByTaskName(@Param("taskName") String taskName);

    /**
     * 按任务类型分页查询，按结束时间升序
     */
    @Select("SELECT * FROM task_config WHERE task_type = #{taskType} ORDER BY end_time ASC")
    List<TaskConfig> selectByTaskTypePage(@Param("taskType") String taskType,
                                          @Param("page") Page<TaskConfig> page);

    /**
     * 按状态分页查询，按结束时间升序
     */
    @Select("SELECT * FROM task_config WHERE status = #{status} ORDER BY end_time ASC")
    List<TaskConfig> selectByStatusPage(@Param("status") Integer status,
                                        @Param("page") Page<TaskConfig> page);
}