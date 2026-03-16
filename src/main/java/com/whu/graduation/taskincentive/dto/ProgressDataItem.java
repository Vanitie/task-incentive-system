package com.whu.graduation.taskincentive.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "每周每日完成率项（周、百分比、动画时长、颜色）")
public class ProgressDataItem {
    @Schema(description = "周名称，例如 周一")
    private String week;

    @Schema(description = "完成率百分比整数（0-100）")
    private Integer percentage;

    @Schema(description = "动画持续时间（毫秒）")
    private Integer duration;

    @Schema(description = "颜色值（十六进制字符串），根据完成率映射得到")
    private String color;
}
