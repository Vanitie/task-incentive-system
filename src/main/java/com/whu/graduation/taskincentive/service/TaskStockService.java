package com.whu.graduation.taskincentive.service;

import com.whu.graduation.taskincentive.dao.entity.TaskStock;

import java.util.List;

/**
 * 任务库存服务接口
 * 提供库存CRUD及乐观锁扣减方法
 */
public interface TaskStockService {

    /**
     * 新增库存记录
     * 使用雪花ID生成唯一任务ID
     * @param taskStock 库存对象
     * @return true表示保存成功，false表示失败
     */
    boolean save(TaskStock taskStock);

    /**
     * 更新库存记录
     * @param taskStock 库存对象
     * @return true表示更新成功，false表示失败
     */
    boolean update(TaskStock taskStock);

    /**
     * 按任务ID删除库存记录
     * @param taskId 任务ID
     * @return true表示删除成功，false表示失败
     */
    boolean deleteById(Long taskId);

    /**
     * 按任务ID获取库存记录列表
     * @param taskId 任务ID
     * @return 对应库存对象列表
     */
    List<TaskStock> getById(Long taskId);

    /**
     * 按任务ID和阶段获取库存记录
     * @param taskId 任务ID
     * @param stageIndex 阶梯任务阶段序号，普通任务为1
     * @return 对应库存对象
     */
    TaskStock getByIdAndStageIndex(Long taskId, Integer stageIndex);

    /**
     * 按任务ID和阶段扣减库存
     * @param taskId 任务ID
     * @param stageIndex 阶梯任务阶段序号，普通任务为1
     * @param count 扣减数量
     * @return true表示扣减成功，false表示库存不足或版本不匹配
     */
    boolean deductStock(Long taskId, Integer stageIndex, Integer count);
}