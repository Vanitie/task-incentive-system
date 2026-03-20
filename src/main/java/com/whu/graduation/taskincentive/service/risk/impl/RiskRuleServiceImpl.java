package com.whu.graduation.taskincentive.service.risk.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dao.mapper.RiskRuleMapper;
import com.whu.graduation.taskincentive.dto.risk.RiskRuleRequest;
import com.whu.graduation.taskincentive.service.risk.RuleExpressionEvaluator;
import com.whu.graduation.taskincentive.service.risk.RiskCacheStore;
import com.whu.graduation.taskincentive.service.risk.RiskRuleService;
import com.whu.graduation.taskincentive.dto.RiskRuleValidateResult;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * 风控规则服务实现
 */
@Service
public class RiskRuleServiceImpl implements RiskRuleService {

    private static final Logger log = LoggerFactory.getLogger(RiskRuleServiceImpl.class);

    private final RiskRuleMapper riskRuleMapper;
    private final RiskCacheStore cacheStore;
    private final RuleExpressionEvaluator ruleExpressionEvaluator;

    public RiskRuleServiceImpl(RiskRuleMapper riskRuleMapper, RiskCacheStore cacheStore,
                               RuleExpressionEvaluator ruleExpressionEvaluator) {
        this.riskRuleMapper = riskRuleMapper;
        this.cacheStore = cacheStore;
        this.ruleExpressionEvaluator = ruleExpressionEvaluator;
    }

    @Override
    public Page<RiskRule> page(Page<RiskRule> page) {
        return riskRuleMapper.selectPage(page, new QueryWrapper<RiskRule>().orderByDesc("priority"));
    }

    @Override
    public RiskRule create(RiskRuleRequest request) {
        RiskRuleValidateResult validate = validateConditionExpr(request.getConditionExpr());
        if (!validate.isValid()) {
            throw new IllegalArgumentException(validate.getMessage());
        }
        RiskRule rule = RiskRule.builder()
                .id(IdWorker.getId())
                .name(request.getName())
                .type(request.getType())
                .priority(request.getPriority())
                .status(request.getStatus())
                .conditionExpr(request.getConditionExpr())
                .action(request.getAction())
                .actionParams(request.getActionParams())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .version(1)
                .createdBy(request.getCreatedBy())
                .updatedBy(request.getUpdatedBy())
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
        riskRuleMapper.insert(rule);
        refreshCache();
        return rule;
    }

    @Override
    public RiskRule update(Long id, RiskRuleRequest request) {
        RiskRuleValidateResult validate = validateConditionExpr(request.getConditionExpr());
        if (!validate.isValid()) {
            throw new IllegalArgumentException(validate.getMessage());
        }
        RiskRule rule = riskRuleMapper.selectById(id);
        if (rule == null) return null;
        rule.setName(request.getName());
        rule.setType(request.getType());
        rule.setPriority(request.getPriority());
        rule.setStatus(request.getStatus());
        rule.setConditionExpr(request.getConditionExpr());
        rule.setAction(request.getAction());
        rule.setActionParams(request.getActionParams());
        rule.setStartTime(request.getStartTime());
        rule.setEndTime(request.getEndTime());
        rule.setUpdatedBy(request.getUpdatedBy());
        rule.setUpdatedAt(new Date());
        riskRuleMapper.updateById(rule);
        refreshCache();
        return rule;
    }

    @Override
    public boolean publish(Long id) {
        RiskRule rule = riskRuleMapper.selectById(id);
        if (rule == null) return false;
        rule.setStatus(1);
        rule.setVersion(rule.getVersion() == null ? 1 : rule.getVersion() + 1);
        rule.setUpdatedAt(new Date());
        riskRuleMapper.updateById(rule);
        refreshCache();
        return true;
    }

    @Override
    public boolean rollback(Long id) {
        RiskRule rule = riskRuleMapper.selectById(id);
        if (rule == null) return false;
        rule.setStatus(0);
        rule.setUpdatedAt(new Date());
        riskRuleMapper.updateById(rule);
        refreshCache();
        return true;
    }

    @Override
    public RiskRuleValidateResult validateConditionExpr(String expression) {
        return ruleExpressionEvaluator.validate(expression);
    }

    private void refreshCache() {
        List<RiskRule> rules = riskRuleMapper.selectList(new QueryWrapper<RiskRule>().eq("status", 1));
        for (RiskRule rule : rules) {
            RiskRuleValidateResult validate = ruleExpressionEvaluator.validate(rule.getConditionExpr());
            if (!validate.isValid()) {
                log.warn("规则表达式不符合规范: ruleId={}, msg={}", rule.getId(), validate.getMessage());
            }
        }
        cacheStore.refreshRules(rules);
    }
}
