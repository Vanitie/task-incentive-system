package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.service.MonitorMetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;


@Service
@RequiredArgsConstructor
public class MonitorMetricsServiceImpl implements MonitorMetricsService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MINUTE_KEY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter AXIS_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final LongAdder GLOBAL_REQUEST_COUNT = new LongAdder();
    private static volatile long lastQpsTimestampMs = System.currentTimeMillis();
    private static volatile long lastQpsRequestCount = 0L;
    private static volatile double lastQpsValue = 0.0;

    private static final ConcurrentMap<String, List<Double>> MINUTE_LATENCY_BUCKET = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;

    @Override
    public double getQps() {
        double requestsInLastMinute = getLastMinuteRequestCount();
        return round(twoDecimal(requestsInLastMinute / 60.0));
    }

    @Override
    public long getRequestCount() {
        return Math.round(getTotalHttpRequestCount());
    }

    @Override
    public double getHourSuccessRate() {
        long success = HourWindowCounter.SUCCESS_COUNT.sum();
        long total = HourWindowCounter.TOTAL_COUNT.sum();
        if (total == 0) {
            return 0.0;
        }
        return round(twoDecimal(success * 100.0 / total));
    }

    @Override
    public double getHourFailureRate() {
        long failure = HourWindowCounter.FAILURE_COUNT.sum();
        long total = HourWindowCounter.TOTAL_COUNT.sum();
        if (total == 0) {
            return 0.0;
        }
        return round(twoDecimal(failure * 100.0 / total));
    }

    @Override
    public double getMinuteTp90Ms() {
        return readPercentileMs(0.90);
    }

    @Override
    public double getMinuteTp95Ms() {
        return readPercentileMs(0.95);
    }

    @Override
    public double getMinuteTp99Ms() {
        return readPercentileMs(0.99);
    }

    @Override
    public String getServerName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    @Override
    public double getCpuUsagePercent() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            double value = osBean.getSystemCpuLoad();
            if (value < 0) {
                return 0.0;
            }
            return round(twoDecimal(value * 100.0));
        }
        return 0.0;
    }

    @Override
    public double getMemoryUsagePercent() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            long totalPhysical = osBean.getTotalMemorySize();
            long freePhysical = osBean.getFreeMemorySize();
            if (totalPhysical <= 0) {
                return 0.0;
            }
            long used = totalPhysical - freePhysical;
            return round(twoDecimal(used * 100.0 / totalPhysical));
        }
        return 0.0;
    }

    @Override
    public double getDiskUsagePercent() {
        long total = 0L;
        long usable = 0L;
        try {
            Iterator<FileStore> iterator = FileSystems.getDefault().getFileStores().iterator();
            while (iterator.hasNext()) {
                FileStore fs = iterator.next();
                long t = fs.getTotalSpace();
                long u = fs.getUsableSpace();
                if (t > 0) {
                    total += t;
                    usable += u;
                }
            }
        } catch (Exception ignored) {
            return 0.0;
        }
        if (total <= 0) {
            return 0.0;
        }
        long used = total - usable;
        return round(twoDecimal(used * 100.0 / total));
    }

    @Override
    public String getCurrentTime() {
        return LocalDateTime.now(ZoneId.systemDefault()).format(TIME_FORMATTER);
    }

    @Override
    public Map<String, Object> getTpSeriesLast20Minutes() {
        LocalDateTime nowMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        List<String> xAxis = new ArrayList<>();
        List<Double> tp90List = new ArrayList<>();
        List<Double> tp95List = new ArrayList<>();
        List<Double> tp99List = new ArrayList<>();

        for (int i = 19; i >= 0; i--) {
            LocalDateTime minute = nowMinute.minusMinutes(i);
            String key = minute.format(MINUTE_KEY_FMT);
            xAxis.add(minute.format(AXIS_FMT));

            List<Double> costs = MINUTE_LATENCY_BUCKET.getOrDefault(key, Collections.emptyList());
            tp90List.add(round(twoDecimal(percentile(costs, 0.90))));
            tp95List.add(round(twoDecimal(percentile(costs, 0.95))));
            tp99List.add(round(twoDecimal(percentile(costs, 0.99))));
        }

        List<Map<String, Object>> series = new ArrayList<>();
        series.add(Map.of("name", "tp90", "data", tp90List));
        series.add(Map.of("name", "tp95", "data", tp95List));
        series.add(Map.of("name", "tp99", "data", tp99List));
        return Map.of("xAxis", xAxis, "series", series);
    }

    private double readPercentileMs(double percentile) {
        Timer timer = meterRegistry.find("http.server.requests").timer();
        if (timer == null) {
            return 0.0;
        }
        var snapshot = timer.takeSnapshot();
        var values = snapshot.percentileValues();
        for (var v : values) {
            if (Math.abs(v.percentile() - percentile) < 0.0001) {
                return round(twoDecimal(v.value(java.util.concurrent.TimeUnit.MILLISECONDS)));
            }
        }
        return 0.0;
    }

    private double twoDecimal(double val) {
        return Double.parseDouble(String.format(Locale.ROOT, "%.2f", val));
    }

    private double round(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return 0.0;
        }
        return val;
    }

    private static void recordRequestLatency(long costMs) {
        LocalDateTime minute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        String key = minute.format(MINUTE_KEY_FMT);
        MINUTE_LATENCY_BUCKET.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>())).add((double) costMs);

        LocalDateTime expireBefore = minute.minusMinutes(60);
        for (String k : MINUTE_LATENCY_BUCKET.keySet()) {
            try {
                LocalDateTime t = LocalDateTime.parse(k, MINUTE_KEY_FMT);
                if (t.isBefore(expireBefore)) {
                    MINUTE_LATENCY_BUCKET.remove(k);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static double percentile(List<Double> values, double p) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> copy = new ArrayList<>(values);
        copy.sort(Comparator.naturalOrder());
        int n = copy.size();
        int idx = (int) Math.ceil(p * n) - 1;
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        return copy.get(idx);
    }

    /**
     * 汇总所有 http.server.requests 的 timer 分片，得到全量请求数。
     */
    private double getTotalHttpRequestCount() {
        return meterRegistry.find("http.server.requests")
                .timers()
                .stream()
                .mapToDouble(Timer::count)
                .sum();
    }

    /**
     * 最近60秒请求数（平滑估算）：
     * 当前分钟全量 + 上一分钟按剩余秒数加权，避免跨分钟瞬时归零。
     */
    private double getLastMinuteRequestCount() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentMinute = now.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime prevMinute = currentMinute.minusMinutes(1);

        String currentKey = currentMinute.format(MINUTE_KEY_FMT);
        String prevKey = prevMinute.format(MINUTE_KEY_FMT);

        List<Double> currentCosts = MINUTE_LATENCY_BUCKET.getOrDefault(currentKey, Collections.emptyList());
        List<Double> prevCosts = MINUTE_LATENCY_BUCKET.getOrDefault(prevKey, Collections.emptyList());

        int sec = now.getSecond(); // 0~59
        // 最近60秒窗口里，上一分钟贡献比例 = (60-sec)/60
        double prevWeight = (60.0 - sec) / 60.0;

        return currentCosts.size() + prevCosts.size() * prevWeight;
    }


    @Service
    public static class HourWindowCounter extends OncePerRequestFilter {

        static final LongAdder TOTAL_COUNT = new LongAdder();
        static final LongAdder SUCCESS_COUNT = new LongAdder();
        static final LongAdder FAILURE_COUNT = new LongAdder();
        private static volatile long windowStart = System.currentTimeMillis();

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            rollIfNeeded();
            TOTAL_COUNT.increment();

            long startNs = System.nanoTime();
            try {
                filterChain.doFilter(request, response);
            } finally {
                long costMs = (System.nanoTime() - startNs) / 1_000_000;
                recordRequestLatency(costMs);

                int status = response.getStatus();
                if (status >= 200 && status < 400) {
                    SUCCESS_COUNT.increment();
                } else {
                    FAILURE_COUNT.increment();
                }
            }
        }

        private static synchronized void rollIfNeeded() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= 60 * 60 * 1000L) {
                TOTAL_COUNT.reset();
                SUCCESS_COUNT.reset();
                FAILURE_COUNT.reset();
                windowStart = now;
            }
        }
    }
}
