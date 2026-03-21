package com.whu.graduation.taskincentive.mq;

import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dao.entity.RewardFreezeRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风控决策落库消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDecisionPersistMessage {
    /**
     * 决策日志
     */
    private RiskDecisionLog decisionLog;
    /**
     * 冻结记录（可为空）
     */
    private RewardFreezeRecord freezeRecord;
}
