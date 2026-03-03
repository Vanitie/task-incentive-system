package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务配置 Mapper
 */
@Mapper
public interface TaskConfigMapper extends BaseMapper<TaskConfig> {

    // TODO: 可添加自定义查询方法，例如按状态或类型查询任务列表
}