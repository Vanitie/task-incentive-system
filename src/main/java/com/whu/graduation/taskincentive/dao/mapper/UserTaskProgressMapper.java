package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.UserTaskProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserTaskProgressMapper extends BaseMapper<UserTaskProgress> {

    /**
     * 按用户ID查询任务进度
     */
    @Select("SELECT * FROM user_task_progress WHERE user_id = #{userId}")
    List<UserTaskProgress> selectByUserId(@Param("userId") Long userId);
}