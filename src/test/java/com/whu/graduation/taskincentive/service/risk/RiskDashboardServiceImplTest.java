package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.dao.mapper.RiskDecisionLogMapper;
import com.whu.graduation.taskincentive.dto.risk.RiskDashboardOverviewResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskDashboardTrendItem;
import com.whu.graduation.taskincentive.service.risk.impl.RiskDashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
