package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.BenchmarkLoadTestDTO;
import com.whu.graduation.taskincentive.service.BenchmarkLoadTestService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/**
 * 基准压测接口：仅用于衡量接入层开销，不包含业务逻辑。
 */
@RestController
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkLoadTestService benchmarkLoadTestService;

    @Operation(summary = "压测空接口（无业务逻辑）")
    @GetMapping("/api/benchmark/noop")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<?> noop() {
        return ApiResponse.success(Collections.singletonMap("status", "ok"));
    }

    @Operation(summary = "启动压测任务（前端可传参）")
    @PostMapping("/api/benchmark/load-test/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BenchmarkLoadTestDTO.LoadTestStartResponse> startLoadTest(
            @RequestBody BenchmarkLoadTestDTO.LoadTestStartRequest request) {
        try {
            return ApiResponse.success(benchmarkLoadTestService.start(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @Operation(summary = "查询压测任务状态")
    @GetMapping("/api/benchmark/load-test/{runId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BenchmarkLoadTestDTO.LoadTestStatusResponse> loadTestStatus(@PathVariable String runId) {
        try {
            return ApiResponse.success(benchmarkLoadTestService.status(runId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }
}

