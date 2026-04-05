package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskDashboardOverviewResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskDashboardTrendItem;
import com.whu.graduation.taskincentive.service.risk.RiskDashboardService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskDashboardControllerTest {

    @Test
    void overview_shouldReturnOk() {
        RiskDashboardService service = mock(RiskDashboardService.class);
        RiskDashboardController controller = new RiskDashboardController(service);
        when(service.overview(null, null)).thenReturn(new RiskDashboardOverviewResponse());

        ApiResponse<RiskDashboardOverviewResponse> result = controller.overview(null, null);

        assertEquals(0, result.getCode());
    }

    @Test
    void dailyTrend_shouldReturnOk() {
        RiskDashboardService service = mock(RiskDashboardService.class);
        RiskDashboardController controller = new RiskDashboardController(service);
        when(service.dailyTrend(null, null)).thenReturn(Collections.emptyList());

        ApiResponse<List<RiskDashboardTrendItem>> result = controller.dailyTrend(null, null);

        assertEquals(0, result.getCode());
    }
}

