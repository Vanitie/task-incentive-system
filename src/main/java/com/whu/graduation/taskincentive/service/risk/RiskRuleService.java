package com.whu.graduation.taskincentive.service.risk;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dto.RiskRuleValidateResult;
import com.whu.graduation.taskincentive.dto.risk.RiskRuleRequest;

/**
 * 风控规则服务
 */
public interface RiskRuleService {
    Page<RiskRule> page(Page<RiskRule> page);
    RiskRule create(RiskRuleRequest request);
    RiskRule update(Long id, RiskRuleRequest request);
    boolean publish(Long id);
    boolean rollback(Long id);
    RiskRuleValidateResult validateConditionExpr(String expression);
}
