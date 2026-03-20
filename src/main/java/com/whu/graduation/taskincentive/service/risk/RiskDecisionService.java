package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.dto.risk.RiskDecisionRequest;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;

/**
 * 风控决策服务
 */
public interface RiskDecisionService {

    /**
     * 实时风控决策
     */
    RiskDecisionResponse evaluate(RiskDecisionRequest request);
}
