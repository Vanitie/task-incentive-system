package com.whu.graduation.taskincentive.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * 分页查询任务配置
     */
    Page<TaskConfig> selectPage(Page<TaskConfig> page);

    /**
     * 多级缓存获取 TaskConfig（本地->Redis->DB）
     */
    TaskConfig getTaskConfig(Long taskId);

    /**
     * 根据事件类型获取关联的 taskId 列表（由 service 维护 Redis set 或 DB 关联表）
     */
    Set<String> getTaskIdsByEventType(String eventType);

    /**
     * 仅走数据库获取事件关联任务ID（压测对照：无缓存）
     */
    Set<String> getTaskIdsByEventTypeDirect(String eventType);

    /**
     * 使本地缓存失效（由事件触发）
     */
    void invalidateTaskConfig(Long taskId);

    /**
     * 主动从 Redis/DB 拉取最新并刷新本地缓存
     */
    void refreshTaskConfig(Long taskId);

    /**
     * 批量获取 taskConfig，返回 id->TaskConfig 映射（用于批量处理优化）
     */
    Map<Long, TaskConfig> getTaskConfigsByIds(Set<Long> ids);

    /**
     * 仅走数据库批量获取 taskConfig（压测对照：无缓存）
     */
    Map<Long, TaskConfig> getTaskConfigsByIdsDirect(Set<Long> ids);

    /**
     * 多条件分页查询任务配置
     */
    Page<TaskConfig> searchByConditions(String taskName, String taskType, Integer status, String rewardType, Page<TaskConfig> page);
}