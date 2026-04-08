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

    /**
     * 对照链路风控：全量按数据库读取并同步落库，不依赖缓存和 Kafka。
     */
    RiskDecisionResponse evaluateDirect(RiskDecisionRequest request);
}
