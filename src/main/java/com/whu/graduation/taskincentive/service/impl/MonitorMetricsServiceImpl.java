package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.service.MonitorMetricsService;
import com.whu.graduation.taskincentive.dto.ApiResponse;
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
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Service
@RequiredArgsConstructor
public class MonitorMetricsServiceImpl implements MonitorMetricsService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MeterRegistry meterRegistry;

    @Override
    public double getQps() {
        double oneMinuteRate = meterRegistry.get("http.server.requests").timer().count();
        return round(twoDecimal(oneMinuteRate / 60.0));
    }

    @Override
    public long getRequestCount() {
        return meterRegistry.get("http.server.requests").timer().count();
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
        // 系统物理内存统计
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
        // 横坐标：最近20分钟时间点
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.util.List<String> xAxis = new java.util.ArrayList<>();
        java.util.List<Double> tp90List = new java.util.ArrayList<>();
        java.util.List<Double> tp95List = new java.util.ArrayList<>();
        java.util.List<Double> tp99List = new java.util.ArrayList<>();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        for (int i = 19; i >= 0; i--) {
            java.time.LocalDateTime t = now.minusMinutes(i);
            xAxis.add(fmt.format(t));
            // 采集每分钟的tp90/95/99
            // 这里用全局Timer快照近似
            io.micrometer.core.instrument.Timer timer = meterRegistry.find("http.server.requests").timer();
            double tp90 = 0.0, tp95 = 0.0, tp99 = 0.0;
            if (timer != null) {
                var snapshot = timer.takeSnapshot();
                var values = snapshot.percentileValues();
                for (var v : values) {
                    if (Math.abs(v.percentile() - 0.90) < 0.0001) tp90 = round(twoDecimal(v.value(java.util.concurrent.TimeUnit.MILLISECONDS)));
                    if (Math.abs(v.percentile() - 0.95) < 0.0001) tp95 = round(twoDecimal(v.value(java.util.concurrent.TimeUnit.MILLISECONDS)));
                    if (Math.abs(v.percentile() - 0.99) < 0.0001) tp99 = round(twoDecimal(v.value(java.util.concurrent.TimeUnit.MILLISECONDS)));
                }
            }
            tp90List.add(tp90);
            tp95List.add(tp95);
            tp99List.add(tp99);
        }
        java.util.List<java.util.Map<String, Object>> series = new java.util.ArrayList<>();
        series.add(java.util.Map.of("name", "tp90", "data", tp90List));
        series.add(java.util.Map.of("name", "tp95", "data", tp95List));
        series.add(java.util.Map.of("name", "tp99", "data", tp99List));
        return java.util.Map.of("xAxis", xAxis, "series", series);
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
            try {
                filterChain.doFilter(request, response);
            } finally {
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
