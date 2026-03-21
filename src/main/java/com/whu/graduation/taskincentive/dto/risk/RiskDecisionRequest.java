package com.whu.graduation.taskincentive.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 风控实时决策请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "风控实时决策请求")
public class RiskDecisionRequest {

    @Schema(description = "请求ID，用于幂等")
    private String requestId;

    @Schema(description = "事件ID")
    private String eventId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "事件类型")
    private String eventType;

    @Schema(description = "事件时间")
    private LocalDateTime eventTime;

    @Schema(description = "金额或数量")
    private Integer amount;

    @Schema(description = "资源类型（POINT/BADGE/PHYSICAL/ALL）")
    private String resourceType;

    @Schema(description = "资源ID（具体奖品/徽章/任务奖励ID）")
    private String resourceId;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "IP")
    private String ip;

    @Schema(description = "渠道")
    private String channel;

    @Schema(description = "扩展字段")
    private Map<String, Object> ext;
}
