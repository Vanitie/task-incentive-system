package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.service.MonitorMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "监控指标")
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorMetricsService monitorMetricsService;

    @Operation(summary = "QPS")
    @GetMapping("/qps")
    public ApiResponse<Double> qps() {
        return ApiResponse.success(monitorMetricsService.getQps());
    }

    @Operation(summary = "请求量")
    @GetMapping("/request-count")
    public ApiResponse<Long> requestCount() {
        return ApiResponse.success(monitorMetricsService.getRequestCount());
    }

    @Operation(summary = "小时成功率")
    @GetMapping("/hour-success-rate")
    public ApiResponse<Double> hourSuccessRate() {
        return ApiResponse.success(monitorMetricsService.getHourSuccessRate());
    }

    @Operation(summary = "小时失败率")
    @GetMapping("/hour-failure-rate")
    public ApiResponse<Double> hourFailureRate() {
        return ApiResponse.success(monitorMetricsService.getHourFailureRate());
    }

    @Operation(summary = "分钟TP90")
    @GetMapping("/minute-tp90")
    public ApiResponse<Double> minuteTp90() {
        return ApiResponse.success(monitorMetricsService.getMinuteTp90Ms());
    }

    @Operation(summary = "分钟TP95")
    @GetMapping("/minute-tp95")
    public ApiResponse<Double> minuteTp95() {
        return ApiResponse.success(monitorMetricsService.getMinuteTp95Ms());
    }

    @Operation(summary = "分钟TP99")
    @GetMapping("/minute-tp99")
    public ApiResponse<Double> minuteTp99() {
        return ApiResponse.success(monitorMetricsService.getMinuteTp99Ms());
    }

    @Operation(summary = "服务器名称")
    @GetMapping("/server-name")
    public ApiResponse<String> serverName() {
        return ApiResponse.success(monitorMetricsService.getServerName());
    }

    @Operation(summary = "CPU使用率")
    @GetMapping("/cpu-usage")
    public ApiResponse<Double> cpuUsage() {
        return ApiResponse.success(monitorMetricsService.getCpuUsagePercent());
    }

    @Operation(summary = "内存使用率")
    @GetMapping("/memory-usage")
    public ApiResponse<Double> memoryUsage() {
        return ApiResponse.success(monitorMetricsService.getMemoryUsagePercent());
    }

    @Operation(summary = "磁盘使用率")
    @GetMapping("/disk-usage")
    public ApiResponse<Double> diskUsage() {
        return ApiResponse.success(monitorMetricsService.getDiskUsagePercent());
    }

    @Operation(summary = "当前时间")
    @GetMapping("/time")
    public ApiResponse<String> time() {
        return ApiResponse.success(monitorMetricsService.getCurrentTime());
    }

    @Operation(summary = "最近20分钟tp90/tp95/tp99序列")
    @GetMapping("/tp-series-last20min")
    public ApiResponse<Object> tpSeriesLast20Min() {
        return ApiResponse.success(monitorMetricsService.getTpSeriesLast20Minutes());
    }

    @Operation(summary = "服务器资源监控数据（多台/多时点）")
    @GetMapping("/resource-series")
    public ApiResponse<List<Map<String, String>>> resourceSeries() {
        return ApiResponse.success(monitorMetricsService.getResourceSeries());
    }
}
