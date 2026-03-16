package com.whu.graduation.taskincentive.dto;

import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.common.enums.StockType;
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
public class Reward {

    /** 奖励 id（系统生成） */
    private Long rewardId;

    /** 奖励来源的任务 id */
    private Long taskId;

    /** 奖励类型，例如积分/徽章/实物 */
    private RewardType rewardType;

    /** 库存类型，表示是否限量等 */
    private StockType stockType;

    /** 奖励数量或积分值 */
    private Integer amount;

    /** 徽章 code 或实物 code（若适用） */
    private Integer code;
}
