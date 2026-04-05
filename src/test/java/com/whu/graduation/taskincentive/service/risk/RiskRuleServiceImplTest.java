package com.whu.graduation.taskincentive.service.risk;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dao.mapper.RiskRuleMapper;
import com.whu.graduation.taskincentive.dto.RiskRuleValidateResult;
import com.whu.graduation.taskincentive.dto.risk.RiskRuleRequest;
import com.whu.graduation.taskincentive.service.risk.impl.RiskRuleServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskRuleServiceImplTest {

    @Test
    void pageCreateUpdatePublishRollback_shouldCoverCoreBranches() {
        RiskRuleMapper mapper = mock(RiskRuleMapper.class);
        RiskCacheStore cacheStore = mock(RiskCacheStore.class);
        RuleExpressionEvaluator evaluator = mock(RuleExpressionEvaluator.class);
        RiskRuleServiceImpl service = new RiskRuleServiceImpl(mapper, cacheStore, evaluator);

        Page<RiskRule> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(new RiskRule()));
        when(mapper.selectPage(any(), any())).thenReturn(page);
        assertEquals(1, service.page(new Page<>(1, 10)).getRecords().size());

        RiskRuleRequest request = RiskRuleRequest.builder()
                .name("r1")
                .type("POINT")
                .priority(1)
                .status(1)
                .conditionExpr("#amount > 1")
                .action("PASS")
                .build();

        when(evaluator.validate("#amount > 1")).thenReturn(RiskRuleValidateResult.valid());
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(
                RiskRule.builder().id(11L).conditionExpr("#amount > 1").build()
        ));

        RiskRule created = service.create(request);
        assertNotNull(created.getId());
        verify(mapper).insert(any(RiskRule.class));
        verify(cacheStore).refreshRules(any());

        RiskRule existing = RiskRule.builder().id(100L).version(1).build();
        when(mapper.selectById(100L)).thenReturn(existing);
        RiskRule updated = service.update(100L, request);
        assertEquals(100L, updated.getId());

        assertEquals(true, service.publish(100L));
        assertEquals(true, service.rollback(100L));

        when(mapper.selectById(404L)).thenReturn(null);
        assertNull(service.update(404L, request));
        assertEquals(false, service.publish(404L));
        assertEquals(false, service.rollback(404L));
    }

    @Test
    void createAndUpdate_shouldThrowWhenExpressionInvalid() {
        RiskRuleMapper mapper = mock(RiskRuleMapper.class);
        RiskCacheStore cacheStore = mock(RiskCacheStore.class);
        RuleExpressionEvaluator evaluator = mock(RuleExpressionEvaluator.class);
        RiskRuleServiceImpl service = new RiskRuleServiceImpl(mapper, cacheStore, evaluator);

        RiskRuleRequest request = RiskRuleRequest.builder().conditionExpr("bad").build();
        when(evaluator.validate("bad")).thenReturn(RiskRuleValidateResult.invalid("bad expr"));

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        assertThrows(IllegalArgumentException.class, () -> service.update(1L, request));
    }
}

