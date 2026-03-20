package com.whu.graduation.taskincentive.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 风控看板总览数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "风控看板总览数据")
public class RiskDashboardOverviewResponse {
    @Schema(description = "统计开始时间")
    private Date start;

    @Schema(description = "统计结束时间")
    private Date end;

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

    @Schema(description = "拦截率（百分比）")
    private double interceptRate;

    @Schema(description = "放行率（百分比）")
    private double passRate;

    @Schema(description = "平均耗时（毫秒）")
    private double avgLatencyMs;

    @Schema(description = "P95耗时（毫秒）")
    private double p95LatencyMs;

    @Schema(description = "P99耗时（毫秒）")
    private double p99LatencyMs;
}
