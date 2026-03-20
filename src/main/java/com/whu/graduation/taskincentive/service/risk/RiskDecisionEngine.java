package com.whu.graduation.taskincentive.service.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.graduation.taskincentive.common.enums.RiskDecisionAction;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskHitRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 风控决策引擎（规则评估与冲突处理）
 */
@Component
public class RiskDecisionEngine {

    private final RuleExpressionEvaluator evaluator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RiskDecisionEngine(RuleExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public RiskDecisionResponse evaluateRules(List<RiskRule> rules, Map<String, Object> context) {
        List<RiskHitRule> hits = new ArrayList<>();
        if (rules != null) {
            for (RiskRule rule : rules) {
                if (rule == null || rule.getStatus() == null || rule.getStatus() != 1) {
                    continue;
                }
                if (!inTimeWindow(rule, context)) {
                    continue;
                }
                boolean matched = evaluator.evaluate(rule.getConditionExpr(), context);
                if (matched) {
                    hits.add(RiskHitRule.builder()
                            .ruleId(rule.getId())
                            .ruleName(rule.getName())
                            .ruleType(rule.getType())
                            .action(rule.getAction())
                            .actionParams(rule.getActionParams())
                            .reasonCode(rule.getAction())
                            .build());
                }
            }
        }

        // 冲突处理：命中多条放行/降级规则，进入 REVIEW
        long passLike = hits.stream().filter(h -> RiskDecisionAction.PASS.name().equalsIgnoreCase(h.getAction())
                || RiskDecisionAction.DEGRADE_PASS.name().equalsIgnoreCase(h.getAction())).count();
        if (passLike > 1) {
            return RiskDecisionResponse.builder()
                    .decision(RiskDecisionAction.REVIEW.name())
                    .reasonCode("RULE_CONFLICT")
                    .hitRules(hits)
                    .riskScore(calcScore(hits))
                    .build();
        }

        RiskDecisionAction action = decideAction(hits, rules);
        RiskDecisionResponse.RiskDecisionResponseBuilder builder = RiskDecisionResponse.builder()
                .decision(action.name())
                .reasonCode(action.name())
                .hitRules(hits)
                .riskScore(calcScore(hits));

        if (action == RiskDecisionAction.DEGRADE_PASS) {
            builder.degradeRatio(parseDegradeRatio(hits));
        }
        return builder.build();
    }

    private RiskDecisionAction decideAction(List<RiskHitRule> hits, List<RiskRule> rules) {
        if (hits == null || hits.isEmpty()) {
            return RiskDecisionAction.PASS;
        }
        // 根据规则优先级从高到低选动作，拒绝优先
        hits.sort(Comparator.comparing(h -> findPriority(h.getRuleId(), rules), Comparator.reverseOrder()));
        for (RiskHitRule hit : hits) {
            if (RiskDecisionAction.REJECT.name().equalsIgnoreCase(hit.getAction())) {
                return RiskDecisionAction.REJECT;
            }
        }
        for (RiskHitRule hit : hits) {
            if (RiskDecisionAction.REVIEW.name().equalsIgnoreCase(hit.getAction())) {
                return RiskDecisionAction.REVIEW;
            }
        }
        for (RiskHitRule hit : hits) {
            if (RiskDecisionAction.FREEZE.name().equalsIgnoreCase(hit.getAction())) {
                return RiskDecisionAction.FREEZE;
            }
        }
        for (RiskHitRule hit : hits) {
            if (RiskDecisionAction.DEGRADE_PASS.name().equalsIgnoreCase(hit.getAction())) {
                return RiskDecisionAction.DEGRADE_PASS;
            }
        }
        return RiskDecisionAction.PASS;
    }

    private int findPriority(Long ruleId, List<RiskRule> rules) {
        if (ruleId == null || rules == null) return 0;
        for (RiskRule r : rules) {
            if (r != null && ruleId.equals(r.getId())) {
                return r.getPriority() == null ? 0 : r.getPriority();
            }
        }
        return 0;
    }

    private int calcScore(List<RiskHitRule> hits) {
        if (hits == null) return 0;
        return Math.min(100, hits.size() * 10);
    }

    private Double parseDegradeRatio(List<RiskHitRule> hits) {
        if (hits == null) return null;
        for (RiskHitRule hit : hits) {
            if (!RiskDecisionAction.DEGRADE_PASS.name().equalsIgnoreCase(hit.getAction())) {
                continue;
            }
            Double ratio = parseRatio(hit.getActionParams());
            if (ratio != null) return ratio;
        }
        return 0.5;
    }

    private Double parseRatio(String params) {
        if (params == null || params.trim().isEmpty()) return null;
        try {
            JsonNode node = objectMapper.readTree(params);
            if (node.has("ratio")) {
                return node.get("ratio").asDouble();
            }
        } catch (Exception ignored) {
        }
        if (params.contains("ratio")) {
            String[] parts = params.split("=");
            if (parts.length == 2) {
                try { return Double.parseDouble(parts[1]); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private boolean inTimeWindow(RiskRule rule, Map<String, Object> context) {
        if (rule == null) return true;
        java.util.Date start = rule.getStartTime();
        java.util.Date end = rule.getEndTime();
        if (start == null && end == null) return true;

        java.time.LocalDateTime eventTime = null;
        Object ctxTime = context == null ? null : context.get("eventTime");
        if (ctxTime instanceof java.time.LocalDateTime) {
            eventTime = (java.time.LocalDateTime) ctxTime;
        }
        if (eventTime == null) {
            eventTime = java.time.LocalDateTime.now();
        }
        java.time.Instant eventInstant = eventTime.atZone(java.time.ZoneId.systemDefault()).toInstant();

        if (start != null && eventInstant.isBefore(start.toInstant())) {
            return false;
        }
        if (end != null && eventInstant.isAfter(end.toInstant())) {
            return false;
        }
        return true;
    }
}
