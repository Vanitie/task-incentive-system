package com.whu.graduation.taskincentive.risk;

import com.whu.graduation.taskincentive.common.enums.RiskDecisionAction;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionEngine;
import com.whu.graduation.taskincentive.service.risk.RuleExpressionEvaluator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 风控规则引擎单测
 */
public class RiskDecisionEngineTest {

    @Test
    void evaluateRules_reject_when_rule_hit() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();
        RiskDecisionEngine engine = new RiskDecisionEngine(evaluator);

        RiskRule rule = RiskRule.builder()
                .id(1L)
                .name("高频刷")
                .type("FREQ")
                .priority(100)
                .status(1)
                .conditionExpr("count_1m > 10")
                .action(RiskDecisionAction.REJECT.name())
                .actionParams(null)
                .build();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("count_1m", 20);

        RiskDecisionResponse resp = engine.evaluateRules(Arrays.asList(rule), ctx);
        assertEquals(RiskDecisionAction.REJECT.name(), resp.getDecision());
    }
}
