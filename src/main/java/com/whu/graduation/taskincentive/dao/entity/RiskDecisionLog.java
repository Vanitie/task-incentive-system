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
 * 风控决策日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("risk_decision_log")
public class RiskDecisionLog {
    /**
     * 主键ID
     */
    private Long id;
    /**
     * 请求ID
     */
    private String requestId;
    /**
     * 事件ID
     */
    private String eventId;
    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 任务ID
     */
    private Long taskId;
    /**
     * 决策结果
     */
    private String decision;
    /**
     * 原因码
     */
    private String reasonCode;
    /**
     * 命中规则（JSON字符串）
     */
    private String hitRules;
    /**
     * 风险分数
     */
    private Integer riskScore;
    /**
     * 决策耗时（毫秒）
     */
    private Long latencyMs;
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
