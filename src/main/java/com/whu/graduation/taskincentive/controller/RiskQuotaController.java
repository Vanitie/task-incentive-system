package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.dto.risk.RiskQuotaRequest;
import com.whu.graduation.taskincentive.service.risk.RiskQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 风控配额管理
 */
@RestController
@RequestMapping("/api/risk/quotas")
@Validated
public class RiskQuotaController {

    private final RiskQuotaService riskQuotaService;

    public RiskQuotaController(RiskQuotaService riskQuotaService) {
        this.riskQuotaService = riskQuotaService;
    }

    @Operation(summary = "配额列表")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<PageResult<RiskQuota>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Page<RiskQuota> p = new Page<>(page, size);
        Page<RiskQuota> result = riskQuotaService.page(p);
        PageResult<RiskQuota> pr = PageResult.<RiskQuota>builder()
                .total(result.getTotal())
                .page((int) result.getCurrent())
                .size((int) result.getSize())
                .items(result.getRecords())
                .build();
        return ApiResponse.success(pr);
    }

    @Operation(summary = "更新配额")
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<RiskQuota> update(@Valid @RequestBody RiskQuotaRequest request) {
        return ApiResponse.success(riskQuotaService.update(request));
    }
}
