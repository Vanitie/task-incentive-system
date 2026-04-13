package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskBlacklist;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dao.entity.RiskWhitelist;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.RiskRuleValidateResult;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionRequest;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskQuotaRequest;
import com.whu.graduation.taskincentive.dto.risk.RiskRuleRequest;
import com.whu.graduation.taskincentive.service.risk.RiskDashboardService;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionLogService;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionService;
import com.whu.graduation.taskincentive.service.risk.RiskListService;
import com.whu.graduation.taskincentive.service.risk.RiskQuotaService;
import com.whu.graduation.taskincentive.service.risk.RiskRuleService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskControllersUnitTest {

    @Test
    void riskDecisionControllerEvaluate_shouldReturnOkResponse() {
        RiskDecisionService service = mock(RiskDecisionService.class);
        RiskDecisionController controller = new RiskDecisionController(service);
        RiskDecisionResponse response = RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").build();
        when(service.evaluate(org.mockito.Mockito.any())).thenReturn(response);

        ApiResponse<RiskDecisionResponse> result = controller.evaluate(new RiskDecisionRequest());

        assertEquals(0, result.getCode());
        assertEquals("PASS", result.getData().getDecision());
    }

    @Test
    void riskRuleControllerCreate_shouldReturnErrorWhenExpressionInvalid() {
        RiskRuleService service = mock(RiskRuleService.class);
        RiskRuleController controller = new RiskRuleController(service);
        RiskRuleRequest request = new RiskRuleRequest();
        request.setConditionExpr("bad_expr");
        when(service.validateConditionExpr("bad_expr")).thenReturn(RiskRuleValidateResult.invalid("expr invalid"));

        ApiResponse<RiskRule> result = controller.create(request);

        assertEquals(400, result.getCode());
        assertEquals("expr invalid", result.getMsg());
    }

    @Test
    void riskRuleControllerList_shouldBuildPageResult() {
        RiskRuleService service = mock(RiskRuleService.class);
        RiskRuleController controller = new RiskRuleController(service);
        Page<RiskRule> page = new Page<>(2, 5);
        page.setTotal(9);
        page.setRecords(Collections.singletonList(new RiskRule()));
        when(service.page(org.mockito.Mockito.any())).thenReturn(page);

        ApiResponse<?> result = controller.list(2, 5);

        assertEquals(0, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void riskListControllerEndpoints_shouldReturnOk() {
        RiskListService service = mock(RiskListService.class);
        RiskListController controller = new RiskListController(service);
        Page<RiskBlacklist> blacklistPage = new Page<>(1, 20);
        blacklistPage.setRecords(Collections.singletonList(new RiskBlacklist()));
        Page<RiskWhitelist> whitelistPage = new Page<>(1, 20);
        whitelistPage.setRecords(Collections.singletonList(new RiskWhitelist()));
        when(service.pageBlacklist(org.mockito.Mockito.any())).thenReturn(blacklistPage);
        when(service.pageWhitelist(org.mockito.Mockito.any())).thenReturn(whitelistPage);
        when(service.addBlacklist(org.mockito.Mockito.any())).thenReturn(new RiskBlacklist());
        when(service.addWhitelist(org.mockito.Mockito.any())).thenReturn(new RiskWhitelist());

        assertEquals(0, controller.blacklist(1, 20).getCode());
        assertEquals(0, controller.whitelist(1, 20).getCode());
        assertEquals(0, controller.addBlacklist(new com.whu.graduation.taskincentive.dto.risk.RiskListRequest()).getCode());
        assertEquals(0, controller.addWhitelist(new com.whu.graduation.taskincentive.dto.risk.RiskListRequest()).getCode());
    }

    @Test
    void riskQuotaControllerEndpoints_shouldReturnOk() {
        RiskQuotaService service = mock(RiskQuotaService.class);
        RiskQuotaController controller = new RiskQuotaController(service);
        Page<RiskQuota> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(new RiskQuota()));
        when(service.page(org.mockito.Mockito.any())).thenReturn(page);
        when(service.create(org.mockito.Mockito.any())).thenReturn(new RiskQuota());
        when(service.update(org.mockito.Mockito.any())).thenReturn(new RiskQuota());
        when(service.deleteById(1L)).thenReturn(true);

        ApiResponse<?> listResult = controller.list(1, 20);
        ApiResponse<RiskQuota> createResult = controller.create(new RiskQuotaRequest());
        ApiResponse<RiskQuota> updateResult = controller.update(new RiskQuotaRequest());
        ApiResponse<Boolean> deleteResult = controller.delete(1L);

        assertEquals(0, listResult.getCode());
        assertEquals(0, createResult.getCode());
        assertEquals(0, updateResult.getCode());
        assertTrue(deleteResult.getData());
    }

    @Test
    void riskDashboardAndDecisionLogControllers_shouldReturnOk() {
        RiskDashboardService dashboardService = mock(RiskDashboardService.class);
        RiskDashboardController dashboardController = new RiskDashboardController(dashboardService);
        when(dashboardService.overview(null, null)).thenReturn(new com.whu.graduation.taskincentive.dto.risk.RiskDashboardOverviewResponse());
        when(dashboardService.dailyTrend(null, null)).thenReturn(Collections.emptyList());

        assertEquals(0, dashboardController.overview(null, null).getCode());
        assertEquals(0, dashboardController.dailyTrend(null, null).getCode());

        RiskDecisionLogService decisionLogService = mock(RiskDecisionLogService.class);
        RiskDecisionLogController decisionLogController = new RiskDecisionLogController(decisionLogService);
        Page<RiskDecisionLog> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(new RiskDecisionLog()));
        when(decisionLogService.page(
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any()))
                .thenReturn(page);

        ApiResponse<?> result = decisionLogController.list(1, 20, null, null, null, null, null, null, null);
        assertEquals(0, result.getCode());
    }
}

