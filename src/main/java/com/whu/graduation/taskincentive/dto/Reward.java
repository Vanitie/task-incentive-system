package com.whu.graduation.taskincentive.dto;

import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.common.enums.StockType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 奖励类
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Reward {

    /**
     * 奖励id
     */
    private Long rewardId;

    /**
     * 奖励来源的任务id
     */
    private Long taskId;

    /**
     * 奖励类型
     */
    private RewardType rewardType;

    /**
     * 库存类型
     */
    private StockType stockType;

    /**
     * 奖励数量
     */
    private Integer amount;

    /**
     * 徽章code / 实物code
     */
    private Integer code;
}
