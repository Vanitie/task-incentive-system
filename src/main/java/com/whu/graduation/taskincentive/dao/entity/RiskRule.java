package com.whu.graduation.taskincentive.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 风控规则
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("risk_rule")
public class RiskRule {
    /**
     * 主键ID
     */
    private Long id;
    /**
     * 规则名称
     */
    private String name;
    /**
     * 规则类型
     */
    private String type;
    /**
     * 优先级
     */
    private Integer priority;
    /**
     * 状态（0停用，1启用）
     */
    private Integer status;
    /**
     * 条件表达式
     */
    private String conditionExpr;
    /**
     * 决策动作
     */
    private String action;
    /**
     * 决策动作参数
     */
    private String actionParams;
    /**
     * 版本号
     */
    private Integer version;
    /**
     * 创建人
     */
    private String createdBy;
    /**
     * 更新人
     */
    private String updatedBy;
    /**
     * 生效开始时间
     */
    private Date startTime;
    /**
     * 生效结束时间
     */
    private Date endTime;
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
