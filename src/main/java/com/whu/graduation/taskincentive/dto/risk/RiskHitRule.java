package com.whu.graduation.taskincentive.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风控命中规则明细
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "风控命中规则明细")
public class RiskHitRule {

    @Schema(description = "规则ID")
    private Long ruleId;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "规则类型")
    private String ruleType;

    @Schema(description = "命中动作")
    private String action;

    @Schema(description = "动作参数")
    private String actionParams;

    @Schema(description = "原因码")
    private String reasonCode;
}
