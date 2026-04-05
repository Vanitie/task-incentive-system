package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.dto.RiskRuleValidateResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RuleExpressionEvaluatorTest {

    @Test
    public void validate_shouldRejectMissingSharp() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();
        RiskRuleValidateResult result = evaluator.validate("count_1m > 10");
        assertFalse(result.isValid());
    }

    @Test
    public void evaluate_shouldWorkWithSharpVariables() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();
        Map<String, Object> vars = new HashMap<>();
        vars.put("count_1m", 11);
        boolean hit = evaluator.evaluate("#count_1m > 10", vars);
        assertTrue(hit);
    }

    @Test
    public void evaluate_shouldReturnFalse_whenExpressionEmptyOrInvalid() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();

        assertFalse(evaluator.evaluate(null, Map.of("count_1m", 11)));
        assertFalse(evaluator.evaluate("   ", Map.of("count_1m", 11)));
        assertFalse(evaluator.evaluate("#count_1m >", Map.of("count_1m", 11)));
    }

    @Test
    public void evaluate_shouldReturnFalse_whenTypeAccessOrNonBooleanResult() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();

        assertFalse(evaluator.evaluate("T(java.lang.System).currentTimeMillis() > 0", Map.of()));
        assertFalse(evaluator.evaluate("#count_1m + 1", Map.of("count_1m", 1)));
    }

    @Test
    public void validate_shouldCoverEmptySyntaxAndValidCases() {
        RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();

        RiskRuleValidateResult empty = evaluator.validate(" ");
        assertFalse(empty.isValid());

        RiskRuleValidateResult syntax = evaluator.validate("#count_1m >");
        assertFalse(syntax.isValid());

        RiskRuleValidateResult ok = evaluator.validate("#count_1m > 10 && #amount_1d >= 1");
        assertTrue(ok.isValid());
    }
}
