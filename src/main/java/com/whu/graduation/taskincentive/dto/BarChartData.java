package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 两周任务条形图数据项
 * 包含一周内每天的任务接取数和任务完成数，用于前端绘制对比柱状图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarChartData {
    /**
     * 每日任务接取数（长度为7），顺序为从周一到周日
     */
    private List<Long> taskReceived;

    /**
     * 每日任务完成数（长度为7），顺序为从周一到周日
     */
    private List<Long> taskCompleted;
}
