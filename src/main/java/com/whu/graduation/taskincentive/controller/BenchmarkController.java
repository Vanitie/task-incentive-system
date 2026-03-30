package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/**
 * 基准压测接口：仅用于衡量接入层开销，不包含业务逻辑。
 */
@RestController
public class BenchmarkController {

    @Operation(summary = "压测空接口（无业务逻辑）")
    @GetMapping("/api/benchmark/noop")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<?> noop() {
        return ApiResponse.success(Collections.singletonMap("status", "ok"));
    }
}

