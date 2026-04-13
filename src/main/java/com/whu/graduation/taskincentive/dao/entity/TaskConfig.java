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
 * 任务模板实体
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
     * 任务类型：累加 ACCUMULATE / 签到 CONTINUOUS / 阶梯 STAIR
     */
    private String taskType;

    /**
     * 库存类型：无限 UNLIMITED / 限量 LIMITED
     */
    private String stockType;

    /**
     * 触发事件类型，例如 用户学习 USER_LEARN, 用户签到 USER_SIGN, 用户领奖 USER_REWARD_CLAIM
     */
    private String triggerEvent;

    /**
     * 任务策略配置 JSON，例如目标值、连续天数、阶梯规则等
     */
    private String ruleConfig;

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
     * 任务状态：停用 STATUS_DISABLED 0 / 启用 STATUS_ENABLED 1
     */
    private Integer status;

    /**
     * 任务开始时间
     */
    private Date startTime;

    /**
     * 任务结束时间
     */
    private Date endTime;

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