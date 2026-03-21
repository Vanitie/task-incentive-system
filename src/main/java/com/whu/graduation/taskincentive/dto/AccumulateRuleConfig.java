package com.whu.graduation.taskincentive.dto;

import lombok.Data;

/**
 * 累积任务规则配置
 */
@Data
public class AccumulateRuleConfig {
    /**
     * 目标值（累计达到该值即完成）
     */
    private Integer targetValue;
}
