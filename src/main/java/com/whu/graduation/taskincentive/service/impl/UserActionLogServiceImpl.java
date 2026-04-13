package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserActionLogMapper;
import com.whu.graduation.taskincentive.dto.UserActionAnalyticsDTO;
import com.whu.graduation.taskincentive.service.UserActionLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 用户行为日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserActionLogServiceImpl extends ServiceImpl<UserActionLogMapper, UserActionLog>
        implements UserActionLogService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");

    private final UserActionLogMapper userActionLogMapper;
    private final UserMapper userMapper;

    @Override
    public boolean save(UserActionLog log) {
        log.setId(IdWorker.getId());
        return super.save(log);
    }

    @Override
    public boolean update(UserActionLog log) {
        return super.updateById(log);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public UserActionLog getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<UserActionLog> listAll() {
        return super.list();
    }

    @Override
    public List<UserActionLog> selectByUserId(Long userId) {
        return this.baseMapper.selectByUserId(userId);
    }

    @Override
    public List<UserActionLog> selectByActionType(String actionType) {
        return this.baseMapper.selectByActionType(actionType);
    }

    @Override
    public Page<UserActionLog> selectByUserIdPage(com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserActionLog> page, Long userId) {
        QueryWrapper<UserActionLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time");
        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    public Page<UserActionLog> selectByActionTypePage(com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserActionLog> page, String actionType) {
        QueryWrapper<UserActionLog> wrapper = new QueryWrapper<>();
        wrapper.eq("action_type", actionType).orderByDesc("create_time");
        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    public Long countUserAction(Long userId, String actionType) {
        QueryWrapper<UserActionLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        if (actionType != null && !actionType.isEmpty()) {
            wrapper.eq("action_type", actionType);
        }
        return this.baseMapper.selectCount(wrapper);
    }

    @Override
    public Page<UserActionLog> queryByConditions(Page<UserActionLog> page, Long userId, String actionType, String startTime, String endTime) {
        QueryWrapper<UserActionLog> wrapper = new QueryWrapper<>();
        if (userId != null) wrapper.eq("user_id", userId);
        if (actionType != null && !actionType.isEmpty()) wrapper.eq("action_type", actionType);
        if (startTime != null && !startTime.isEmpty()) wrapper.ge("create_time", startTime);
        if (endTime != null && !endTime.isEmpty()) wrapper.le("create_time", endTime);
        wrapper.orderByDesc("create_time");
        Page<UserActionLog> result = this.baseMapper.selectPage(page, wrapper);

        List<UserActionLog> records = result.getRecords();
        if (records == null || records.isEmpty()) {
            return result;
        }

        // 批量查询用户名，避免 N+1
        Set<Long> userIds = records.stream()
                .map(UserActionLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userNameMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            userNameMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        }

        for (UserActionLog record : records) {
            record.setUserName(userNameMap.get(record.getUserId()));
        }
        return result;
    }

    @Override
    public List<UserActionAnalyticsDTO.TrendPoint> getActionTrend(String startTime, String endTime, String granularity) {
        LocalDateTime start = parseDateTime(startTime, LocalDateTime.now().minusDays(6));
        LocalDateTime end = parseDateTime(endTime, LocalDateTime.now());
        boolean byHour = "HOUR".equalsIgnoreCase(granularity);
        Map<String, Long> counter = byHour ? initHourBuckets(start, end) : initDayBuckets(start, end);
        for (UserActionLog log : loadLogs(start, end)) {
            if (log == null || log.getCreateTime() == null) {
                continue;
            }
            LocalDateTime t = toLocalDateTime(log.getCreateTime());
            String bucket = byHour ? HOUR_FMT.format(t) : DAY_FMT.format(t);
            counter.put(bucket, counter.getOrDefault(bucket, 0L) + 1L);
        }
        return counter.entrySet().stream()
                .map(e -> UserActionAnalyticsDTO.TrendPoint.builder().bucket(e.getKey()).count(e.getValue()).build())
                .collect(Collectors.toList());
    }

    @Override
    public List<UserActionAnalyticsDTO.TypeRatioItem> getActionTypeRatio(String startTime, String endTime) {
        LocalDateTime start = parseDateTime(startTime, LocalDateTime.now().minusDays(6));
        LocalDateTime end = parseDateTime(endTime, LocalDateTime.now());
        Map<String, Long> typeCount = new HashMap<>();
        long total = 0L;
        for (UserActionLog log : loadLogs(start, end)) {
            String type = normalizeActionType(log == null ? null : log.getActionType());
            typeCount.put(type, typeCount.getOrDefault(type, 0L) + 1L);
            total++;
        }
        final long totalCount = total;
        return typeCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> UserActionAnalyticsDTO.TypeRatioItem.builder()
                        .actionType(e.getKey())
                        .count(e.getValue())
                        .ratio(totalCount == 0 ? 0D : round2((e.getValue() * 100D) / totalCount))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<UserActionAnalyticsDTO.UserLayerItem> getUserLayerBehavior(String startTime, String endTime) {
        LocalDateTime start = parseDateTime(startTime, LocalDateTime.now().minusDays(6));
        LocalDateTime end = parseDateTime(endTime, LocalDateTime.now());
        Map<Long, List<UserActionLog>> userLogs = loadLogs(start, end).stream()
                .filter(l -> l != null && l.getUserId() != null)
                .collect(Collectors.groupingBy(UserActionLog::getUserId));

        LayerCounter newbie = new LayerCounter("NEW_USER");
        LayerCounter active = new LayerCounter("ACTIVE_USER");
        LayerCounter silent = new LayerCounter("SILENT_USER");

        for (List<UserActionLog> logs : userLogs.values()) {
            int totalActions = logs == null ? 0 : logs.size();
            LayerCounter target = totalActions <= 3 ? newbie : (totalActions >= 8 ? active : silent);
            for (UserActionLog log : logs) {
                target.add(normalizeActionType(log.getActionType()));
            }
        }
        return List.of(newbie.toItem(), active.toItem(), silent.toItem());
    }

    @Override
    public List<UserActionAnalyticsDTO.HeatmapCell> getWeeklyHeatmap(String startTime, String endTime) {
        LocalDateTime start = parseDateTime(startTime, LocalDateTime.now().minusDays(6));
        LocalDateTime end = parseDateTime(endTime, LocalDateTime.now());
        Map<String, Long> heat = new HashMap<>();
        for (UserActionLog log : loadLogs(start, end)) {
            if (log == null || log.getCreateTime() == null) {
                continue;
            }
            LocalDateTime t = toLocalDateTime(log.getCreateTime());
            int day = t.getDayOfWeek().getValue();
            int hour = t.getHour();
            String key = day + "-" + hour;
            heat.put(key, heat.getOrDefault(key, 0L) + 1L);
        }

        List<UserActionAnalyticsDTO.HeatmapCell> cells = new java.util.ArrayList<>(7 * 24);
        for (int day = 1; day <= 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                String key = day + "-" + hour;
                cells.add(UserActionAnalyticsDTO.HeatmapCell.builder()
                        .dayOfWeek(day)
                        .hourOfDay(hour)
                        .count(heat.getOrDefault(key, 0L))
                        .build());
            }
        }
        return cells;
    }

    @Override
    public UserActionAnalyticsDTO.ConversionDashboard getConversionDashboard(String startTime, String endTime) {
        LocalDateTime start = parseDateTime(startTime, LocalDateTime.now().minusDays(6));
        LocalDateTime end = parseDateTime(endTime, LocalDateTime.now());
        List<UserActionLog> logs = loadLogs(start, end);
        long totalActions = logs.size();
        long activeUsers = logs.stream().map(UserActionLog::getUserId).filter(Objects::nonNull).distinct().count();
        long completedActions = logs.stream().map(UserActionLog::getActionType).map(this::normalizeActionType)
                .filter(t -> "LEARN".equals(t) || "ACCEPT".equals(t)).count();
        long rewardActions = logs.stream().map(UserActionLog::getActionType).map(this::normalizeActionType)
                .filter("REWARD"::equals).count();

        double completionRate = totalActions == 0 ? 0D : round2(completedActions * 100D / totalActions);
        double rewardRate = completedActions == 0 ? 0D : round2(rewardActions * 100D / completedActions);
        double avgActionsPerUser = activeUsers == 0 ? 0D : round2(totalActions * 1D / activeUsers);

        return UserActionAnalyticsDTO.ConversionDashboard.builder()
                .completionRate(completionRate)
                .rewardRate(rewardRate)
                .avgActionsPerUser(avgActionsPerUser)
                .totalActions(totalActions)
                .activeUsers(activeUsers)
                .completedActions(completedActions)
                .rewardActions(rewardActions)
                .build();
    }

    @Override
    public UserActionAnalyticsDTO.TopNResult getTopN(String startTime, String endTime, Integer topN) {
        int n = (topN == null || topN <= 0) ? 10 : Math.min(topN, 100);
        LocalDateTime start = parseDateTime(startTime, LocalDateTime.now().minusDays(6));
        LocalDateTime end = parseDateTime(endTime, LocalDateTime.now());
        List<UserActionLog> logs = loadLogs(start, end);

        Map<Long, Long> userCount = logs.stream()
                .filter(l -> l != null && l.getUserId() != null)
                .collect(Collectors.groupingBy(UserActionLog::getUserId, Collectors.counting()));
        Map<String, Long> typeCount = logs.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(l -> normalizeActionType(l.getActionType()), Collectors.counting()));

        Map<Long, String> userNames = loadUserNames(userCount.keySet());
        List<UserActionAnalyticsDTO.TopNItem> topUsers = userCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(n)
                .map(e -> UserActionAnalyticsDTO.TopNItem.builder()
                        .name(userNames.getOrDefault(e.getKey(), String.valueOf(e.getKey())))
                        .count(e.getValue())
                        .build())
                .collect(Collectors.toList());

        List<UserActionAnalyticsDTO.TopNItem> topTypes = typeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(n)
                .map(e -> UserActionAnalyticsDTO.TopNItem.builder().name(e.getKey()).count(e.getValue()).build())
                .collect(Collectors.toList());

        return UserActionAnalyticsDTO.TopNResult.builder()
                .topUsers(topUsers)
                .topActionTypes(topTypes)
                .build();
    }

    private List<UserActionLog> loadLogs(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<UserActionLog> wrapper = new QueryWrapper<>();
        wrapper.ge("create_time", toDate(start))
                .le("create_time", toDate(end));
        List<UserActionLog> logs = userActionLogMapper.selectList(wrapper);
        return logs == null ? Collections.emptyList() : logs;
    }

    private Map<Long, String> loadUserNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, u -> u.getUsername() == null ? String.valueOf(u.getId()) : u.getUsername(), (a, b) -> a));
    }

    private Map<String, Long> initDayBuckets(LocalDateTime start, LocalDateTime end) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        LocalDateTime cursor = start.toLocalDate().atStartOfDay();
        LocalDateTime stop = end.toLocalDate().atStartOfDay();
        while (!cursor.isAfter(stop)) {
            buckets.put(DAY_FMT.format(cursor), 0L);
            cursor = cursor.plusDays(1);
        }
        return buckets;
    }

    private Map<String, Long> initHourBuckets(LocalDateTime start, LocalDateTime end) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        LocalDateTime cursor = start.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime stop = end.withMinute(0).withSecond(0).withNano(0);
        while (!cursor.isAfter(stop)) {
            buckets.put(HOUR_FMT.format(cursor), 0L);
            cursor = cursor.plusHours(1);
        }
        return buckets;
    }

    private LocalDateTime parseDateTime(String text, LocalDateTime fallback) {
        if (text == null || text.trim().isEmpty()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(text, TIME_FMT);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(text);
            } catch (Exception ex) {
                return fallback;
            }
        }
    }

    private Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDateTime toLocalDateTime(Date time) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(time.getTime()), ZoneId.systemDefault());
    }

    private String normalizeActionType(String actionType) {
        if (actionType == null || actionType.trim().isEmpty()) {
            return "OTHER";
        }
        String upper = actionType.toUpperCase();
        if (upper.contains("SIGN") || upper.contains("CHECK")) {
            return "SIGN";
        }
        if (upper.contains("LEARN") || upper.contains("STUDY")) {
            return "LEARN";
        }
        if (upper.contains("ACCEPT") || upper.contains("TAKE_TASK")) {
            return "ACCEPT";
        }
        if (upper.contains("REWARD") || upper.contains("CLAIM")) {
            return "REWARD";
        }
        return "OTHER";
    }

    private double round2(double v) {
        return Math.round(v * 100D) / 100D;
    }

    private static class LayerCounter {
        private final String layer;
        private long signCount;
        private long learnCount;
        private long acceptCount;
        private long rewardCount;
        private long totalCount;

        private LayerCounter(String layer) {
            this.layer = layer;
        }

        private void add(String actionType) {
            totalCount++;
            if ("SIGN".equals(actionType)) {
                signCount++;
                return;
            }
            if ("LEARN".equals(actionType)) {
                learnCount++;
                return;
            }
            if ("ACCEPT".equals(actionType)) {
                acceptCount++;
                return;
            }
            if ("REWARD".equals(actionType)) {
                rewardCount++;
            }
        }

        private UserActionAnalyticsDTO.UserLayerItem toItem() {
            return UserActionAnalyticsDTO.UserLayerItem.builder()
                    .layer(layer)
                    .signCount(signCount)
                    .learnCount(learnCount)
                    .acceptCount(acceptCount)
                    .rewardCount(rewardCount)
                    .totalCount(totalCount)
                    .build();
        }
    }
}