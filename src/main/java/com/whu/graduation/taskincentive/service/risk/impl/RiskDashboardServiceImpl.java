package com.whu.graduation.taskincentive.service.risk.impl;

import com.whu.graduation.taskincentive.dao.mapper.RiskDecisionLogMapper;
import com.whu.graduation.taskincentive.dto.risk.RiskDashboardOverviewResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskDashboardTrendItem;
import com.whu.graduation.taskincentive.service.risk.RiskDashboardService;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 风控看板服务实现
 */
@Service
public class RiskDashboardServiceImpl implements RiskDashboardService {

    private static final String DATE_FMT = "yyyy-MM-dd";

    private final RiskDecisionLogMapper riskDecisionLogMapper;

    public RiskDashboardServiceImpl(RiskDecisionLogMapper riskDecisionLogMapper) {
        this.riskDecisionLogMapper = riskDecisionLogMapper;
    }

    @Override
    public RiskDashboardOverviewResponse overview(Date start, Date end) {
        Date[] range = normalizeRange(start, end);
        Date rangeStart = range[0];
        Date rangeEnd = range[1];

        Map<String, Long> decisionCounts = loadDecisionCounts(rangeStart, rangeEnd);
        long total = decisionCounts.values().stream().mapToLong(v -> v).sum();
        long pass = decisionCounts.getOrDefault("PASS", 0L);
        long reject = decisionCounts.getOrDefault("REJECT", 0L);
        long degrade = decisionCounts.getOrDefault("DEGRADE_PASS", 0L);
        long review = decisionCounts.getOrDefault("REVIEW", 0L);
        long freeze = decisionCounts.getOrDefault("FREEZE", 0L);

        long intercept = reject + review + freeze;
        double interceptRate = total == 0 ? 0.0 : round2(intercept * 100.0 / total);
        double passRate = total == 0 ? 0.0 : round2(pass * 100.0 / total);

        List<Long> latencies = riskDecisionLogMapper.selectLatencies(rangeStart, rangeEnd);
        double avgLatency = average(latencies);
        double p95 = percentile(latencies, 0.95);
        double p99 = percentile(latencies, 0.99);

        return RiskDashboardOverviewResponse.builder()
                .start(rangeStart)
                .end(rangeEnd)
                .total(total)
                .pass(pass)
                .reject(reject)
                .degradePass(degrade)
                .review(review)
                .freeze(freeze)
                .interceptRate(interceptRate)
                .passRate(passRate)
                .avgLatencyMs(avgLatency)
                .p95LatencyMs(p95)
                .p99LatencyMs(p99)
                .build();
    }

    @Override
    public List<RiskDashboardTrendItem> dailyTrend(Date start, Date end) {
        Date[] range = normalizeRange(start, end);
        Date rangeStart = range[0];
        Date rangeEnd = range[1];

        List<Map<String, Object>> rows = riskDecisionLogMapper.countDailyByDecision(rangeStart, rangeEnd);
        Map<String, Map<String, Long>> dateToDecision = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object dateObj = row.get("the_date");
            Object decisionObj = row.get("decision");
            Object cntObj = row.get("cnt");
            if (dateObj == null || decisionObj == null) continue;
            String date = String.valueOf(dateObj);
            String decision = String.valueOf(decisionObj);
            long cnt = toLong(cntObj);
            dateToDecision.computeIfAbsent(date, k -> new HashMap<>()).put(decision, cnt);
        }

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FMT);
        Calendar cal = Calendar.getInstance();
        cal.setTime(rangeStart);
        List<RiskDashboardTrendItem> result = new ArrayList<>();
        while (cal.getTime().before(rangeEnd)) {
            String date = sdf.format(cal.getTime());
            Map<String, Long> map = dateToDecision.getOrDefault(date, Collections.emptyMap());
            long pass = map.getOrDefault("PASS", 0L);
            long reject = map.getOrDefault("REJECT", 0L);
            long degrade = map.getOrDefault("DEGRADE_PASS", 0L);
            long review = map.getOrDefault("REVIEW", 0L);
            long freeze = map.getOrDefault("FREEZE", 0L);
            long total = pass + reject + degrade + review + freeze;
            result.add(RiskDashboardTrendItem.builder()
                    .date(date)
                    .total(total)
                    .pass(pass)
                    .reject(reject)
                    .degradePass(degrade)
                    .review(review)
                    .freeze(freeze)
                    .build());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return result;
    }

    private Date[] normalizeRange(Date start, Date end) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();

        Date rangeEnd = end == null ? new Date() : end;
        Date rangeStart;
        if (start == null) {
            Calendar c = Calendar.getInstance();
            c.setTime(todayStart);
            c.add(Calendar.DAY_OF_MONTH, -6);
            rangeStart = c.getTime();
        } else {
            rangeStart = start;
        }
        return new Date[]{rangeStart, rangeEnd};
    }

    private Map<String, Long> loadDecisionCounts(Date start, Date end) {
        List<Map<String, Object>> rows = riskDecisionLogMapper.countByDecision(start, end);
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object decision = row.get("decision");
            if (decision == null) continue;
            map.put(String.valueOf(decision), toLong(row.get("cnt")));
        }
        return map;
    }

    private long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(String.valueOf(obj));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private double average(List<Long> values) {
        if (values == null || values.isEmpty()) return 0.0;
        long sum = 0L;
        for (Long v : values) {
            if (v != null) sum += v;
        }
        return round2(sum * 1.0 / values.size());
    }

    private double percentile(List<Long> values, double p) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Long> list = new ArrayList<>();
        for (Long v : values) {
            if (v != null) list.add(v);
        }
        if (list.isEmpty()) return 0.0;
        list.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(p * list.size()) - 1;
        if (index < 0) index = 0;
        if (index >= list.size()) index = list.size() - 1;
        return round2(list.get(index));
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
