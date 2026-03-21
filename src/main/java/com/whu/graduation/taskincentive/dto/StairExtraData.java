package com.whu.graduation.taskincentive.dto;

import lombok.Data;

import java.util.Set;

/**
 * 阶梯任务实例扩展数据
 */
@Data
public class StairExtraData {
    /**
     * 已发放的阶梯序号
     */
    private Set<Integer> grantedStages;
}
