package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.service.MonitorMetricsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitorControllerTest {

    @Test
    void endpoints_shouldDelegateAndWrapResponse() {
        MonitorMetricsService service = mock(MonitorMetricsService.class);
        when(service.getQps()).thenReturn(12.3);
        when(service.getRequestCount()).thenReturn(99L);
        when(service.getHourSuccessRate()).thenReturn(97.5);
        when(service.getHourFailureRate()).thenReturn(2.5);
        when(service.getMinuteTp90Ms()).thenReturn(100.0);
        when(service.getMinuteTp95Ms()).thenReturn(150.0);
        when(service.getMinuteTp99Ms()).thenReturn(200.0);
        when(service.getServerName()).thenReturn("node-a");
        when(service.getCpuUsagePercent()).thenReturn(35.0);
        when(service.getMemoryUsagePercent()).thenReturn(60.0);
        when(service.getDiskUsagePercent()).thenReturn(70.0);
        when(service.getCurrentTime()).thenReturn("2026-04-04 17:30:00");

        Map<String, Object> tpSeries = Map.of("tp90", List.of(100, 120));
        List<Map<String, String>> resourceSeries = List.of(Map.of("server", "node-a", "cpu", "35%"));
        when(service.getTpSeriesLast20Minutes()).thenReturn(tpSeries);
        when(service.getResourceSeries()).thenReturn(resourceSeries);

        MonitorController controller = new MonitorController(service);

        assertEquals(12.3, controller.qps().getData());
        assertEquals(99L, controller.requestCount().getData());
        assertEquals(97.5, controller.hourSuccessRate().getData());
        assertEquals(2.5, controller.hourFailureRate().getData());
        assertEquals(100.0, controller.minuteTp90().getData());
        assertEquals(150.0, controller.minuteTp95().getData());
        assertEquals(200.0, controller.minuteTp99().getData());
        assertEquals("node-a", controller.serverName().getData());
        assertEquals(35.0, controller.cpuUsage().getData());
        assertEquals(60.0, controller.memoryUsage().getData());
        assertEquals(70.0, controller.diskUsage().getData());
        assertEquals("2026-04-04 17:30:00", controller.time().getData());

        ApiResponse<Object> tpResp = controller.tpSeriesLast20Min();
        assertEquals(tpSeries, tpResp.getData());
        assertEquals(resourceSeries, controller.resourceSeries().getData());

        verify(service).getQps();
        verify(service).getRequestCount();
        verify(service).getHourSuccessRate();
        verify(service).getHourFailureRate();
        verify(service).getMinuteTp90Ms();
        verify(service).getMinuteTp95Ms();
        verify(service).getMinuteTp99Ms();
        verify(service).getServerName();
        verify(service).getCpuUsagePercent();
        verify(service).getMemoryUsagePercent();
        verify(service).getDiskUsagePercent();
        verify(service).getCurrentTime();
        verify(service).getTpSeriesLast20Minutes();
        verify(service).getResourceSeries();
    }
}

