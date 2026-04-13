package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionLogService;
import com.whu.graduation.taskincentive.util.DateParamParser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * 风控决策日志查询
 */
@RestController
@RequestMapping("/api/risk/decisions")
public class RiskDecisionLogController {

    private final RiskDecisionLogService riskDecisionLogService;

    public RiskDecisionLogController(RiskDecisionLogService riskDecisionLogService) {
        this.riskDecisionLogService = riskDecisionLogService;
    }

    @Operation(summary = "决策日志查询")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<PageResult<RiskDecisionLog>> list(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         @RequestParam(required = false) Long taskId,
                                                         @RequestParam(required = false) Long userId,
                                                         @RequestParam(required = false) String userName,
                                                         @RequestParam(required = false) String taskName,
                                                         @RequestParam(required = false) String decision,
                                                         @RequestParam(required = false) String start,
                                                         @RequestParam(required = false) String end) {
        Date startDate = DateParamParser.parseNullable(start, "start");
        Date endDate = DateParamParser.parseNullable(end, "end");
        Page<RiskDecisionLog> p = new Page<>(page, size);
        Page<RiskDecisionLog> result = riskDecisionLogService.page(p, taskId, userId, userName, taskName, decision, startDate, endDate);
        PageResult<RiskDecisionLog> pr = PageResult.<RiskDecisionLog>builder()
                .total(result.getTotal())
                .page((int) result.getCurrent())
                .size((int) result.getSize())
                .items(result.getRecords())
                .build();
        return ApiResponse.success(pr);
    }
}
