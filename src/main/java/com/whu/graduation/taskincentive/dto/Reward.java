package com.whu.graduation.taskincentive.dto;

import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.common.enums.StockType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 奖励描述 DTO，用于描述某次奖励的来源与类型信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "奖励描述 DTO，包含奖励来源与类型信息")
public class Reward {

    @Schema(description = "奖励 id（系统生成）")
    private Long rewardId;

    @Schema(description = "奖励来源的任务 id")
    private Long taskId;

    @Schema(description = "阶梯任务的阶段序号，普通任务为1")
    private Integer stageIndex;

    @Schema(description = "奖励类型，例如积分/徽章/实物")
    private RewardType rewardType;

    @Schema(description = "库存类型，表示是否限量等")
    private StockType stockType;

    @Schema(description = "奖励数量或积分值")
    private Integer amount;

    @Schema(description = "徽章 code 或实物 code（若适用）")
    private Integer code;
}
