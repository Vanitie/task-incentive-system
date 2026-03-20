package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionRequest;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 风控实时决策接口
 */
@RestController
@RequestMapping("/api/risk/decision")
@Validated
public class RiskDecisionController {

    private final RiskDecisionService riskDecisionService;

    public RiskDecisionController(RiskDecisionService riskDecisionService) {
        this.riskDecisionService = riskDecisionService;
    }

    @Operation(summary = "风控实时决策")
    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<RiskDecisionResponse> evaluate(@Valid @RequestBody RiskDecisionRequest request) {
        return ApiResponse.success(riskDecisionService.evaluate(request));
    }
}
