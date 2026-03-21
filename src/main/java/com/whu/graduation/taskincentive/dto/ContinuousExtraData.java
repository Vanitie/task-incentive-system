package com.whu.graduation.taskincentive.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 连续任务实例扩展数据
 */
@Data
public class ContinuousExtraData {
    /**
     * 上次签到日期
     */
    private LocalDate lastSignDate;
    /**
     * 连续签到天数
     */
    private Integer continuousDays;
}
