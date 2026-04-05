package com.whu.graduation.taskincentive.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonitorMetricsServiceDefaultMethodTest {

    @Test
    void getResourceSeries_shouldBuildSingleSnapshot() {
        MonitorMetricsService service = new MonitorMetricsService() {
            @Override
            public double getQps() { return 0; }
            @Override
            public long getRequestCount() { return 0; }
            @Override
            public double getHourSuccessRate() { return 0; }
            @Override
            public double getHourFailureRate() { return 0; }
            @Override
            public double getMinuteTp90Ms() { return 0; }
            @Override
            public double getMinuteTp95Ms() { return 0; }
            @Override
            public double getMinuteTp99Ms() { return 0; }
            @Override
            public String getServerName() { return "node-1"; }
            @Override
            public double getCpuUsagePercent() { return 45.2; }
            @Override
            public double getMemoryUsagePercent() { return 68.7; }
            @Override
            public double getDiskUsagePercent() { return 20.4; }
            @Override
            public String getCurrentTime() { return "2026-04-04 17:40:01"; }
            @Override
            public Map<String, Object> getTpSeriesLast20Minutes() { return Map.of(); }
        };

        List<Map<String, String>> series = service.getResourceSeries();

        assertEquals(1, series.size());
        assertEquals("node-1", series.get(0).get("server"));
        assertEquals("45%", series.get(0).get("cpu"));
        assertEquals("69%", series.get(0).get("memory"));
        assertEquals("20%", series.get(0).get("disk"));
        assertEquals("2026-04-04 17:40", series.get(0).get("timestamp"));
    }
}

