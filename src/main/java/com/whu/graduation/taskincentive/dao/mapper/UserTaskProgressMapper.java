package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.UserTaskProgress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户任务进度 Mapper
 */
@Mapper
public interface UserTaskProgressMapper extends BaseMapper<UserTaskProgress> {

    // TODO: 可添加按用户ID查询任务进度、按状态统计等自定义方法
}