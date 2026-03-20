package com.whu.graduation.taskincentive.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 规则创建/更新请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "规则创建/更新请求")
public class RiskRuleRequest {
    private String name;
    private String type;
    private Integer priority;
    private Integer status;
    private String conditionExpr;
    private String action;
    private String actionParams;
    private Date startTime;
    private Date endTime;
    private String createdBy;
    private String updatedBy;
}
