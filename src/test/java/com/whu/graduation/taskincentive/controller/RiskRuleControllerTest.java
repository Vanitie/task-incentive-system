package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.RiskRuleValidateResult;
import com.whu.graduation.taskincentive.dto.risk.RiskRuleRequest;
import com.whu.graduation.taskincentive.service.risk.RiskRuleService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskRuleControllerTest {

    @Test
    void list_shouldReturnOk() {
        RiskRuleService service = mock(RiskRuleService.class);
        RiskRuleController controller = new RiskRuleController(service);
        Page<RiskRule> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(Collections.singletonList(new RiskRule()));
        when(service.page(any())).thenReturn(page);

        assertEquals(0, controller.list(1, 20).getCode());
    }

    @Test
    void validate_shouldReturnOk() {
        RiskRuleService service = mock(RiskRuleService.class);
        RiskRuleController controller = new RiskRuleController(service);
        RiskRuleRequest request = new RiskRuleRequest();
        request.setConditionExpr("score > 10");
        when(service.validateConditionExpr("score > 10")).thenReturn(RiskRuleValidateResult.valid());

        ApiResponse<RiskRuleValidateResult> result = controller.validate(request);

        assertEquals(0, result.getCode());
        assertTrue(result.getData().isValid());
    }

    @Test
    void createAndUpdateAndLifecycle_shouldReturnExpectedCodes() {
        RiskRuleService service = mock(RiskRuleService.class);
        RiskRuleController controller = new RiskRuleController(service);
        RiskRuleRequest request = new RiskRuleRequest();
        request.setConditionExpr("bad_expr");

        when(service.validateConditionExpr("bad_expr")).thenReturn(RiskRuleValidateResult.invalid("expr invalid"));
        assertEquals(400, controller.create(request).getCode());
        assertEquals(400, controller.update(1L, request).getCode());

        when(service.publish(1L)).thenReturn(true);
        when(service.rollback(1L)).thenReturn(true);
        assertEquals(0, controller.publish(1L).getCode());
        assertEquals(0, controller.rollback(1L).getCode());
    }
}


