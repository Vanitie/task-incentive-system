package com.whu.graduation.taskincentive.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;

import java.util.List;

/**
 * 任务配置服务接口
 * 提供任务配置的增删改查及自定义查询功能
 */
public interface TaskConfigService {

    /**
     * 新增任务配置
     * 使用雪花ID生成唯一任务ID
     * @param taskConfig 任务配置对象
     * @return true表示保存成功，false表示失败
     */
    boolean save(TaskConfig taskConfig);

    /**
     * 更新任务配置
     * @param taskConfig 待更新任务配置对象
     * @return true表示更新成功，false表示失败
     */
    boolean update(TaskConfig taskConfig);

    /**
     * 按ID删除任务配置
     * @param id 任务ID
     * @return true表示删除成功，false表示失败
     */
    boolean deleteById(Long id);

    /**
     * 按ID获取任务配置
     * @param id 任务ID
     * @return 对应任务配置对象，如果不存在返回null
     */
    TaskConfig getById(Long id);

    /**
     * 查询所有任务配置
     * @return 任务配置列表
     */
    List<TaskConfig> listAll();

    /**
     * 按任务名称查询任务配置，按结束时间升序排序
     * @param taskName 任务名称
     * @return 符合条件的任务列表
     */
    List<TaskConfig> selectByTaskName(String taskName);

    /**
     * 按任务类型分页查询，按结束时间升序排序
     * @param page 分页对象
     * @param taskType 任务类型（TASK_TYPE_BEHAVIOR / TASK_TYPE_STAIR / TASK_TYPE_LIMITED）
     * @return 分页后的任务列表
     */
    Page<TaskConfig> selectByTaskTypePage(Page<TaskConfig> page, String taskType);

    /**
     * 按任务状态分页查询，按结束时间升序排序
     * @param page 分页对象
     * @param status 任务状态（0停用 / 1启用）
     * @return 分页后的任务列表
     */
    Page<TaskConfig> selectByStatusPage(Page<TaskConfig> page, Integer status);
}