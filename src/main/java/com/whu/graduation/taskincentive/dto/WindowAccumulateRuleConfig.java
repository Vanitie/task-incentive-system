package com.whu.graduation.taskincentive.dto;

import lombok.Data;

/**
 * 时间窗累积任务规则配置
 */
@Data
public class WindowAccumulateRuleConfig {
    /**
     * 目标值（窗口内累计达到该值即完成）
     */
    private Integer targetValue;
    /**
     * 时间窗大小（分钟）
     */
    private Integer windowMinutes;
}
