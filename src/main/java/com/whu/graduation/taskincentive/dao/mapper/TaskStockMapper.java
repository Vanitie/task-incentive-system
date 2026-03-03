package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskStockMapper extends BaseMapper<TaskStock> {

    /**
     * 乐观锁扣减库存
     * 返回受影响行数，0表示扣减失败
     */
    @Update("UPDATE task_stock " +
            "SET available_stock = available_stock - #{count}, version = version + 1 " +
            "WHERE task_id = #{taskId} AND version = #{version} AND available_stock >= #{count}")
    int deductStock(@Param("taskId") Long taskId,
                    @Param("count") Integer count,
                    @Param("version") Integer version);
}