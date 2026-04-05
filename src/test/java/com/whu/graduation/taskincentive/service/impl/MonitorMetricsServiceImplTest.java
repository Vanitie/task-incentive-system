package com.whu.graduation.taskincentive.service.impl;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitorMetricsServiceImplTest {

    private static final DateTimeFormatter MINUTE_KEY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @BeforeEach
    void resetStatics() throws Exception {
        clearLatencyBucket();
        getAdder("TOTAL_COUNT").reset();
        getAdder("SUCCESS_COUNT").reset();
        getAdder("FAILURE_COUNT").reset();
    }

    @Test
    void hourRates_shouldReturnZeroWhenNoRequests() {
        MonitorMetricsServiceImpl service = new MonitorMetricsServiceImpl(new SimpleMeterRegistry());

        assertEquals(0.0, service.getHourSuccessRate());
        assertEquals(0.0, service.getHourFailureRate());
    }

    @Test
    void hourRates_shouldCalculateWithCounters() throws Exception {
        getAdder("TOTAL_COUNT").add(10);
        getAdder("SUCCESS_COUNT").add(7);
        getAdder("FAILURE_COUNT").add(3);

        MonitorMetricsServiceImpl service = new MonitorMetricsServiceImpl(new SimpleMeterRegistry());

        assertEquals(70.0, service.getHourSuccessRate());
        assertEquals(30.0, service.getHourFailureRate());
    }

    @Test
    void tpSeries_shouldReturn20Points_andUseCurrentMinuteData() throws Exception {
        @SuppressWarnings("unchecked")
        ConcurrentMap<String, List<Double>> bucket = (ConcurrentMap<String, List<Double>>) getLatencyBucketField().get(null);
        String currentKey = LocalDateTime.now().withSecond(0).withNano(0).format(MINUTE_KEY_FMT);
        bucket.put(currentKey, new ArrayList<>(List.of(10.0, 20.0, 30.0)));

        MonitorMetricsServiceImpl service = new MonitorMetricsServiceImpl(new SimpleMeterRegistry());
        Map<String, Object> out = service.getTpSeriesLast20Minutes();

        @SuppressWarnings("unchecked")
        List<String> xAxis = (List<String>) out.get("xAxis");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) out.get("series");

        assertEquals(20, xAxis.size());
        @SuppressWarnings("unchecked")
        List<Double> tp90 = (List<Double>) series.get(0).get("data");
        assertEquals(20, tp90.size());
        assertEquals(30.0, tp90.get(tp90.size() - 1));
    }

    @Test
    void qpsAndRequestCount_shouldUseMinuteBucketAndMeterRegistry() throws Exception {
        @SuppressWarnings("unchecked")
        ConcurrentMap<String, List<Double>> bucket = (ConcurrentMap<String, List<Double>>) getLatencyBucketField().get(null);
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        bucket.put(now.format(MINUTE_KEY_FMT), new ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0)));
        bucket.put(now.minusMinutes(1).format(MINUTE_KEY_FMT), new ArrayList<>(List.of(1.0, 2.0)));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Timer t = registry.timer("http.server.requests");
        t.record(Duration.ofMillis(10));
        t.record(Duration.ofMillis(20));

        MonitorMetricsServiceImpl service = new MonitorMetricsServiceImpl(registry);

        assertTrue(service.getQps() >= 0.0);
        assertEquals(2L, service.getRequestCount());
    }

    @Test
    void minutePercentiles_shouldReturnNonNegativeValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Timer timer = Timer.builder("http.server.requests")
                .publishPercentiles(0.90, 0.95, 0.99)
                .register(registry);
        timer.record(Duration.ofMillis(10));
        timer.record(Duration.ofMillis(40));
        timer.record(Duration.ofMillis(70));

        MonitorMetricsServiceImpl service = new MonitorMetricsServiceImpl(registry);

        assertTrue(service.getMinuteTp90Ms() >= 0.0);
        assertTrue(service.getMinuteTp95Ms() >= 0.0);
        assertTrue(service.getMinuteTp99Ms() >= 0.0);
    }

    @Test
    void systemMetrics_shouldReturnBoundedValuesAndFormattedTime() {
        MonitorMetricsServiceImpl service = new MonitorMetricsServiceImpl(new SimpleMeterRegistry());

        assertTrue(service.getCpuUsagePercent() >= 0.0);
        assertTrue(service.getMemoryUsagePercent() >= 0.0);
        assertTrue(service.getDiskUsagePercent() >= 0.0);
        assertTrue(service.getCurrentTime().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void hourWindowCounter_shouldCountSuccessAndFailure() throws Exception {
        MonitorMetricsServiceImpl.HourWindowCounter filter = new MonitorMetricsServiceImpl.HourWindowCounter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        FilterChain chain = mock(FilterChain.class);
        doNothing().when(chain).doFilter(org.mockito.ArgumentMatchers.eq(req), org.mockito.ArgumentMatchers.any());

        HttpServletResponse resp200 = mock(HttpServletResponse.class);
        when(resp200.getStatus()).thenReturn(200);
        filter.doFilterInternal(req, resp200, chain);

        HttpServletResponse resp500 = mock(HttpServletResponse.class);
        when(resp500.getStatus()).thenReturn(500);
        filter.doFilterInternal(req, resp500, chain);

        assertTrue(getAdder("TOTAL_COUNT").sum() >= 2);
        assertTrue(getAdder("SUCCESS_COUNT").sum() >= 1);
        assertTrue(getAdder("FAILURE_COUNT").sum() >= 1);
    }

    @Test
    void minutePercentiles_shouldReturnZero_whenTimerMissing() {
        MonitorMetricsServiceImpl service = new MonitorMetricsServiceImpl(new SimpleMeterRegistry());

        assertEquals(0.0, service.getMinuteTp90Ms());
        assertEquals(0.0, service.getMinuteTp95Ms());
        assertEquals(0.0, service.getMinuteTp99Ms());
    }

    @Test
    void privateHelpers_shouldCoverRoundAndPercentileBoundaries() throws Exception {
        java.lang.reflect.Method round = MonitorMetricsServiceImpl.class.getDeclaredMethod("round", double.class);
        round.setAccessible(true);
        assertEquals(0.0, (Double) round.invoke(new MonitorMetricsServiceImpl(new SimpleMeterRegistry()), Double.NaN));
        assertEquals(0.0, (Double) round.invoke(new MonitorMetricsServiceImpl(new SimpleMeterRegistry()), Double.POSITIVE_INFINITY));

        java.lang.reflect.Method percentile = MonitorMetricsServiceImpl.class.getDeclaredMethod("percentile", List.class, double.class);
        percentile.setAccessible(true);
        assertEquals(0.0, (Double) percentile.invoke(null, null, 0.9));
        assertEquals(0.0, (Double) percentile.invoke(null, List.of(), 0.9));
        assertEquals(1.0, (Double) percentile.invoke(null, List.of(1.0, 2.0, 3.0), 0.0));
        assertEquals(3.0, (Double) percentile.invoke(null, List.of(1.0, 2.0, 3.0), 2.0));
    }

    @Test
    void hourWindowCounter_shouldRollWindow_whenExpired() throws Exception {
        MonitorMetricsServiceImpl.HourWindowCounter filter = new MonitorMetricsServiceImpl.HourWindowCounter();
        getAdder("TOTAL_COUNT").add(5);
        getAdder("SUCCESS_COUNT").add(3);
        getAdder("FAILURE_COUNT").add(2);

        Field startField = MonitorMetricsServiceImpl.HourWindowCounter.class.getDeclaredField("windowStart");
        startField.setAccessible(true);
        startField.setLong(null, System.currentTimeMillis() - (61L * 60L * 1000L));

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(200);
        FilterChain chain = mock(FilterChain.class);
        doNothing().when(chain).doFilter(org.mockito.ArgumentMatchers.eq(req), org.mockito.ArgumentMatchers.any());

        assertDoesNotThrow(() -> filter.doFilterInternal(req, resp, chain));

        assertEquals(1L, getAdder("TOTAL_COUNT").sum());
        assertEquals(1L, getAdder("SUCCESS_COUNT").sum());
        assertEquals(0L, getAdder("FAILURE_COUNT").sum());
    }

    private static Field getLatencyBucketField() throws Exception {
        Field f = MonitorMetricsServiceImpl.class.getDeclaredField("MINUTE_LATENCY_BUCKET");
        f.setAccessible(true);
        return f;
    }

    private static void clearLatencyBucket() throws Exception {
        @SuppressWarnings("unchecked")
        ConcurrentMap<String, List<Double>> bucket = (ConcurrentMap<String, List<Double>>) getLatencyBucketField().get(null);
        bucket.clear();
    }

    private static LongAdder getAdder(String fieldName) throws Exception {
        Field f = MonitorMetricsServiceImpl.HourWindowCounter.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return (LongAdder) f.get(null);
    }
}

