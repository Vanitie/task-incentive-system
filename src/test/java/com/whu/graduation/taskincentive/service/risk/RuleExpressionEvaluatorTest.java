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
}
