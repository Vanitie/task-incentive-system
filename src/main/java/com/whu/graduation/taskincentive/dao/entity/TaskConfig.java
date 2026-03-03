package com.whu.graduation.taskincentive.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 任务配置实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("task_config")
public class TaskConfig {

    /**
     * 任务ID
     */
    private Long id;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务类型：行为 TASK_TYPE_BEHAVIOR / 阶梯 TASK_TYPE_STAIR / 限量 TASK_TYPE_LIMITED
     */
    private String taskType;

    /**
     * 触发事件类型，例如 用户学习 USER_LEARN, 用户签到 USER_SIGN
     */
    private String triggerEvent;

    /**
     * 完成目标次数或时长
     */
    private Integer targetValue;

    /**
     * 奖励类型：积分 REWARD_POINT / 徽章 REWARD_BADGE / 实物 REWARD_PHYSICAL
     */
    private String rewardType;

    /**
     * 奖励数值或数量
     */
    private Integer rewardValue;

    /**
     * 限量任务总库存，仅限量任务使用
     */
    private Integer totalStock;

    /**
     * 任务开始时间
     */
    private Date startTime;

    /**
     * 任务结束时间
     */
    private Date endTime;

    /**
     * 任务状态：停用 STATUS_DISABLED / 启用 STATUS_ENABLED
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
