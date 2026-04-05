package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.dao.mapper.RiskDecisionLogMapper;
import com.whu.graduation.taskincentive.dto.risk.RiskDashboardOverviewResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskDashboardTrendItem;
import com.whu.graduation.taskincentive.service.risk.impl.RiskDashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RiskDashboardServiceImplTest {

    private RiskDecisionLogMapper riskDecisionLogMapper;
    private RiskDashboardServiceImpl service;

    @BeforeEach
    public void setUp() {
        riskDecisionLogMapper = Mockito.mock(RiskDecisionLogMapper.class);
        service = new RiskDashboardServiceImpl(riskDecisionLogMapper);
    }

    @Test
    public void overview_shouldCalculateRatesAndLatency() {
        Date start = new Date(System.currentTimeMillis() - 3600_000L);
        Date end = new Date();

        List<Map<String, Object>> decisionRows = new ArrayList<>();
        decisionRows.add(row("PASS", 8));
        decisionRows.add(row("REJECT", 2));
        decisionRows.add(row("FREEZE", 0));
        when(riskDecisionLogMapper.countByDecision(start, end)).thenReturn(decisionRows);
        when(riskDecisionLogMapper.selectLatencies(start, end)).thenReturn(Arrays.asList(10L, 20L, 30L, 40L));

        RiskDashboardOverviewResponse res = service.overview(start, end);

        assertEquals(10, res.getTotal());
        assertEquals(8, res.getPass());
        assertEquals(2, res.getReject());
        assertEquals(0, res.getFreeze());
        assertEquals(20.0, res.getInterceptRate());
        assertEquals(80.0, res.getPassRate());
        assertEquals(25.0, res.getAvgLatencyMs());
        assertEquals(40.0, res.getP95LatencyMs());
        assertEquals(40.0, res.getP99LatencyMs());
    }

    @Test
    public void dailyTrend_shouldFillMissingDays() throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, -1);
        Date start = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 3);
        Date end = cal.getTime();

        String day1 = sdf.format(start);
        Calendar c2 = Calendar.getInstance();
        c2.setTime(start);
        c2.add(Calendar.DAY_OF_MONTH, 1);
        String day2 = sdf.format(c2.getTime());

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(day1, "PASS", 2));
        rows.add(row(day1, "REJECT", 1));
        rows.add(row(day2, "PASS", 3));
        when(riskDecisionLogMapper.countDailyByDecision(start, end)).thenReturn(rows);

        List<RiskDashboardTrendItem> result = service.dailyTrend(start, end);

        assertEquals(3, result.size());
        assertEquals(3, result.get(0).getTotal());
        assertEquals(3, result.get(1).getTotal());
        assertEquals(0, result.get(2).getTotal());
    }

    @Test
    public void overview_shouldHandleNullRangeInvalidCountAndNullLatencyList() {
        List<Map<String, Object>> decisionRows = new ArrayList<>();
        decisionRows.add(row("PASS", 3));
        Map<String, Object> invalidCount = new HashMap<>();
        invalidCount.put("decision", "REJECT");
        invalidCount.put("cnt", "bad-number");
        decisionRows.add(invalidCount);
        Map<String, Object> nullDecision = new HashMap<>();
        nullDecision.put("decision", null);
        nullDecision.put("cnt", 100);
        decisionRows.add(nullDecision);

        when(riskDecisionLogMapper.countByDecision(any(), any())).thenReturn(decisionRows);
        when(riskDecisionLogMapper.selectLatencies(any(), any())).thenReturn(null);

        RiskDashboardOverviewResponse res = service.overview(null, null);

        assertNotNull(res.getStart());
        assertNotNull(res.getEnd());
        assertEquals(3, res.getTotal());
        assertEquals(3, res.getPass());
        assertEquals(0, res.getReject());
        assertEquals(0.0, res.getAvgLatencyMs());
        assertEquals(0.0, res.getP95LatencyMs());
        assertEquals(0.0, res.getP99LatencyMs());
    }

    @Test
    public void dailyTrend_shouldSkipRowsWithMissingDateOrDecision() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date start = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 2);
        Date end = cal.getTime();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String day = sdf.format(start);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(day, "FREEZE", 1));
        Map<String, Object> missingDate = new HashMap<>();
        missingDate.put("decision", "PASS");
        missingDate.put("cnt", 10);
        rows.add(missingDate);
        Map<String, Object> missingDecision = new HashMap<>();
        missingDecision.put("the_date", day);
        missingDecision.put("cnt", 10);
        rows.add(missingDecision);

        when(riskDecisionLogMapper.countDailyByDecision(start, end)).thenReturn(rows);

        List<RiskDashboardTrendItem> result = service.dailyTrend(start, end);

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getFreeze());
        assertEquals(1, result.get(0).getTotal());
        assertTrue(result.get(1).getStatusCounts().values().stream().allMatch(v -> v == 0));
    }

    private Map<String, Object> row(String decision, long cnt) {
        Map<String, Object> map = new HashMap<>();
        map.put("decision", decision);
        map.put("cnt", cnt);
        return map;
    }

    private Map<String, Object> row(String date, String decision, long cnt) {
        Map<String, Object> map = new HashMap<>();
        map.put("the_date", date);
        map.put("decision", decision);
        map.put("cnt", cnt);
        return map;
    }

    @Test
    public void privateHelpers_shouldCoverRatesAndPercentileBranches() throws Exception {
        Method buildStatusRates = RiskDashboardServiceImpl.class.getDeclaredMethod("buildStatusRates", Map.class, long.class);
        buildStatusRates.setAccessible(true);
        Method toLong = RiskDashboardServiceImpl.class.getDeclaredMethod("toLong", Object.class);
        toLong.setAccessible(true);
        Method average = RiskDashboardServiceImpl.class.getDeclaredMethod("average", List.class);
        average.setAccessible(true);
        Method percentile = RiskDashboardServiceImpl.class.getDeclaredMethod("percentile", List.class, double.class);
        percentile.setAccessible(true);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("PASS", 2L);
        counts.put("REJECT", 1L);
        @SuppressWarnings("unchecked")
        Map<String, Double> zeroRates = (Map<String, Double>) buildStatusRates.invoke(service, counts, 0L);
        assertEquals(0.0, zeroRates.get("PASS"));

        @SuppressWarnings("unchecked")
        Map<String, Double> nonZeroRates = (Map<String, Double>) buildStatusRates.invoke(service, counts, 3L);
        assertEquals(66.67, nonZeroRates.get("PASS"));

        assertEquals(0L, toLong.invoke(service, new Object[]{null}));
        assertEquals(12L, toLong.invoke(service, 12));
        assertEquals(7L, toLong.invoke(service, "7"));
        assertEquals(0L, toLong.invoke(service, "bad"));

        assertEquals(0.0, average.invoke(service, new Object[]{null}));
        assertEquals(0.0, average.invoke(service, List.of()));
        assertEquals(1.5, (Double) average.invoke(service, List.of(1L, 2L)));

        assertEquals(0.0, percentile.invoke(service, new Object[]{null, 0.9}));
        assertEquals(0.0, percentile.invoke(service, List.of(), 0.9));
        assertEquals(0.0, percentile.invoke(service, Arrays.asList(null, null), 0.9));
        assertEquals(1.0, percentile.invoke(service, List.of(1L, 2L, 3L), 0.0));
        assertEquals(3.0, percentile.invoke(service, List.of(1L, 2L, 3L), 2.0));
    }
}
