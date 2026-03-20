package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.dto.risk.RiskDecisionRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 风控指标内存统计（MVP，非分布式）
 */
@Component
public class RiskMetricStore {

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Map<String, LongAdder> minuteCounts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> hourCounts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> dayAmount = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> ipMinuteCounts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> deviceMinuteCounts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userDayDevices = new ConcurrentHashMap<>();

    public void record(RiskDecisionRequest req) {
        LocalDateTime time = req.getEventTime() == null ? LocalDateTime.now() : req.getEventTime();
        String minuteKey = MINUTE_FMT.format(time);
        String hourKey = HOUR_FMT.format(time);
        String dayKey = DAY_FMT.format(time);

        String userTaskKey = buildUserTaskKey(req.getUserId(), req.getTaskId());
        String minuteCountKey = userTaskKey + ":" + minuteKey;
        String hourCountKey = userTaskKey + ":" + hourKey;
        String dayAmountKey = userTaskKey + ":" + dayKey;

        minuteCounts.computeIfAbsent(minuteCountKey, k -> new LongAdder()).increment();
        hourCounts.computeIfAbsent(hourCountKey, k -> new LongAdder()).increment();

        int amount = req.getAmount() == null ? 1 : req.getAmount();
        dayAmount.computeIfAbsent(dayAmountKey, k -> new LongAdder()).add(amount);

        if (req.getIp() != null && !req.getIp().isEmpty()) {
            String ipKey = req.getIp() + ":" + minuteKey;
            ipMinuteCounts.computeIfAbsent(ipKey, k -> new LongAdder()).increment();
        }
        if (req.getDeviceId() != null && !req.getDeviceId().isEmpty()) {
            String deviceKey = req.getDeviceId() + ":" + minuteKey;
            deviceMinuteCounts.computeIfAbsent(deviceKey, k -> new LongAdder()).increment();
            String userDayKey = (req.getUserId() == null ? "" : req.getUserId()) + ":" + dayKey;
            userDayDevices.computeIfAbsent(userDayKey, k -> ConcurrentHashMap.newKeySet()).add(req.getDeviceId());
        }
    }

    public long getCount1m(Long userId, Long taskId, LocalDateTime time) {
        String key = buildUserTaskKey(userId, taskId) + ":" + MINUTE_FMT.format(time);
        return getAdderValue(minuteCounts.get(key));
    }

    public long getCount1h(Long userId, Long taskId, LocalDateTime time) {
        String key = buildUserTaskKey(userId, taskId) + ":" + HOUR_FMT.format(time);
        return getAdderValue(hourCounts.get(key));
    }

    public long getAmount1d(Long userId, Long taskId, LocalDateTime time) {
        String key = buildUserTaskKey(userId, taskId) + ":" + DAY_FMT.format(time);
        return getAdderValue(dayAmount.get(key));
    }

    public long getDistinctDevice1d(Long userId, LocalDateTime time) {
        String key = (userId == null ? "" : userId) + ":" + DAY_FMT.format(time);
        Set<String> set = userDayDevices.get(key);
        return set == null ? 0L : set.size();
    }

    public long getIpCount1m(String ip, LocalDateTime time) {
        if (ip == null || ip.isEmpty()) return 0L;
        String key = ip + ":" + MINUTE_FMT.format(time);
        return getAdderValue(ipMinuteCounts.get(key));
    }

    public long getDeviceCount1m(String deviceId, LocalDateTime time) {
        if (deviceId == null || deviceId.isEmpty()) return 0L;
        String key = deviceId + ":" + MINUTE_FMT.format(time);
        return getAdderValue(deviceMinuteCounts.get(key));
    }

    private String buildUserTaskKey(Long userId, Long taskId) {
        return (userId == null ? "" : userId) + ":" + (taskId == null ? "" : taskId);
    }

    private long getAdderValue(LongAdder adder) {
        return adder == null ? 0L : adder.longValue();
    }
}
