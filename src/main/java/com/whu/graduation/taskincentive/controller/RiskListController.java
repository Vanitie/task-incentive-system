package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskBlacklist;
import com.whu.graduation.taskincentive.dao.entity.RiskWhitelist;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.dto.risk.RiskListRequest;
import com.whu.graduation.taskincentive.service.risk.RiskListService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 风控名单管理
 */
@RestController
@RequestMapping("/api/risk")
@Validated
public class RiskListController {

    private final RiskListService riskListService;

    public RiskListController(RiskListService riskListService) {
        this.riskListService = riskListService;
    }

    @Operation(summary = "黑名单列表")
    @GetMapping("/blacklist")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<PageResult<RiskBlacklist>> blacklist(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        Page<RiskBlacklist> p = new Page<>(page, size);
        Page<RiskBlacklist> result = riskListService.pageBlacklist(p);
        PageResult<RiskBlacklist> pr = PageResult.<RiskBlacklist>builder()
                .total(result.getTotal())
                .page((int) result.getCurrent())
                .size((int) result.getSize())
                .items(result.getRecords())
                .build();
        return ApiResponse.success(pr);
    }

    @Operation(summary = "新增黑名单")
    @PostMapping("/blacklist")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<RiskBlacklist> addBlacklist(@Valid @RequestBody RiskListRequest request) {
        return ApiResponse.success(riskListService.addBlacklist(request));
    }

    @Operation(summary = "白名单列表")
    @GetMapping("/whitelist")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<PageResult<RiskWhitelist>> whitelist(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        Page<RiskWhitelist> p = new Page<>(page, size);
        Page<RiskWhitelist> result = riskListService.pageWhitelist(p);
        PageResult<RiskWhitelist> pr = PageResult.<RiskWhitelist>builder()
                .total(result.getTotal())
                .page((int) result.getCurrent())
                .size((int) result.getSize())
                .items(result.getRecords())
                .build();
        return ApiResponse.success(pr);
    }

    @Operation(summary = "新增白名单")
    @PostMapping("/whitelist")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<RiskWhitelist> addWhitelist(@Valid @RequestBody RiskListRequest request) {
        return ApiResponse.success(riskListService.addWhitelist(request));
    }
}
