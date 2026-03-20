package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.dto.risk.RiskRuleRequest;
import com.whu.graduation.taskincentive.service.risk.RiskRuleService;
import com.whu.graduation.taskincentive.dto.RiskRuleValidateResult;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 风控规则管理
 */
@RestController
@RequestMapping("/api/risk/rules")
@Validated
public class RiskRuleController {

    private final RiskRuleService riskRuleService;

    public RiskRuleController(RiskRuleService riskRuleService) {
        this.riskRuleService = riskRuleService;
    }

    @Operation(summary = "规则列表")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<PageResult<RiskRule>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        Page<RiskRule> p = new Page<>(page, size);
        Page<RiskRule> result = riskRuleService.page(p);
        PageResult<RiskRule> pr = PageResult.<RiskRule>builder()
                .total(result.getTotal())
                .page((int) result.getCurrent())
                .size((int) result.getSize())
                .items(result.getRecords())
                .build();
        return ApiResponse.success(pr);
    }

    @Operation(summary = "表达式校验")
    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<RiskRuleValidateResult> validate(@RequestBody RiskRuleRequest request) {
        return ApiResponse.success(riskRuleService.validateConditionExpr(request.getConditionExpr()));
    }

    @Operation(summary = "新增规则")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<RiskRule> create(@Valid @RequestBody RiskRuleRequest request) {
        RiskRuleValidateResult validate = riskRuleService.validateConditionExpr(request.getConditionExpr());
        if (!validate.isValid()) {
            return ApiResponse.error(400, validate.getMessage());
        }
        return ApiResponse.success(riskRuleService.create(request));
    }

    @Operation(summary = "更新规则")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<RiskRule> update(@PathVariable Long id, @Valid @RequestBody RiskRuleRequest request) {
        RiskRuleValidateResult validate = riskRuleService.validateConditionExpr(request.getConditionExpr());
        if (!validate.isValid()) {
            return ApiResponse.error(400, validate.getMessage());
        }
        return ApiResponse.success(riskRuleService.update(id, request));
    }

    @Operation(summary = "发布规则")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<Boolean> publish(@PathVariable Long id) {
        return ApiResponse.success(riskRuleService.publish(id));
    }

    @Operation(summary = "回滚规则")
    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<Boolean> rollback(@PathVariable Long id) {
        return ApiResponse.success(riskRuleService.rollback(id));
    }
}
