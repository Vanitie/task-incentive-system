package com.whu.graduation.taskincentive.risk;

import com.whu.graduation.taskincentive.common.enums.RiskDecisionAction;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionEngine;
import com.whu.graduation.taskincentive.service.risk.RuleExpressionEvaluator;
import com.whu.graduation.taskincentive.dto.risk.RiskHitRule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals("高频刷", resp.getReasonCode());
        assertNotNull(resp.getHitRules());
        assertEquals(1, resp.getHitRules().size());
        assertEquals("高频刷", resp.getHitRules().get(0).getReasonCode());
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

    @Test
    void evaluateRules_shouldIgnoreNullAndDisabledRules() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();
        RiskDecisionEngine engine = new RiskDecisionEngine(evaluator);

        RiskRule disabled = RiskRule.builder()
                .id(41L)
                .status(0)
                .conditionExpr("#count_1m > 1")
                .action(RiskDecisionAction.REJECT.name())
                .build();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("count_1m", 99);

        RiskDecisionResponse resp = engine.evaluateRules(Arrays.asList(null, disabled), ctx);
        assertEquals(RiskDecisionAction.PASS.name(), resp.getDecision());
        assertTrue(resp.getHitRules().isEmpty());
        assertEquals(0, resp.getRiskScore());
    }

    @Test
    void evaluateRules_shouldPreferReviewOverFreeze() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();
        RiskDecisionEngine engine = new RiskDecisionEngine(evaluator);

        RiskRule freezeRule = RiskRule.builder()
                .id(51L)
                .priority(50)
                .status(1)
                .conditionExpr("#count_1m >= 1")
                .action(RiskDecisionAction.FREEZE.name())
                .build();

        RiskRule reviewRule = RiskRule.builder()
                .id(52L)
                .priority(10)
                .status(1)
                .conditionExpr("#count_1m >= 1")
                .action(RiskDecisionAction.REVIEW.name())
                .build();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("count_1m", 2);

        RiskDecisionResponse resp = engine.evaluateRules(Arrays.asList(freezeRule, reviewRule), ctx);
        assertEquals(RiskDecisionAction.REVIEW.name(), resp.getDecision());
    }

    @Test
    void evaluateRules_shouldUseDefaultRatio_whenJsonHasNoRatioOrMalformedKv() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();
        RiskDecisionEngine engine = new RiskDecisionEngine(evaluator);

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("count_1m", 3);

        RiskRule degradeNoRatio = RiskRule.builder()
                .id(61L)
                .priority(20)
                .status(1)
                .conditionExpr("#count_1m >= 1")
                .action(RiskDecisionAction.DEGRADE_PASS.name())
                .actionParams("{\"x\":1}")
                .build();
        RiskDecisionResponse noRatioResp = engine.evaluateRules(Arrays.asList(degradeNoRatio), ctx);
        assertEquals(RiskDecisionAction.DEGRADE_PASS.name(), noRatioResp.getDecision());
        assertEquals(0.5, noRatioResp.getDegradeRatio());

        RiskRule degradeBadKv = RiskRule.builder()
                .id(62L)
                .priority(10)
                .status(1)
                .conditionExpr("#count_1m >= 1")
                .action(RiskDecisionAction.DEGRADE_PASS.name())
                .actionParams("ratio=1=2")
                .build();
        RiskDecisionResponse badKvResp = engine.evaluateRules(Arrays.asList(degradeBadKv), ctx);
        assertEquals(RiskDecisionAction.DEGRADE_PASS.name(), badKvResp.getDecision());
        assertEquals(0.5, badKvResp.getDegradeRatio());
    }

    @Test
    void evaluateRules_shouldReturnPass_whenRulesListNull() {
        RiskDecisionEngine engine = new RiskDecisionEngine(new RuleExpressionEvaluator());

        RiskDecisionResponse resp = engine.evaluateRules(null, new HashMap<>());

        assertEquals(RiskDecisionAction.PASS.name(), resp.getDecision());
        assertEquals(0, resp.getRiskScore());
    }

    @Test
    void privateMethods_shouldCoverParseRatioPriorityAndTimeWindowBranches() throws Exception {
        RiskDecisionEngine engine = new RiskDecisionEngine(new RuleExpressionEvaluator());

        Method parseRatio = RiskDecisionEngine.class.getDeclaredMethod("parseRatio", String.class);
        parseRatio.setAccessible(true);
        assertNull(parseRatio.invoke(engine, new Object[]{null}));
        assertEquals(0.8, (Double) parseRatio.invoke(engine, "{\"ratio\":0.8}"));
        assertEquals(0.7, (Double) parseRatio.invoke(engine, "ratio=0.7"));
        assertNull(parseRatio.invoke(engine, "ratio=abc"));
        assertNull(parseRatio.invoke(engine, "ratio=1=2"));

        Method findPriority = RiskDecisionEngine.class.getDeclaredMethod("findPriority", Long.class, java.util.List.class);
        findPriority.setAccessible(true);
        assertEquals(0, (Integer) findPriority.invoke(engine, null, Arrays.asList()));

        RiskRule p0 = RiskRule.builder().id(701L).priority(null).build();
        RiskRule p9 = RiskRule.builder().id(702L).priority(9).build();
        assertEquals(0, (Integer) findPriority.invoke(engine, 701L, Arrays.asList(p0, p9)));
        assertEquals(9, (Integer) findPriority.invoke(engine, 702L, Arrays.asList(p0, p9)));

        Method inTimeWindow = RiskDecisionEngine.class.getDeclaredMethod("inTimeWindow", RiskRule.class, Map.class);
        inTimeWindow.setAccessible(true);
        assertTrue((Boolean) inTimeWindow.invoke(engine, null, null));

        RiskRule noWindow = RiskRule.builder().startTime(null).endTime(null).build();
        assertTrue((Boolean) inTimeWindow.invoke(engine, noWindow, new HashMap<>()));

        Date now = new Date();
        RiskRule notStarted = RiskRule.builder().startTime(new Date(now.getTime() + 60_000)).endTime(null).build();
        Map<String, Object> ctxNow = new HashMap<>();
        ctxNow.put("eventTime", java.time.LocalDateTime.now());
        assertFalse((Boolean) inTimeWindow.invoke(engine, notStarted, ctxNow));

        RiskRule ended = RiskRule.builder().startTime(null).endTime(new Date(now.getTime() - 60_000)).build();
        assertFalse((Boolean) inTimeWindow.invoke(engine, ended, ctxNow));

        RiskRule aroundNow = RiskRule.builder()
                .startTime(new Date(now.getTime() - 60_000))
                .endTime(new Date(now.getTime() + 60_000))
                .build();
        Map<String, Object> badCtxType = new HashMap<>();
        badCtxType.put("eventTime", "not-local-date-time");
        assertTrue((Boolean) inTimeWindow.invoke(engine, aroundNow, badCtxType));
    }

    @Test
    void privateMethods_shouldCoverDecideActionAndScoreBranches() throws Exception {
        RiskDecisionEngine engine = new RiskDecisionEngine(new RuleExpressionEvaluator());

        Method decideAction = RiskDecisionEngine.class.getDeclaredMethod("decideAction", java.util.List.class, java.util.List.class);
        decideAction.setAccessible(true);
        Method calcScore = RiskDecisionEngine.class.getDeclaredMethod("calcScore", java.util.List.class);
        calcScore.setAccessible(true);
        Method parseDegradeRatio = RiskDecisionEngine.class.getDeclaredMethod("parseDegradeRatio", java.util.List.class);
        parseDegradeRatio.setAccessible(true);

        assertEquals(RiskDecisionAction.PASS, decideAction.invoke(engine, null, null));
        assertEquals(0, calcScore.invoke(engine, new Object[]{null}));
        assertNull(parseDegradeRatio.invoke(engine, new Object[]{null}));

        RiskHitRule unknown = RiskHitRule.builder().ruleId(1L).action("UNKNOWN").build();
        assertEquals(RiskDecisionAction.PASS, decideAction.invoke(engine, Arrays.asList(unknown), null));

        RiskHitRule reject = RiskHitRule.builder().ruleId(2L).action("REJECT").build();
        RiskHitRule review = RiskHitRule.builder().ruleId(3L).action("REVIEW").build();
        assertEquals(RiskDecisionAction.REJECT, decideAction.invoke(engine, Arrays.asList(review, reject), null));

        RiskHitRule degradeNoParams = RiskHitRule.builder().ruleId(4L).action("DEGRADE_PASS").actionParams("{}").build();
        assertEquals(0.5, parseDegradeRatio.invoke(engine, Arrays.asList(degradeNoParams)));

        @SuppressWarnings("unchecked")
        int bigScore = (Integer) calcScore.invoke(engine, Arrays.asList(
                unknown, unknown, unknown, unknown, unknown, unknown, unknown, unknown, unknown, unknown, unknown
        ));
        assertEquals(100, bigScore);
    }
}
