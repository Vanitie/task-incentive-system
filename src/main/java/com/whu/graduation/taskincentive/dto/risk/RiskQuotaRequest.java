package com.whu.graduation.taskincentive.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配额更新请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "配额更新请求")
public class RiskQuotaRequest {
    private Long id;
    private String scopeType;
    private String scopeId;
    private String periodType;
    private Integer limitValue;
}
