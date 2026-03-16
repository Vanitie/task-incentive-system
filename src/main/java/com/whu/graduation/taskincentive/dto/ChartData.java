package com.whu.graduation.taskincentive.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用图表数据 DTO，用于前端展示最近 7 天的统计项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通用图表数据 DTO，用于前端展示最近 7 天的统计项")
public class ChartData {
    @Schema(description = "图标名称或标识（可由前端映射到实际图标组件）")
    private String icon;

    @Schema(description = "背景颜色（十六进制字符串，例如 #effaff）")
    private String bgColor;

    @Schema(description = "主颜色（用于折线或文字颜色）")
    private String color;

    @Schema(description = "动画持续时间（毫秒，供前端使用）")
    private Integer duration;

    @Schema(description = "指标名称，例如 用户总数")
    private String name;

    @Schema(description = "指标展示值（通常为最后一天或累计值）")
    private Long value;

    @Schema(description = "环比百分比字符串，例如 +88% 或 -10%")
    private String percent;

    @Schema(description = "最近 7 天的数据数组（长度为 7），顺序为从 6 天前 到 今天")
    private List<Long> data;
}
