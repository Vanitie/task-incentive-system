package com.whu.graduation.taskincentive.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 风控实时决策响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "风控实时决策响应")
public class RiskDecisionResponse {

    @Schema(description = "决策结果")
    private String decision;

    @Schema(description = "原因码")
    private String reasonCode;

    @Schema(description = "命中规则列表")
    private List<RiskHitRule> hitRules;

    @Schema(description = "风险分")
    private Integer riskScore;

    @Schema(description = "链路追踪ID")
    private String traceId;

    @Schema(description = "降级比例（0-1），仅 DEGRADE_PASS 有值")
    private Double degradeRatio;
}
