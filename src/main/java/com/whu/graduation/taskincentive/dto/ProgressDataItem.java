package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于展示每周每日完成率的 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressDataItem {
    /** 周名称，例如 "周一" */
    private String week;

    /** 完成率百分比整数（0-100） */
    private Integer percentage;

    /** 动画持续时间（毫秒） */
    private Integer duration;

    /** 颜色值（十六进制字符串），根据完成率映射得到 */
    private String color;
}
