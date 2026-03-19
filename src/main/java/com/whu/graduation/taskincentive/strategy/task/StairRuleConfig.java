package com.whu.graduation.taskincentive.strategy.task;

import lombok.Data;

import java.util.List;

/**
 * 阶梯任务规则配置
 */
@Data
public class StairRuleConfig {
    private List<Integer> stages;
    private List<Integer> rewards;
}
