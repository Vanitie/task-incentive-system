package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 图表数据 DTO，用于前端展示一项统计（最近 7 天）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartData {
    private String icon;
    private String bgColor;
    private String color;
    private Integer duration;
    private String name;
    private Long value;
    private String percent; // 格式如 +88% 或 -10%
    private List<Long> data; // 最近 7 天的数据，顺序为从 6 天前 到 今天
}
