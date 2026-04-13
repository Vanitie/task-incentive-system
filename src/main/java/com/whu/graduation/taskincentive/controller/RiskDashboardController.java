package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskDashboardOverviewResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskDashboardTrendItem;
import com.whu.graduation.taskincentive.service.risk.RiskDashboardService;
import com.whu.graduation.taskincentive.util.DateParamParser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 风控看板接口
 */
@RestController
@RequestMapping("/api/risk/dashboard")
public class RiskDashboardController {

    private final RiskDashboardService riskDashboardService;

    public RiskDashboardController(RiskDashboardService riskDashboardService) {
        this.riskDashboardService = riskDashboardService;
    }

    @Operation(summary = "风控看板总览")
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<RiskDashboardOverviewResponse> overview(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        Date startDate = DateParamParser.parseNullable(start, "start");
        Date endDate = DateParamParser.parseNullable(end, "end");
        return ApiResponse.success(riskDashboardService.overview(startDate, endDate));
    }

    @Operation(summary = "风控看板按天趋势")
    @GetMapping("/daily-trend")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<List<RiskDashboardTrendItem>> dailyTrend(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        Date startDate = DateParamParser.parseNullable(start, "start");
        Date endDate = DateParamParser.parseNullable(end, "end");
        return ApiResponse.success(riskDashboardService.dailyTrend(startDate, endDate));
    }
}
