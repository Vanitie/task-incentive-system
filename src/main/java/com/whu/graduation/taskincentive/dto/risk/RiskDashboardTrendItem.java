package com.whu.graduation.taskincentive.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 风控看板按天趋势项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "风控看板按天趋势项")
public class RiskDashboardTrendItem {
    @Schema(description = "日期（yyyy-MM-dd）")
    private String date;

    @Schema(description = "总决策数")
    private long total;

    @Schema(description = "放行数")
    private long pass;

    @Schema(description = "拒绝数")
    private long reject;

    @Schema(description = "降级放行数")
    private long degradePass;

    @Schema(description = "复核数")
    private long review;

    @Schema(description = "冻结数")
    private long freeze;

    @Schema(description = "五种决策状态计数（PASS/REJECT/DEGRADE_PASS/REVIEW/FREEZE）")
    private Map<String, Long> statusCounts;
}
