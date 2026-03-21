package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 时间窗累积任务明细
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WindowAccumulateEntry {
    /**
     * 事件时间
     */
    private LocalDateTime time;
    /**
     * 事件值
     */
    private Integer value;
}
