package com.whu.graduation.taskincentive.risk;

import com.whu.graduation.taskincentive.common.enums.RiskDecisionAction;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionEngine;
import com.whu.graduation.taskincentive.service.risk.RuleExpressionEvaluator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                .conditionExpr("#count_1m > 10")
                .action(RiskDecisionAction.REJECT.name())
                .actionParams(null)
                .build();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("count_1m", 20);

        RiskDecisionResponse resp = engine.evaluateRules(Arrays.asList(rule), ctx);
        assertEquals(RiskDecisionAction.REJECT.name(), resp.getDecision());
        assertNotNull(resp.getHitRules());
        assertEquals(1, resp.getHitRules().size());
    }

    @Test
    void evaluateRules_review_when_multiple_pass_like_rules_hit() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();
        RiskDecisionEngine engine = new RiskDecisionEngine(evaluator);

        RiskRule pass = RiskRule.builder()
                .id(11L)
                .name("低风险放行")
                .type("BASE")
                .priority(10)
                .status(1)
                .conditionExpr("#count_1m >= 0")
                .action(RiskDecisionAction.PASS.name())
                .build();

        RiskRule degrade = RiskRule.builder()
                .id(12L)
                .name("降级放行")
                .type("BASE")
                .priority(9)
                .status(1)
                .conditionExpr("#count_1m >= 0")
                .action(RiskDecisionAction.DEGRADE_PASS.name())
                .actionParams("{\"ratio\":0.5}")
                .build();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("count_1m", 1);

        RiskDecisionResponse resp = engine.evaluateRules(Arrays.asList(pass, degrade), ctx);
        assertEquals(RiskDecisionAction.REVIEW.name(), resp.getDecision());
        assertEquals("RULE_CONFLICT", resp.getReasonCode());
    }

    @Test
    void evaluateRules_shouldReturnPass_whenRuleOutOfTimeWindow() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();
        RiskDecisionEngine engine = new RiskDecisionEngine(evaluator);

        RiskRule futureRule = RiskRule.builder()
                .id(21L)
                .name("未来生效规则")
                .type("TIME")
                .priority(50)
                .status(1)
                .conditionExpr("#count_1m > 1")
                .action(RiskDecisionAction.REJECT.name())
                .startTime(new Date(System.currentTimeMillis() + 3600_000L))
                .build();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("count_1m", 99);

        RiskDecisionResponse resp = engine.evaluateRules(Arrays.asList(futureRule), ctx);
        assertEquals(RiskDecisionAction.PASS.name(), resp.getDecision());
        assertTrue(resp.getHitRules().isEmpty());
    }

    @Test
    void evaluateRules_shouldParseDegradeRatioFromKeyValueParams() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();
        RiskDecisionEngine engine = new RiskDecisionEngine(evaluator);

        RiskRule degrade = RiskRule.builder()
                .id(31L)
                .name("降级放行")
                .type("BASE")
                .priority(10)
                .status(1)
                .conditionExpr("#count_1m >= 1")
                .action(RiskDecisionAction.DEGRADE_PASS.name())
                .actionParams("ratio=0.3")
                .build();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("count_1m", 3);

        RiskDecisionResponse resp = engine.evaluateRules(Arrays.asList(degrade), ctx);
        assertEquals(RiskDecisionAction.DEGRADE_PASS.name(), resp.getDecision());
        assertEquals(0.3, resp.getDegradeRatio());
    }

    @Test
    void evaluateRules_shouldUseDefaultRatio_whenParamsMalformed() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();
        RiskDecisionEngine engine = new RiskDecisionEngine(evaluator);

        RiskRule degrade = RiskRule.builder()
                .id(32L)
                .name("降级放行-坏参数")
                .type("BASE")
                .priority(10)
                .status(1)
                .conditionExpr("#count_1m >= 1")
                .action(RiskDecisionAction.DEGRADE_PASS.name())
                .actionParams("{bad-json}")
                .build();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("count_1m", 2);

        RiskDecisionResponse resp = engine.evaluateRules(Arrays.asList(degrade), ctx);
        assertEquals(RiskDecisionAction.DEGRADE_PASS.name(), resp.getDecision());
        assertEquals(0.5, resp.getDegradeRatio());
    }
}
