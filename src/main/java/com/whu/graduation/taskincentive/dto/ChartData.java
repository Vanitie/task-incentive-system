package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用图表数据 DTO，用于前端展示最近 7 天的统计项
 * 包含展示所需的样式（icon/bgColor/color/duration）以及统计数值（name/value/percent/data）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartData {
    /** 图标名称或标识（可由前端映射到实际图标组件） */
    private String icon;

    /** 背景颜色（十六进制字符串，例如 #effaff） */
    private String bgColor;

    /** 主颜色（用于折线或文字颜色） */
    private String color;

    /** 动画持续时间（毫秒，供前端使用） */
    private Integer duration;

    /** 指标名称，例如 "用户总数" */
    private String name;

    /** 指标展示值（通常为最后一天或累计值） */
    private Long value;

    /**
     * 环比百分比字符串，例如 "+88%" 或 "-10%"，由后端计算好并包含符号
     */
    private String percent;

    /**
     * 最近 7 天的数据数组（长度为 7），顺序为从 6 天前 到 今天
     */
    private List<Long> data;
}
