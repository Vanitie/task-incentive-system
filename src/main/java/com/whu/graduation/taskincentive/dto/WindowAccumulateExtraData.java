package com.whu.graduation.taskincentive.dto;

import lombok.Data;

import java.util.List;

/**
 * 时间窗累积任务扩展数据
 */
@Data
public class WindowAccumulateExtraData {
    /**
     * 窗口内事件明细
     */
    private List<WindowAccumulateEntry> entries;
}
