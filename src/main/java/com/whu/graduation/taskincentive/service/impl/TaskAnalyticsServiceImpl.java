package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.TaskConfigHistory;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dao.mapper.TaskConfigMapper;
import com.whu.graduation.taskincentive.dao.mapper.TaskConfigHistoryMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.dto.TaskAnalyticsDTO;
import com.whu.graduation.taskincentive.service.TaskAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskAnalyticsServiceImpl implements TaskAnalyticsService {

    private final JdbcTemplate jdbcTemplate;
    private final TaskConfigHistoryMapper taskConfigHistoryMapper;
    private final UserTaskInstanceMapper userTaskInstanceMapper;
    private final UserRewardRecordMapper userRewardRecordMapper;
    private final TaskConfigMapper taskConfigMapper;
    private final UserMapper userMapper;

    @Override
    public TaskAnalyticsDTO.MetricOverview taskConfigOverview(Long taskId, int days) {
        int safeDays = days == 30 ? 30 : 7;
        Instant end = Instant.now();
        Instant start = end.minus(safeDays, ChronoUnit.DAYS);
        TaskAnalyticsDTO.MetricOverview rangeOverview = metricsInRange(taskId, start, end, safeDays);
        if (safeLong(rangeOverview.getAcceptedCount()) > 0 || safeLong(rangeOverview.getCompletedCount()) > 0 || safeLong(rangeOverview.getRewardedCount()) > 0) {
            return rangeOverview;
        }
        // 若窗口内无数据，回退到任务全量历史，避免前端全部为 0。
        return metricsInRange(taskId, null, null, safeDays);
    }

    @Override
    public TaskAnalyticsDTO.AudienceHit taskConfigAudienceHit(Long taskId, int days) {
        long targetUsers = userMapper.selectCount(Wrappers.lambdaQuery(User.class));
        // 触达口径改为「已接取该任务的用户数」（全量历史去重用户）。
        List<Map<String, Object>> reachedRows = userTaskInstanceMapper.selectMaps(new QueryWrapper<UserTaskInstance>()
                .select("user_id")
                .eq("task_id", taskId)
                .gt("status", 0)
                .groupBy("user_id"));
        List<Long> reachedUserIds = reachedRows.stream()
                .map(row -> parseLong(row.get("user_id")))
                .filter(id -> id > 0)
                .collect(Collectors.toList());
        long reachedUsers = reachedUserIds.size();

        List<TaskAnalyticsDTO.AudienceLayerItem> layers = new ArrayList<>();
        if (!reachedUserIds.isEmpty()) {
            Date newThreshold = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));
            List<User> users = userMapper.selectBatchIds(reachedUserIds);
            Map<Long, Date> lastActiveByUser = userTaskInstanceMapper.selectMaps(new QueryWrapper<UserTaskInstance>()
                            .select("user_id", "MAX(update_time) AS last_active")
                            .in("user_id", reachedUserIds)
                            .groupBy("user_id"))
                    .stream()
                    .collect(Collectors.toMap(
                            row -> parseLong(row.get("user_id")),
                            row -> toDate(row.get("last_active")),
                            (a, b) -> a));

            long newCount = 0L;
            long activeCount = 0L;
            long silentCount = 0L;
            for (User user : users) {
                Date createTime = user.getCreateTime();
                Date lastActive = lastActiveByUser.get(user.getId());
                if (createTime != null && !createTime.before(newThreshold)) {
                    newCount++;
                } else if (lastActive != null && !lastActive.before(newThreshold)) {
                    activeCount++;
                } else {
                    silentCount++;
                }
            }
            layers.add(TaskAnalyticsDTO.AudienceLayerItem.builder().layer("NEW").count(newCount).rate(ratio(newCount, reachedUsers)).build());
            layers.add(TaskAnalyticsDTO.AudienceLayerItem.builder().layer("ACTIVE").count(activeCount).rate(ratio(activeCount, reachedUsers)).build());
            layers.add(TaskAnalyticsDTO.AudienceLayerItem.builder().layer("SILENT").count(silentCount).rate(ratio(silentCount, reachedUsers)).build());
        }

        return TaskAnalyticsDTO.AudienceHit.builder()
                .targetUsers(targetUsers)
                .reachedUsers(reachedUsers)
                .reachRate(ratio(reachedUsers, targetUsers))
                .layerBreakdown(layers)
                .build();
    }

    @Override
    public List<TaskAnalyticsDTO.HeatmapCell> taskConfigTimeHeatmap(Long taskId, int days) {
        int safeDays = days <= 0 ? 7 : days;
        Instant end = Instant.now();
        Instant start = end.minus(safeDays, ChronoUnit.DAYS);
        List<Map<String, Object>> rows = queryHeatmapRows(taskId, Date.from(start), Date.from(end));
        if (rows.isEmpty()) {
            rows = queryHeatmapRows(taskId, null, null);
        }
        return rows.stream()
                .map(row -> TaskAnalyticsDTO.HeatmapCell.builder()
                        .dayOfWeek(parseInt(row.get("dayOfWeek")))
                        .hourOfDay(parseInt(row.get("hourOfDay")))
                        .count(parseLong(row.get("cnt")))
                        .build())
                .sorted(Comparator.comparing(TaskAnalyticsDTO.HeatmapCell::getDayOfWeek, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(TaskAnalyticsDTO.HeatmapCell::getHourOfDay, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskAnalyticsDTO.RewardElasticityItem> taskConfigRewardElasticity(Long taskId, int days) {
        int safeDays = days <= 0 ? 30 : days;
        Instant end = Instant.now();
        Instant start = end.minus(safeDays, ChronoUnit.DAYS);

        TaskConfig target = taskConfigMapper.selectById(taskId);
        if (target == null || target.getTaskType() == null || target.getTaskType().trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<TaskConfig> sameTypeTasks = taskConfigMapper.selectList(Wrappers.lambdaQuery(TaskConfig.class)
                .eq(TaskConfig::getTaskType, target.getTaskType())
                .select(TaskConfig::getId, TaskConfig::getRewardValue));
        if (sameTypeTasks.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> taskIds = sameTypeTasks.stream().map(TaskConfig::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (taskIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, String> bucketByTaskId = new HashMap<>();
        for (TaskConfig config : sameTypeTasks) {
            bucketByTaskId.put(config.getId(), rewardBucket(config.getRewardValue()));
        }

        List<Map<String, Object>> acceptedRows = queryElasticAccepted(taskIds, Date.from(start), Date.from(end));
        List<Map<String, Object>> rewardedRows = queryElasticRewarded(taskIds, Date.from(start), Date.from(end));
        if (acceptedRows.isEmpty() && rewardedRows.isEmpty()) {
            acceptedRows = queryElasticAccepted(taskIds, null, null);
            rewardedRows = queryElasticRewarded(taskIds, null, null);
        }

        Map<String, long[]> bucketStats = new LinkedHashMap<>();
        bucketStats.put("0-10", new long[3]);
        bucketStats.put("11-50", new long[3]);
        bucketStats.put("51-100", new long[3]);
        bucketStats.put("100+", new long[3]);

        for (Map<String, Object> row : acceptedRows) {
            Long tid = parseLong(row.get("task_id"));
            String bucket = bucketByTaskId.get(tid);
            if (bucket == null) {
                continue;
            }
            long[] stats = bucketStats.get(bucket);
            stats[0] += parseLong(row.get("acceptedCount"));
            stats[1] += parseLong(row.get("completedCount"));
        }
        for (Map<String, Object> row : rewardedRows) {
            Long tid = parseLong(row.get("task_id"));
            String bucket = bucketByTaskId.get(tid);
            if (bucket == null) {
                continue;
            }
            long[] stats = bucketStats.get(bucket);
            stats[2] += parseLong(row.get("rewardedCount"));
        }

        List<TaskAnalyticsDTO.RewardElasticityItem> out = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : bucketStats.entrySet()) {
            long accepted = entry.getValue()[0];
            long completed = entry.getValue()[1];
            long rewarded = entry.getValue()[2];
            if (accepted == 0 && completed == 0 && rewarded == 0) {
                continue;
            }
            out.add(TaskAnalyticsDTO.RewardElasticityItem.builder()
                    .rewardBucket(entry.getKey())
                    .acceptedCount(accepted)
                    .completedCount(completed)
                    .rewardedCount(rewarded)
                    .completionRate(ratio(completed, accepted))
                    .rewardRate(ratio(rewarded, completed))
                    .build());
        }
        return out;
    }

    @Override
    public TaskAnalyticsDTO.VersionCompare taskConfigVersionCompare(Long taskId,
                                                                    String baselineVersion,
                                                                    String compareVersion,
                                                                    String compareStart,
                                                                    String compareEnd) {
        TaskConfigHistory baselineHistory = resolveHistory(taskId, baselineVersion, false);
        TaskConfigHistory compareHistory = resolveHistory(taskId, compareVersion, true);

        if (baselineHistory != null && compareHistory != null) {
            Instant baselineStart = safeInstant(baselineHistory.getCreateTime()).minus(7, ChronoUnit.DAYS);
            Instant baselineEnd = safeInstant(baselineHistory.getCreateTime());
            Instant compareStartInstant = safeInstant(compareHistory.getCreateTime()).minus(7, ChronoUnit.DAYS);
            Instant compareEndInstant = safeInstant(compareHistory.getCreateTime());
            TaskAnalyticsDTO.MetricOverview baseline = metricsInRange(taskId, baselineStart, baselineEnd, 7);
            TaskAnalyticsDTO.MetricOverview compare = metricsInRange(taskId, compareStartInstant, compareEndInstant, 7);
            return buildVersionCompare(baselineVersionOf(baselineHistory, baselineVersion),
                    baselineStart,
                    baselineEnd,
                    compareVersionOf(compareHistory, compareVersion),
                    compareStartInstant,
                    compareEndInstant,
                    baseline,
                    compare,
                    "基于 task_config_history 版本快照时间点对比");
        }

        Instant now = Instant.now();
        Instant compareEndInstant = parseTime(compareEnd, now);
        Instant compareStartInstant = parseTime(compareStart, compareEndInstant.minus(7, ChronoUnit.DAYS));
        if (!compareStartInstant.isBefore(compareEndInstant)) {
            compareStartInstant = compareEndInstant.minus(7, ChronoUnit.DAYS);
        }
        long windowDays = Math.max(1, ChronoUnit.DAYS.between(compareStartInstant, compareEndInstant));
        Instant baselineEndInstant = compareStartInstant;
        Instant baselineStartInstant = baselineEndInstant.minus(windowDays, ChronoUnit.DAYS);

        TaskAnalyticsDTO.MetricOverview baseline = metricsInRange(taskId, baselineStartInstant, baselineEndInstant, (int) windowDays);
        TaskAnalyticsDTO.MetricOverview compare = metricsInRange(taskId, compareStartInstant, compareEndInstant, (int) windowDays);
        return buildVersionCompare(baselineVersion, baselineStartInstant, baselineEndInstant, compareVersion, compareStartInstant, compareEndInstant,
                baseline, compare, "未匹配到历史版本，按时间窗口前后对比");
    }

    @Override
    public TaskAnalyticsDTO.HealthMetrics taskConfigHealth(Long taskId, int days) {
        int safeDays = days <= 0 ? 7 : days;
        Instant end = Instant.now();
        Instant start = end.minus(safeDays, ChronoUnit.DAYS);

        long totalReward = countLong("SELECT COUNT(1) FROM user_reward_record WHERE task_id=? AND create_time>=? AND create_time<?",
                taskId, ts(start), ts(end));
        long failedReward = countLong("SELECT COUNT(1) FROM user_reward_record WHERE task_id=? AND grant_status=3 AND create_time>=? AND create_time<?",
                taskId, ts(start), ts(end));
        long timeoutReward = countLong("SELECT COUNT(1) FROM user_reward_record WHERE task_id=? AND error_msg IS NOT NULL AND LOWER(error_msg) LIKE '%timeout%' AND create_time>=? AND create_time<?",
                taskId, ts(start), ts(end));
        long compensationCount = countLong("SELECT COUNT(1) FROM user_reward_record WHERE task_id=? AND error_msg IS NOT NULL AND LOWER(error_msg) LIKE '%replay%' AND create_time>=? AND create_time<?",
                taskId, ts(start), ts(end));
        long idempotentConflictCount = countLong("SELECT COUNT(1) FROM (SELECT message_id FROM user_reward_record WHERE task_id=? AND message_id IS NOT NULL AND message_id<>'' GROUP BY message_id HAVING COUNT(1)>1) t",
                taskId);

        return TaskAnalyticsDTO.HealthMetrics.builder()
                .failedRate(ratio(failedReward, totalReward))
                .timeoutRate(ratio(timeoutReward, totalReward))
                .compensationCount(compensationCount)
                .idempotentConflictCount(idempotentConflictCount)
                .totalRewardRequests(totalReward)
                .build();
    }

            @Override
            public TaskAnalyticsDTO.TaskConfigInsight taskConfigInsight(Long taskId, int days) {
            int safeDays = days <= 0 ? 7 : days;
            TaskAnalyticsDTO.MetricOverview overview = taskConfigOverview(taskId, safeDays);
            TaskAnalyticsDTO.HealthMetrics health = taskConfigHealth(taskId, safeDays);

            long accepted = safeLong(overview.getAcceptedCount());
            long completed = safeLong(overview.getCompletedCount());
            long rewarded = safeLong(overview.getRewardedCount());
            long conversionLoss = Math.max(accepted - completed, 0);
            long rewardLoss = Math.max(completed - rewarded, 0);

            double completionRate = safeDouble(overview.getCompletionRate());
            double rewardRate = safeDouble(overview.getRewardRate());
            double failedRate = safeDouble(health.getFailedRate());
            double timeoutRate = safeDouble(health.getTimeoutRate());
            // 综合评分口径：转化效率 70% + 稳定性 30%。
            double qualityScore = round2(
                completionRate * 0.4D +
                    rewardRate * 0.3D +
                    Math.max(0D, 100D - failedRate) * 0.2D +
                    Math.max(0D, 100D - timeoutRate) * 0.1D
            );

            return TaskAnalyticsDTO.TaskConfigInsight.builder()
                .days(safeDays)
                .acceptedCount(accepted)
                .completedCount(completed)
                .rewardedCount(rewarded)
                .completionRate(completionRate)
                .rewardRate(rewardRate)
                .avgCompletionMinutes(safeDouble(overview.getAvgCompletionMinutes()))
                .failedRate(failedRate)
                .timeoutRate(timeoutRate)
                .compensationCount(safeLong(health.getCompensationCount()))
                .idempotentConflictCount(safeLong(health.getIdempotentConflictCount()))
                .totalRewardRequests(safeLong(health.getTotalRewardRequests()))
                .conversionLossCount(conversionLoss)
                .rewardLossCount(rewardLoss)
                .qualityScore(qualityScore)
                .build();
            }

    @Override
    public TaskAnalyticsDTO.MetricOverview userTaskOverview(String startTime, String endTime, String taskType, String campaignId, String userLayer) {
        Instant[] range = resolveRange(startTime, endTime, 7);
        int days = normalizeDaysBetween(range[0], range[1], 7);
        long accepted = countLong(baseCountSql("COUNT(1)", userLayer), baseParams(taskType, campaignId, range[0], range[1]));
        long completed = countLong(completedOverviewSql("COUNT(1)", userLayer, false), completedOverviewParams(taskType, campaignId, range[0], range[1], false));
        long rewarded = countLong(completedOverviewSql("COUNT(1)", userLayer, true), completedOverviewParams(taskType, campaignId, range[0], range[1], true));
        double avgMinutes = queryDoubleOrZero(completedOverviewSql("AVG(TIMESTAMPDIFF(MINUTE, uti.create_time, uti.update_time))", userLayer, false),
                completedOverviewParams(taskType, campaignId, range[0], range[1], false));

        return TaskAnalyticsDTO.MetricOverview.builder()
                .days(days)
                .acceptedCount(accepted)
                .completedCount(completed)
                .rewardedCount(rewarded)
                .completionRate(ratio(completed, accepted))
                .rewardRate(ratio(rewarded, completed))
                .avgCompletionMinutes(round2(avgMinutes))
                .build();
    }

    @Override
    public TaskAnalyticsDTO.FunnelMetrics userTaskFunnel(String startTime, String endTime, String taskType, String campaignId, String userLayer) {
        TaskAnalyticsDTO.MetricOverview ov = userTaskOverview(startTime, endTime, taskType, campaignId, userLayer);
        long accepted = safeLong(ov.getAcceptedCount());
        long completed = safeLong(ov.getCompletedCount());
        long rewarded = safeLong(ov.getRewardedCount());
        long exposed = accepted;

        return TaskAnalyticsDTO.FunnelMetrics.builder()
                .exposed(exposed)
                .accepted(accepted)
                .completed(completed)
                .rewarded(rewarded)
                .acceptRate(ratio(accepted, exposed))
                .completeRate(ratio(completed, accepted))
                .rewardRate(ratio(rewarded, completed))
                .note("当前缺少任务曝光事实表，exposed口径暂按accepted近似")
                .build();
    }

    @Override
    public List<TaskAnalyticsDTO.TrendPoint> userTaskTrend(String startTime, String endTime, String granularity, String taskType, String campaignId) {
        Instant[] range = resolveRange(startTime, endTime, 7);
        boolean byHour = "HOUR".equalsIgnoreCase(granularity);
        String bucketExprUti = byHour ? "DATE_FORMAT(uti.create_time,'%Y-%m-%d %H:00:00')" : "DATE(uti.create_time)";
        String bucketExprRr = byHour ? "DATE_FORMAT(rr.create_time,'%Y-%m-%d %H:00:00')" : "DATE(rr.create_time)";

        String utiSql = "SELECT " + bucketExprUti + " AS bucket, COUNT(1) AS acceptedCount, " +
                "SUM(CASE WHEN uti.status=3 THEN 1 ELSE 0 END) AS completedCount " +
                "FROM user_task_instance uti JOIN task_config tc ON tc.id=uti.task_id " +
                "WHERE uti.create_time>=? AND uti.create_time<? " +
                (taskType == null || taskType.isEmpty() ? "" : " AND tc.task_type=? ") +
                (campaignId == null || campaignId.isEmpty() ? "" : " AND (tc.trigger_event=? OR CAST(tc.id AS CHAR)=?) ") +
                "GROUP BY bucket ORDER BY bucket";

        List<Object> utiParams = new ArrayList<>();
        utiParams.add(ts(range[0]));
        utiParams.add(ts(range[1]));
        if (taskType != null && !taskType.isEmpty()) utiParams.add(taskType);
        if (campaignId != null && !campaignId.isEmpty()) {
            utiParams.add(campaignId);
            utiParams.add(campaignId);
        }

        Map<String, TaskAnalyticsDTO.TrendPoint> map = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(utiSql, utiParams.toArray())) {
            String bucket = String.valueOf(row.get("bucket"));
            map.put(bucket, TaskAnalyticsDTO.TrendPoint.builder()
                    .bucket(bucket)
                    .acceptedCount(parseLong(row.get("acceptedCount")))
                    .completedCount(parseLong(row.get("completedCount")))
                    .rewardedCount(0L)
                    .failedCount(0L)
                    .build());
        }

        String rrSql = "SELECT " + bucketExprRr + " AS bucket, " +
                "SUM(CASE WHEN rr.grant_status=2 THEN 1 ELSE 0 END) AS rewardedCount, " +
                "SUM(CASE WHEN rr.grant_status=3 THEN 1 ELSE 0 END) AS failedCount " +
                "FROM user_reward_record rr JOIN task_config tc ON tc.id=rr.task_id " +
                "WHERE rr.create_time>=? AND rr.create_time<? " +
                (taskType == null || taskType.isEmpty() ? "" : " AND tc.task_type=? ") +
                (campaignId == null || campaignId.isEmpty() ? "" : " AND (tc.trigger_event=? OR CAST(tc.id AS CHAR)=?) ") +
                "GROUP BY bucket ORDER BY bucket";

        List<Object> rrParams = new ArrayList<>();
        rrParams.add(ts(range[0]));
        rrParams.add(ts(range[1]));
        if (taskType != null && !taskType.isEmpty()) rrParams.add(taskType);
        if (campaignId != null && !campaignId.isEmpty()) {
            rrParams.add(campaignId);
            rrParams.add(campaignId);
        }

        for (Map<String, Object> row : jdbcTemplate.queryForList(rrSql, rrParams.toArray())) {
            String bucket = String.valueOf(row.get("bucket"));
            TaskAnalyticsDTO.TrendPoint p = map.get(bucket);
            if (p == null) {
                p = TaskAnalyticsDTO.TrendPoint.builder().bucket(bucket).acceptedCount(0L).completedCount(0L).build();
            }
            p.setRewardedCount(parseLong(row.get("rewardedCount")));
            p.setFailedCount(parseLong(row.get("failedCount")));
            map.put(bucket, p);
        }

        return new ArrayList<>(map.values());
    }

    @Override
    public List<TaskAnalyticsDTO.DimensionPerformance> userTaskTypePerformance(String startTime, String endTime, Integer topN, String sortBy) {
        Instant[] range = resolveRange(startTime, endTime, 7);
        String order = "acceptedCount".equalsIgnoreCase(sortBy) ? "acceptedCount" :
                ("completedCount".equalsIgnoreCase(sortBy) ? "completedCount" : "rewardedCount");
        int limit = topN == null || topN <= 0 ? 20 : Math.min(topN, 100);

        String sql = "SELECT a.dimName, a.acceptedCount, a.completedCount, COALESCE(b.rewardedCount,0) AS rewardedCount, a.avgCompletionMinutes " +
            "FROM (" +
            "  SELECT COALESCE(tc.task_type,'UNKNOWN') AS dimName, COUNT(uti.id) AS acceptedCount, " +
            "         SUM(CASE WHEN uti.status=3 THEN 1 ELSE 0 END) AS completedCount, " +
            "         AVG(CASE WHEN uti.status=3 THEN TIMESTAMPDIFF(MINUTE, uti.create_time, uti.update_time) END) AS avgCompletionMinutes " +
            "  FROM user_task_instance uti " +
            "  LEFT JOIN task_config tc ON tc.id=uti.task_id " +
            "  WHERE uti.create_time>=? AND uti.create_time<? " +
            "  GROUP BY COALESCE(tc.task_type,'UNKNOWN')" +
            ") a " +
            "LEFT JOIN (" +
            "  SELECT COALESCE(tc.task_type,'UNKNOWN') AS dimName, COUNT(rr.id) AS rewardedCount " +
            "  FROM user_reward_record rr " +
            "  LEFT JOIN task_config tc ON tc.id=rr.task_id " +
            "  WHERE rr.grant_status=2 AND rr.create_time>=? AND rr.create_time<? " +
            "  GROUP BY COALESCE(tc.task_type,'UNKNOWN')" +
            ") b ON b.dimName=a.dimName " +
            "ORDER BY " + order + " DESC LIMIT " + limit;

        List<TaskAnalyticsDTO.DimensionPerformance> out = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, ts(range[0]), ts(range[1]), ts(range[0]), ts(range[1]))) {
            out.add(toDimPerformance(row));
        }
        return out;
    }

    @Override
    public List<TaskAnalyticsDTO.DimensionPerformance> userTaskLayerPerformance(String startTime, String endTime, Integer topN, String sortBy) {
        Instant[] range = resolveRange(startTime, endTime, 7);
        String order = "acceptedCount".equalsIgnoreCase(sortBy) ? "acceptedCount" :
                ("completedCount".equalsIgnoreCase(sortBy) ? "completedCount" : "rewardedCount");

        String sql = "SELECT " +
                "CASE WHEN u.create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 'NEW' " +
                "WHEN COALESCE(ua.last_active, '1970-01-01') >= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 'ACTIVE' " +
                "ELSE 'SILENT' END AS dimName, " +
                "COUNT(uti.id) AS acceptedCount, " +
                "SUM(CASE WHEN uti.status=3 THEN 1 ELSE 0 END) AS completedCount, " +
                "SUM(CASE WHEN urr.grant_status=2 THEN 1 ELSE 0 END) AS rewardedCount, " +
                "AVG(CASE WHEN uti.status=3 THEN TIMESTAMPDIFF(MINUTE, uti.create_time, uti.update_time) END) AS avgCompletionMinutes " +
                "FROM user_task_instance uti " +
                "JOIN user u ON u.id=uti.user_id " +
                "LEFT JOIN (SELECT user_id, MAX(update_time) AS last_active FROM user_task_instance GROUP BY user_id) ua ON ua.user_id=u.id " +
                "LEFT JOIN user_reward_record urr ON urr.task_id=uti.task_id AND urr.user_id=uti.user_id AND urr.grant_status=2 " +
                "WHERE uti.create_time>=? AND uti.create_time<? " +
                "GROUP BY dimName ORDER BY " + order + " DESC";

        List<TaskAnalyticsDTO.DimensionPerformance> out = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, ts(range[0]), ts(range[1]))) {
            out.add(toDimPerformance(row));
        }
        int safe = topN == null || topN <= 0 ? out.size() : Math.min(topN, out.size());
        return out.subList(0, safe);
    }

    @Override
    public List<TaskAnalyticsDTO.UserTopNItem> userTaskTopN(String startTime, String endTime, Integer n, String taskType, String orderBy) {
        Instant[] range = resolveRange(startTime, endTime, 7);
        int limit = n == null || n <= 0 ? 10 : Math.min(n, 100);
        String sortExpr = "task".equalsIgnoreCase(orderBy) ? "topTaskCount" : "acceptedCount";

        String sql = "SELECT u.id AS userId, u.username AS userName, COUNT(uti.id) AS acceptedCount, COUNT(DISTINCT uti.task_id) AS topTaskCount " +
                "FROM user_task_instance uti " +
                "JOIN user u ON u.id=uti.user_id " +
                "JOIN task_config tc ON tc.id=uti.task_id " +
                "WHERE uti.create_time>=? AND uti.create_time<? " +
                (taskType == null || taskType.isEmpty() ? "" : " AND tc.task_type=? ") +
                "GROUP BY u.id, u.username ORDER BY " + sortExpr + " DESC LIMIT " + limit;

        List<Object> params = new ArrayList<>();
        params.add(ts(range[0]));
        params.add(ts(range[1]));
        if (taskType != null && !taskType.isEmpty()) params.add(taskType);

        List<TaskAnalyticsDTO.UserTopNItem> out = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, params.toArray())) {
            out.add(TaskAnalyticsDTO.UserTopNItem.builder()
                    .userId(parseLong(row.get("userId")))
                    .userName(String.valueOf(row.get("userName")))
                    .acceptedCount(parseLong(row.get("acceptedCount")))
                    .topTaskCount(parseLong(row.get("topTaskCount")))
                    .build());
        }
        return out;
    }

    @Override
    public List<TaskAnalyticsDTO.UserTopNItem> userTaskTopNAccepted(String startTime, String endTime, Integer n, String taskType, String orderBy) {
        return userTaskTopN(startTime, endTime, n, taskType, orderBy);
    }

    @Override
    public TaskAnalyticsDTO.AnomalyMetrics userTaskAnomaly(String startTime, String endTime, String taskType, String campaignId) {
        Instant[] range = resolveRange(startTime, endTime, 7);
        Object[] params = taskOnlyFilterParams(taskType, campaignId, range[0], range[1]);

        String baseWhere = " WHERE rr.create_time>=? AND rr.create_time<? " +
                "AND (? IS NULL OR ?='' OR tc.task_type=?) " +
                "AND (? IS NULL OR ?='' OR tc.trigger_event=? OR CAST(tc.id AS CHAR)=?) ";

        long total = countLong("SELECT COUNT(1) FROM user_reward_record rr JOIN task_config tc ON tc.id=rr.task_id" + baseWhere, params);
        long failed = countLong("SELECT COUNT(1) FROM user_reward_record rr JOIN task_config tc ON tc.id=rr.task_id" + baseWhere + " AND rr.grant_status=3", params);
        long replayCount = countLong("SELECT COUNT(1) FROM user_reward_record rr JOIN task_config tc ON tc.id=rr.task_id" + baseWhere + " AND rr.error_msg IS NOT NULL AND LOWER(rr.error_msg) LIKE '%replay%'", params);
        long idempotentConflictCount = countLong(
                "SELECT COUNT(1) FROM (SELECT rr.message_id FROM user_reward_record rr JOIN task_config tc ON tc.id=rr.task_id" + baseWhere +
                        " AND rr.message_id IS NOT NULL AND rr.message_id<>'' GROUP BY rr.message_id HAVING COUNT(1)>1) t",
                params);

        List<TaskAnalyticsDTO.ErrorReasonItem> reasons = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT COALESCE(SUBSTRING(error_msg,1,64),'UNKNOWN') AS reason, COUNT(1) AS cnt " +
                        "FROM user_reward_record rr JOIN task_config tc ON tc.id=rr.task_id " +
                        baseWhere + " AND rr.error_msg IS NOT NULL GROUP BY reason ORDER BY cnt DESC LIMIT 5", params)) {
            reasons.add(TaskAnalyticsDTO.ErrorReasonItem.builder()
                    .reason(String.valueOf(row.get("reason")))
                    .count(parseLong(row.get("cnt")))
                    .build());
        }

        return TaskAnalyticsDTO.AnomalyMetrics.builder()
                .errorRate(ratio(failed, total))
                .topErrorReasons(reasons)
                .replayCount(replayCount)
                .idempotentConflictCount(idempotentConflictCount)
                .build();
    }

    @Override
    public TaskAnalyticsDTO.CostRoiMetrics userTaskCostRoi(String startTime, String endTime, String taskType, String campaignId) {
        Instant[] range = resolveRange(startTime, endTime, 7);
        Object[] params = taskOnlyFilterParams(taskType, campaignId, range[0], range[1]);

        String taskWhere = " WHERE uti.create_time>=? AND uti.create_time<? " +
                "AND (? IS NULL OR ?='' OR tc.task_type=?) " +
                "AND (? IS NULL OR ?='' OR tc.trigger_event=? OR CAST(tc.id AS CHAR)=?) ";
        String rewardWhere = " WHERE rr.create_time>=? AND rr.create_time<? " +
                "AND (? IS NULL OR ?='' OR tc.task_type=?) " +
                "AND (? IS NULL OR ?='' OR tc.trigger_event=? OR CAST(tc.id AS CHAR)=?) ";

        long completed = countLong("SELECT COUNT(1) FROM user_task_instance uti JOIN task_config tc ON tc.id=uti.task_id" + taskWhere + " AND uti.status=3", params);
        double rewardCost = queryDoubleOrZero("SELECT COALESCE(SUM(rr.reward_value),0) FROM user_reward_record rr JOIN task_config tc ON tc.id=rr.task_id" + rewardWhere + " AND rr.grant_status=2", params);

        return TaskAnalyticsDTO.CostRoiMetrics.builder()
                .completedCount(completed)
                .rewardCost(round2(rewardCost))
                .costPerCompletion(completed <= 0 ? 0D : round2(rewardCost / completed))
                .roi(completed <= 0 ? 0D : round2(completed / Math.max(rewardCost, 1D)))
                .build();
    }

    private TaskAnalyticsDTO.DimensionPerformance toDimPerformance(Map<String, Object> row) {
        long accepted = parseLong(row.get("acceptedCount"));
        long completed = parseLong(row.get("completedCount"));
        long rewarded = parseLong(row.get("rewardedCount"));
        return TaskAnalyticsDTO.DimensionPerformance.builder()
                .name(String.valueOf(row.get("dimName")))
                .acceptedCount(accepted)
                .completedCount(completed)
                .rewardedCount(rewarded)
                .completionRate(ratio(completed, accepted))
                .rewardRate(ratio(rewarded, completed))
                .avgCompletionMinutes(round2(parseDouble(row.get("avgCompletionMinutes"))))
                .build();
    }

    private TaskAnalyticsDTO.MetricOverview metricsInRange(Long taskId, Instant start, Instant end, int days) {
        Date startDate = start == null ? null : Date.from(start);
        Date endDate = end == null ? null : Date.from(end);
        long accepted = countOverviewAccepted(taskId, startDate, endDate);
        long completed = countOverviewCompleted(taskId, startDate, endDate);
        long rewarded = countOverviewRewarded(taskId, startDate, endDate);
        double avgMinutes = queryOverviewAvgCompletionMinutes(taskId, startDate, endDate);
        return TaskAnalyticsDTO.MetricOverview.builder()
                .days(days)
                .acceptedCount(accepted)
                .completedCount(completed)
                .rewardedCount(rewarded)
                .completionRate(ratio(completed, accepted))
                .rewardRate(ratio(rewarded, completed))
                .avgCompletionMinutes(round2(avgMinutes))
                .build();
    }

    private List<Map<String, Object>> queryHeatmapRows(Long taskId, Date start, Date end) {
        QueryWrapper<UserTaskInstance> qw = new QueryWrapper<UserTaskInstance>()
                .select("DAYOFWEEK(create_time) AS dayOfWeek", "HOUR(create_time) AS hourOfDay", "COUNT(1) AS cnt")
                .eq("task_id", taskId)
                .gt("status", 0)
                .groupBy("DAYOFWEEK(create_time)", "HOUR(create_time)");
        if (start != null) {
            qw.ge("create_time", start);
        }
        if (end != null) {
            qw.lt("create_time", end);
        }
        return userTaskInstanceMapper.selectMaps(qw);
    }

    private List<Map<String, Object>> queryElasticAccepted(Set<Long> taskIds, Date start, Date end) {
        QueryWrapper<UserTaskInstance> qw = new QueryWrapper<UserTaskInstance>()
                .select("task_id", "COUNT(1) AS acceptedCount", "SUM(CASE WHEN status=3 THEN 1 ELSE 0 END) AS completedCount")
                .in("task_id", taskIds)
                .gt("status", 0)
                .groupBy("task_id");
        if (start != null) {
            qw.ge("create_time", start);
        }
        if (end != null) {
            qw.lt("create_time", end);
        }
        return userTaskInstanceMapper.selectMaps(qw);
    }

    private List<Map<String, Object>> queryElasticRewarded(Set<Long> taskIds, Date start, Date end) {
        QueryWrapper<UserRewardRecord> qw = new QueryWrapper<UserRewardRecord>()
                .select("task_id", "COUNT(1) AS rewardedCount")
                .in("task_id", taskIds)
                .eq("grant_status", 2)
                .groupBy("task_id");
        if (start != null) {
            qw.ge("create_time", start);
        }
        if (end != null) {
            qw.lt("create_time", end);
        }
        return userRewardRecordMapper.selectMaps(qw);
    }

    private String rewardBucket(Integer rewardValue) {
        int value = rewardValue == null ? 0 : rewardValue;
        if (value <= 10) {
            return "0-10";
        }
        if (value <= 50) {
            return "11-50";
        }
        if (value <= 100) {
            return "51-100";
        }
        return "100+";
    }

    private TaskAnalyticsDTO.VersionCompare buildVersionCompare(String baselineVersion,
                                                                Instant baselineStart,
                                                                Instant baselineEnd,
                                                                String compareVersion,
                                                                Instant compareStart,
                                                                Instant compareEnd,
                                                                TaskAnalyticsDTO.MetricOverview baseline,
                                                                TaskAnalyticsDTO.MetricOverview compare,
                                                                String note) {
        return TaskAnalyticsDTO.VersionCompare.builder()
                .baselineVersion(baselineVersion)
                .compareVersion(compareVersion)
                .baselineStart(Date.from(baselineStart))
                .baselineEnd(Date.from(baselineEnd))
                .compareStart(Date.from(compareStart))
                .compareEnd(Date.from(compareEnd))
                .baseline(baseline)
                .compare(compare)
                .deltaCompletionRate(round2(safeDouble(compare.getCompletionRate()) - safeDouble(baseline.getCompletionRate())))
                .deltaRewardRate(round2(safeDouble(compare.getRewardRate()) - safeDouble(baseline.getRewardRate())))
                .note(note)
                .build();
    }

    private TaskConfigHistory resolveHistory(Long taskId, String versionText, boolean latestIfMissing) {
        if (versionText != null && !versionText.trim().isEmpty()) {
            Integer versionNo = parseInt(versionText);
            if (versionNo != null) {
                return taskConfigHistoryMapper.selectByTaskIdAndVersion(taskId, versionNo);
            }
        }
        if (!latestIfMissing) {
            QueryWrapper<TaskConfigHistory> qw = new QueryWrapper<TaskConfigHistory>()
                    .eq("task_id", taskId)
                    .orderByDesc("version_no")
                    .last("LIMIT 1 OFFSET 1");
            return taskConfigHistoryMapper.selectOne(qw);
        }
        QueryWrapper<TaskConfigHistory> qw = new QueryWrapper<TaskConfigHistory>()
                .eq("task_id", taskId)
                .orderByDesc("version_no")
                .last("LIMIT 1");
        return taskConfigHistoryMapper.selectOne(qw);
    }

    private String baselineVersionOf(TaskConfigHistory h, String fallback) {
        if (h != null && h.getVersionNo() != null) {
            return String.valueOf(h.getVersionNo());
        }
        return fallback;
    }

    private String compareVersionOf(TaskConfigHistory h, String fallback) {
        if (h != null && h.getVersionNo() != null) {
            return String.valueOf(h.getVersionNo());
        }
        return fallback;
    }

    private String baseCountSql(String projection, String userLayer) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT COALESCE(").append(projection).append(",0) FROM user_task_instance uti ")
                .append("JOIN task_config tc ON tc.id=uti.task_id ")
                .append("JOIN user u ON u.id=uti.user_id ");
        if (userLayer != null && !userLayer.isEmpty()) {
            sb.append("LEFT JOIN (SELECT user_id, MAX(update_time) AS last_active FROM user_task_instance GROUP BY user_id) ua ON ua.user_id=u.id ");
        }
        sb.append("WHERE uti.create_time>=? AND uti.create_time<? ")
                .append("AND (? IS NULL OR ?='' OR tc.task_type=?) ")
                .append("AND (? IS NULL OR ?='' OR tc.trigger_event=? OR CAST(tc.id AS CHAR)=?) ");
        appendLayerClause(sb, userLayer);
        return sb.toString();
    }

    private String baseRewardJoinCountSql(String userLayer) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT COALESCE(COUNT(1),0) FROM user_reward_record rr ")
                .append("JOIN task_config tc ON tc.id=rr.task_id ")
                .append("JOIN user u ON u.id=rr.user_id ");
        if (userLayer != null && !userLayer.isEmpty()) {
            sb.append("LEFT JOIN (SELECT user_id, MAX(update_time) AS last_active FROM user_task_instance GROUP BY user_id) ua ON ua.user_id=u.id ");
        }
        sb.append("WHERE rr.grant_status=2 AND rr.create_time>=? AND rr.create_time<? ")
                .append("AND (? IS NULL OR ?='' OR tc.task_type=?) ")
                .append("AND (? IS NULL OR ?='' OR tc.trigger_event=? OR CAST(tc.id AS CHAR)=?) ");
        appendLayerClause(sb, userLayer);
        return sb.toString();
    }

    private String completedOverviewSql(String projection, String userLayer, boolean rewardedOnly) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ").append(projection).append(" FROM user_task_instance uti ")
                .append("JOIN task_config tc ON tc.id=uti.task_id ")
                .append("JOIN user u ON u.id=uti.user_id ");
        if (userLayer != null && !userLayer.isEmpty()) {
            sb.append("LEFT JOIN (SELECT user_id, MAX(update_time) AS last_active FROM user_task_instance GROUP BY user_id) ua ON ua.user_id=u.id ");
        }
        sb.append("WHERE uti.status=3 AND uti.update_time>=? AND uti.update_time<? ")
                .append("AND (? IS NULL OR ?='' OR tc.task_type=?) ")
                .append("AND (? IS NULL OR ?='' OR tc.trigger_event=? OR CAST(tc.id AS CHAR)=?) ");
        appendLayerClause(sb, userLayer);
        if (rewardedOnly) {
            sb.append("AND EXISTS (SELECT 1 FROM user_reward_record rr ")
                    .append("WHERE rr.task_id=uti.task_id AND rr.user_id=uti.user_id AND rr.grant_status=2 AND rr.create_time<?) ");
        }
        return sb.toString();
    }

    private void appendLayerClause(StringBuilder sb, String userLayer) {
        if (userLayer == null || userLayer.isEmpty()) {
            return;
        }
        String upper = userLayer.toUpperCase();
        if ("NEW".equals(upper)) {
            sb.append("AND u.create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) ");
        } else if ("ACTIVE".equals(upper)) {
            sb.append("AND u.create_time < DATE_SUB(NOW(), INTERVAL 7 DAY) ")
                    .append("AND COALESCE(ua.last_active, '1970-01-01') >= DATE_SUB(NOW(), INTERVAL 7 DAY) ");
        } else if ("SILENT".equals(upper)) {
            sb.append("AND COALESCE(ua.last_active, '1970-01-01') < DATE_SUB(NOW(), INTERVAL 7 DAY) ");
        }
    }

    private Object[] baseParams(String taskType, String campaignId, Instant start, Instant end) {
        return new Object[]{
                ts(start), ts(end),
                taskType, taskType, taskType,
                campaignId, campaignId, campaignId, campaignId
        };
    }

    private Object[] taskOnlyFilterParams(String taskType, String campaignId, Instant start, Instant end) {
        return new Object[]{
                ts(start), ts(end),
                taskType, taskType, taskType,
                campaignId, campaignId, campaignId, campaignId
        };
    }

    private Object[] completedOverviewParams(String taskType, String campaignId, Instant start, Instant end, boolean rewardedOnly) {
        List<Object> params = new ArrayList<>();
        params.add(ts(start));
        params.add(ts(end));
        params.add(taskType);
        params.add(taskType);
        params.add(taskType);
        params.add(campaignId);
        params.add(campaignId);
        params.add(campaignId);
        params.add(campaignId);
        if (rewardedOnly) {
            params.add(ts(end));
        }
        return params.toArray();
    }

    private Instant[] resolveRange(String startTime, String endTime, int defaultDays) {
        Instant end = parseTime(endTime, Instant.now());
        Instant start = parseTime(startTime, end.minus(defaultDays, ChronoUnit.DAYS));
        if (!start.isBefore(end)) {
            start = end.minus(defaultDays, ChronoUnit.DAYS);
        }
        return new Instant[]{start, end};
    }

    private Instant parseTime(String text, Instant defaultValue) {
        if (text == null || text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private Instant safeInstant(Date date) {
        if (date == null) {
            return Instant.now();
        }
        return date.toInstant();
    }

    private long countLong(String sql, Object... args) {
        Number v = jdbcTemplate.queryForObject(sql, Number.class, args);
        return v == null ? 0L : v.longValue();
    }

    private double queryDoubleOrZero(String sql, Object... args) {
        Number v = jdbcTemplate.queryForObject(sql, Number.class, args);
        return v == null ? 0D : v.doubleValue();
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return round2((numerator * 1.0D / denominator) * 100D);
    }

    private double round2(double v) {
        return Math.round(v * 100D) / 100D;
    }

    private int normalizeDaysBetween(Instant start, Instant end, int fallbackDays) {
        if (start == null || end == null || !start.isBefore(end)) {
            return Math.max(1, fallbackDays);
        }
        long days = ChronoUnit.DAYS.between(start, end);
        return (int) Math.max(1L, days);
    }

    private long countOverviewAccepted(Long taskId, Date startDate, Date endDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM user_task_instance WHERE task_id=? AND status>0");
        List<Object> params = new ArrayList<>();
        params.add(taskId);
        if (startDate != null) {
            sql.append(" AND create_time>=?");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND create_time<?");
            params.add(endDate);
        }
        return countLong(sql.toString(), params.toArray());
    }

    private long countOverviewCompleted(Long taskId, Date startDate, Date endDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM user_task_instance WHERE task_id=? AND status=3");
        List<Object> params = new ArrayList<>();
        params.add(taskId);
        if (startDate != null) {
            sql.append(" AND update_time>=?");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND update_time<?");
            params.add(endDate);
        }
        return countLong(sql.toString(), params.toArray());
    }

    private long countOverviewRewarded(Long taskId, Date startDate, Date endDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM user_task_instance uti WHERE uti.task_id=? AND uti.status=3");
        List<Object> params = new ArrayList<>();
        params.add(taskId);
        if (startDate != null) {
            sql.append(" AND uti.update_time>=?");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND uti.update_time<?");
            params.add(endDate);
        }
        sql.append(" AND EXISTS (SELECT 1 FROM user_reward_record rr WHERE rr.task_id=uti.task_id AND rr.user_id=uti.user_id AND rr.grant_status=2");
        if (endDate != null) {
            sql.append(" AND rr.create_time<?");
            params.add(endDate);
        }
        sql.append(")");
        return countLong(sql.toString(), params.toArray());
    }

    private double queryOverviewAvgCompletionMinutes(Long taskId, Date startDate, Date endDate) {
        StringBuilder sql = new StringBuilder("SELECT AVG(TIMESTAMPDIFF(MINUTE, create_time, update_time)) FROM user_task_instance WHERE task_id=? AND status=3");
        List<Object> params = new ArrayList<>();
        params.add(taskId);
        if (startDate != null) {
            sql.append(" AND update_time>=?");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND update_time<?");
            params.add(endDate);
        }
        return queryDoubleOrZero(sql.toString(), params.toArray());
    }

    private long parseLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    private Date toDate(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Date) {
            return (Date) v;
        }
        if (v instanceof Timestamp) {
            return new Date(((Timestamp) v).getTime());
        }
        if (v instanceof Instant) {
            return Date.from((Instant) v);
        }
        if (v instanceof LocalDateTime) {
            LocalDateTime ldt = (LocalDateTime) v;
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        }
        if (v instanceof LocalDate) {
            LocalDate ld = (LocalDate) v;
            return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        return null;
    }

    private Integer parseInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private double parseDouble(Object v) {
        if (v == null) {
            return 0D;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0D;
        }
    }

    private double safeDouble(Double v) {
        return v == null ? 0D : v;
    }

    private long safeLong(Long v) {
        return v == null ? 0L : v;
    }
}
