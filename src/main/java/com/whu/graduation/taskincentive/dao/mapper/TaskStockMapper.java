package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务库存 Mapper
 */
@Mapper
public interface TaskStockMapper extends BaseMapper<TaskStock> {

    // TODO: 可添加乐观锁扣减库存的自定义方法
}