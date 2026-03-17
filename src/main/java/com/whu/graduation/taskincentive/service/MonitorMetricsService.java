package com.whu.graduation.taskincentive.service;

import java.util.Map;

public interface MonitorMetricsService {

    double getQps();

    long getRequestCount();

    double getHourSuccessRate();

    double getHourFailureRate();

    double getMinuteTp90Ms();

    double getMinuteTp95Ms();

    double getMinuteTp99Ms();

    String getServerName();

    double getCpuUsagePercent();

    double getMemoryUsagePercent();

    double getDiskUsagePercent();

    String getCurrentTime();

    /**
     * 获取最近20分钟每分钟的tp90、tp95、tp99序列
     */
    Map<String, Object> getTpSeriesLast20Minutes();

    /**
     * 获取服务器资源监控数据（多台/多时点）
     * @return List<Map<String, String>>，每个map包含server、cpu、memory、disk、timestamp
     */
    default java.util.List<java.util.Map<String, String>> getResourceSeries() {
        // 示例实现：仅返回本机当前数据，可扩展为多台/多时点
        java.util.List<java.util.Map<String, String>> list = new java.util.ArrayList<>();
        String server = getServerName();
        String cpu = String.format("%.0f%%", getCpuUsagePercent());
        String memory = String.format("%.0f%%", getMemoryUsagePercent());
        String disk = String.format("%.0f%%", getDiskUsagePercent());
        String timestamp = getCurrentTime().substring(0, 16); // 精确到分钟
        java.util.Map<String, String> map = java.util.Map.of(
                "server", server,
                "cpu", cpu,
                "memory", memory,
                "disk", disk,
                "timestamp", timestamp
        );
        list.add(map);
        return list;
    }
}
