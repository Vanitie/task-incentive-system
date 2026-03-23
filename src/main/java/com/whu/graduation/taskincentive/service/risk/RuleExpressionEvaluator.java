package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.dto.RiskRuleValidateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 规则表达式解析器（基于 SpEL）
 */
@Component
public class RuleExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RuleExpressionEvaluator.class);

    private static final Set<String> KNOWN_VARIABLES = new HashSet<>(Arrays.asList(
            "count_1m", "count_1h", "amount_1d", "distinct_device_1d",
            "ip_count_1m", "device_count_1m",
            "userId", "taskId", "eventType", "amount", "eventTime",
            "userRiskLevel", "deviceRiskScore", "ipReputation", "taskBudgetRemain"
    ));

    private final ExpressionParser parser = new SpelExpressionParser();

    public boolean evaluate(String expression, Map<String, Object> variables) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        try {
            EvaluationContext context = buildSafeContext(variables);
            Expression exp = parser.parseExpression(expression);
            Boolean value = exp.getValue(context, Boolean.class);
            return value != null && value;
        } catch (Exception e) {
            log.warn("风控表达式执行失败: expr={}, err={}", expression, e.getMessage());
            return false;
        }
    }

    public RiskRuleValidateResult validate(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return RiskRuleValidateResult.invalid("表达式为空");
        }
        List<String> missingSharp = findMissingSharpVariables(expression);
        if (!missingSharp.isEmpty()) {
            return RiskRuleValidateResult.invalid("表达式变量应使用#前缀", missingSharp);
        }
        try {
            EvaluationContext context = buildSafeContext(buildDummyVariables());
            parser.parseExpression(expression).getValue(context);
            return RiskRuleValidateResult.valid();
        } catch (Exception e) {
            return RiskRuleValidateResult.invalid("表达式语法错误: " + e.getMessage());
        }
    }

    private EvaluationContext buildSafeContext(Map<String, Object> variables) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setTypeLocator(disabledTypeLocator());
        context.setMethodResolvers(Collections.emptyList());
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }
        return context;
    }

    private TypeLocator disabledTypeLocator() {
        return typeName -> {
            throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
        };
    }

    private Map<String, Object> buildDummyVariables() {
        Map<String, Object> vars = new HashMap<>();
        for (String v : KNOWN_VARIABLES) {
            vars.put(v, 1);
        }
        return vars;
    }

    private List<String> findMissingSharpVariables(String expression) {
        List<String> missing = new ArrayList<>();
        Set<String> missingSet = new LinkedHashSet<>();
        for (String var : KNOWN_VARIABLES) {
            // 仅匹配完整变量名
            Pattern bareVarPattern = Pattern.compile("(?<![#\\w])" + Pattern.quote(var) + "(?!\\w)");
            if (bareVarPattern.matcher(expression).find()) {
                missingSet.add(var);
            }
        }
        missing.addAll(missingSet);
        return missing;
    }
}
