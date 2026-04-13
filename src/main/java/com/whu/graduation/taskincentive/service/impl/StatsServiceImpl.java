package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dao.mapper.RiskDecisionLogMapper;
import com.whu.graduation.taskincentive.dao.mapper.TaskConfigMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.dto.BarChartData;
import com.whu.graduation.taskincentive.dto.DailyStatItem;
import com.whu.graduation.taskincentive.dto.LatestActivityItem;
import com.whu.graduation.taskincentive.dto.ProgressDataItem;
import com.whu.graduation.taskincentive.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final UserMapper userMapper;
    private final UserTaskInstanceMapper userTaskInstanceMapper;
    private final UserRewardRecordMapper userRewardRecordMapper;
    private final TaskConfigMapper taskConfigMapper;
    private final RiskDecisionLogMapper riskDecisionLogMapper;

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat DATE_TIME_SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<BarChartData> getTwoWeeksTaskReceiveAndComplete() {
        // 计算本周周一和上周周一
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int offsetToMon = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        Calendar thisMon = (Calendar) cal.clone();
        thisMon.add(Calendar.DAY_OF_MONTH, offsetToMon);

        Calendar lastMon = (Calendar) thisMon.clone();
        lastMon.add(Calendar.DAY_OF_MONTH, -7);

        // 查询范围：lastMon (包含) -> thisMon + 7 (不包含) ，覆盖上周和本周
        Date rangeStart = lastMon.getTime();
        Calendar tmp = (Calendar) thisMon.clone(); tmp.add(Calendar.DAY_OF_MONTH, 7);
        Date rangeEnd = tmp.getTime();

        // 一次性查询：当天任务接取数 和 已完成数（按 status=1）
        List<Map<String, Object>> receivedRows = userTaskInstanceMapper.countTasksGroupByDate(rangeStart, rangeEnd);
        List<Map<String, Object>> completedRows = userTaskInstanceMapper.countTasksByStatusGroupByDate(rangeStart, rangeEnd, 1);

        Map<String, Long> receivedMap = new HashMap<>();
        Map<String, Long> completedMap = new HashMap<>();
        for (Map<String, Object> r : receivedRows) {
            Object d = r.get("the_date"); Object c = r.get("cnt");
            if (d != null) {
                String key = d.toString();
                long v = 0L;
                if (c instanceof Number) v = ((Number) c).longValue();
                else try { v = Long.parseLong(String.valueOf(c)); } catch (Exception ignored) {}
                receivedMap.put(key, v);
            }
        }
        for (Map<String, Object> r : completedRows) {
            Object d = r.get("the_date"); Object c = r.get("cnt");
            if (d != null) {
                String key = d.toString();
                long v = 0L;
                if (c instanceof Number) v = ((Number) c).longValue();
                else try { v = Long.parseLong(String.valueOf(c)); } catch (Exception ignored) {}
                completedMap.put(key, v);
            }
        }

        // 填充 lastWeek 和 thisWeek 两个数组（顺序：周一->周日）
        List<Long> thisReq = new ArrayList<>(); List<Long> thisComp = new ArrayList<>();
        List<Long> lastReq = new ArrayList<>(); List<Long> lastComp = new ArrayList<>();

        Calendar iter = (Calendar) lastMon.clone();
        for (int i = 0; i < 14; i++) {
            String key = SDF.format(iter.getTime());
            long rcv = receivedMap.getOrDefault(key, 0L);
            long cpl = completedMap.getOrDefault(key, 0L);
            if (i < 7) {
                // last week (first 7 days)
                lastReq.add(rcv); lastComp.add(cpl);
            } else {
                // this week (next 7 days)
                thisReq.add(rcv); thisComp.add(cpl);
            }
            iter.add(Calendar.DAY_OF_MONTH, 1);
        }

        BarChartData thisWeek = BarChartData.builder().taskReceived(thisReq).taskCompleted(thisComp).build();
        BarChartData lastWeek = BarChartData.builder().taskReceived(lastReq).taskCompleted(lastComp).build();
        return Arrays.asList(thisWeek, lastWeek);
    }

    @Override
    public List<ProgressDataItem> getThisWeekCompletionPercent() {
        // 使用一次性按天查询，然后计算百分比
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int offsetToMon = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        Calendar thisMon = (Calendar) cal.clone(); thisMon.add(Calendar.DAY_OF_MONTH, offsetToMon);

        Calendar endCal = (Calendar) thisMon.clone(); endCal.add(Calendar.DAY_OF_MONTH, 7);
        Date start = thisMon.getTime(); Date end = endCal.getTime();

        List<Map<String, Object>> receivedRows = userTaskInstanceMapper.countTasksGroupByDate(start, end);
        List<Map<String, Object>> completedRows = userTaskInstanceMapper.countTasksByStatusGroupByDate(start, end, 1);
        Map<String, Long> receivedMap = new HashMap<>();
        Map<String, Long> completedMap = new HashMap<>();
        for (Map<String, Object> r : receivedRows) {
            Object d = r.get("the_date"); Object c = r.get("cnt");
            if (d != null) {
                String key = d.toString();
                long v = 0L;
                if (c instanceof Number) v = ((Number) c).longValue();
                else try { v = Long.parseLong(String.valueOf(c)); } catch (Exception ignored) {}
                receivedMap.put(key, v);
            }
        }
        for (Map<String, Object> r : completedRows) {
            Object d = r.get("the_date"); Object c = r.get("cnt");
            if (d != null) {
                String key = d.toString();
                long v = 0L;
                if (c instanceof Number) v = ((Number) c).longValue();
                else try { v = Long.parseLong(String.valueOf(c)); } catch (Exception ignored) {}
                completedMap.put(key, v);
            }
        }

        List<ProgressDataItem> items = new ArrayList<>();
        Calendar iter = (Calendar) thisMon.clone();
        for (int i = 0; i < 7; i++) {
            String key = SDF.format(iter.getTime());
            long total = receivedMap.getOrDefault(key, 0L);
            long comp = completedMap.getOrDefault(key, 0L);
            int percent = 0;
            if (total > 0) percent = (int) Math.round((double) comp * 100.0 / (double) total);
            String color = colorFromPercent(percent);
            ProgressDataItem item = ProgressDataItem.builder()
                    .week(getWeekName(iter))
                    .percentage(percent)
                    .duration(110 - i*5)
                    .color(color)
                    .build();
            items.add(item);
            iter.add(Calendar.DAY_OF_MONTH, 1);
        }
        return items;
    }

    @Override
    public Page<DailyStatItem> pagedDailyStats(int page, int size) {
        // 优化：一次性按天查询各项指标，然后在服务端组装并分页
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();
        int days = 30;

        // 数据查询区间：从 earliestDay (包含) 到 tomorrow (不包含)
        Calendar earliest = (Calendar) cal.clone(); earliest.add(Calendar.DAY_OF_MONTH, -(days-1));
        Date rangeStart = earliest.getTime();
        Calendar rangeEndCal = (Calendar) cal.clone(); rangeEndCal.add(Calendar.DAY_OF_MONTH, 1);
        Date rangeEnd = rangeEndCal.getTime();

        // 1. 每日新增用户（按 create_time 分组） -> 用于前缀和计算累计用户数
        List<Map<String, Object>> newUserRows = userMapper.countUsersGroupByDate(rangeStart, rangeEnd);
        Map<String, Long> dailyNewUsers = new HashMap<>();
        for (Map<String, Object> r : newUserRows) {
            Object d = r.get("the_date"); Object c = r.get("cnt");
            if (d != null) {
                String key = d.toString();
                long v = 0L; if (c instanceof Number) v = ((Number)c).longValue(); else try{ v = Long.parseLong(String.valueOf(c)); } catch(Exception ignored){}
                dailyNewUsers.put(key, v);
            }
        }
        long base = userMapper.countUsersBefore(rangeStart); // start之前的累计

        // 2. 活跃用户（定义：以统计日为截止，前7天内接取过任务的去重用户数）
        Map<String, Long> activeMap = new HashMap<>();
        // 为了计算每个统计日的7日窗口，需要把查询起点前移6天
        Calendar activeStartCal = Calendar.getInstance();
        activeStartCal.setTime(rangeStart);
        activeStartCal.add(Calendar.DAY_OF_MONTH, -6);
        Date activeDataStart = activeStartCal.getTime();
        // 一次性查询 [activeDataStart, rangeEnd) 内每天的 user_id
        List<Map<String, Object>> activeSourceRows = userTaskInstanceMapper.selectUserIdsByDate(activeDataStart, rangeEnd);
        // 按天聚合成 date -> userIdSet
        Map<String, Set<Long>> dateToUsers = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (Map<String, Object> r : activeSourceRows) {
            Object d = r.get("the_date");
            Object uid = r.get("user_id");
            if (d == null || uid == null) continue;

            Long userId = null;
            if (uid instanceof Number) {
                userId = ((Number) uid).longValue();
            } else {
                try {
                    userId = Long.parseLong(String.valueOf(uid));
                } catch (Exception ignored) {}
            }
            if (userId == null) continue;

            String dayKey = d.toString();
            dateToUsers.computeIfAbsent(dayKey, k -> new HashSet<>()).add(userId);
        }
        // 对每个统计日 D，计算 [D-6, D] 的并集大小
        Calendar dayIter = Calendar.getInstance();
        dayIter.setTime(rangeStart);
        while (dayIter.getTime().before(rangeEnd)) {
            Set<Long> union = new HashSet<>();
            Calendar scan = (Calendar) dayIter.clone();
            scan.add(Calendar.DAY_OF_MONTH, -6);
            while (!scan.getTime().after(dayIter.getTime())) {
                String k = sdf.format(scan.getTime());
                Set<Long> s = dateToUsers.get(k);
                if (s != null && !s.isEmpty()) {
                    union.addAll(s);
                }
                scan.add(Calendar.DAY_OF_MONTH, 1);
            }
            String statDayKey = sdf.format(dayIter.getTime());
            activeMap.put(statDayKey, (long) union.size());
            dayIter.add(Calendar.DAY_OF_MONTH, 1);
        }


        // 3. 接取数/完成数
        List<Map<String, Object>> recvRows = userTaskInstanceMapper.countTasksGroupByDate(rangeStart, rangeEnd);
        List<Map<String, Object>> compRows = userTaskInstanceMapper.countTasksByStatusGroupByDate(rangeStart, rangeEnd, 1);
        Map<String, Long> recvMap = new HashMap<>(); Map<String, Long> compMap = new HashMap<>();
        for (Map<String, Object> r : recvRows) { Object d = r.get("the_date"); Object c = r.get("cnt"); if (d != null) { String key = d.toString(); long v=0; if (c instanceof Number) v=((Number)c).longValue(); else try{v=Long.parseLong(String.valueOf(c));}catch(Exception ignored){} recvMap.put(key,v);} }
        for (Map<String, Object> r : compRows) { Object d = r.get("the_date"); Object c = r.get("cnt"); if (d != null) { String key = d.toString(); long v=0; if (c instanceof Number) v=((Number)c).longValue(); else try{v=Long.parseLong(String.valueOf(c));}catch(Exception ignored){} compMap.put(key,v);} }

        // 组装列表（正序：earliest -> today），累计用户数只增不减
        List<DailyStatItem> all = new ArrayList<>();
        long running = base;
        Calendar iter = (Calendar) earliest.clone(); // 从最早日期开始
        for (int i = 0; i < days; i++) {
            Date dayStart = iter.getTime();
            String key = SDF.format(dayStart);
            long newUsers = dailyNewUsers.getOrDefault(key, 0L);
            running += newUsers; // cumulative
            long userTotal = running;
            long activeUser = activeMap.getOrDefault(key, 0L);
            long taskReceived = recvMap.getOrDefault(key, 0L);
            long taskCompleted = compMap.getOrDefault(key, 0L);
            String completionRate = "0%";
            if (taskReceived > 0) completionRate = String.format("%d%%", Math.round((double)taskCompleted * 100.0 / (double)taskReceived));
            DailyStatItem item = DailyStatItem.builder()
                    .statDate(key)
                    .userTotal(userTotal)
                    .activeUser(activeUser)
                    .taskReceived(taskReceived)
                    .taskCompleted(taskCompleted)
                    .completionRate(completionRate)
                    .build();
            all.add(item);
            iter.add(Calendar.DAY_OF_MONTH, 1); // 正序遍历
        }
        // 如需 today first，反转 all
        Collections.reverse(all);

        int from = (page - 1) * size;
        int to = Math.min(from + size, all.size());
        Page<DailyStatItem> pg = new Page<>(page, size);
        if (from >= all.size()) { pg.setRecords(Collections.emptyList()); pg.setTotal(all.size()); return pg; }
        List<DailyStatItem> sub = all.subList(from, to);
        pg.setRecords(sub); pg.setTotal(all.size());
        return pg;
    }

    @Override
    public List<LatestActivityItem> latestActivities(int limit) {
        int safeLimit = Math.max(5, Math.min(limit, 20));
        int fetchLimit = Math.max(6, safeLimit);

        List<LatestActivityItem> taskItems = buildTaskConfigActivities(fetchLimit);
        List<LatestActivityItem> riskItems = buildRiskActivities(fetchLimit);
        List<LatestActivityItem> rewardItems = buildRewardActivities(fetchLimit);

        return Stream.of(taskItems, riskItems, rewardItems)
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(LatestActivityItem::getTime, Comparator.nullsLast(String::compareTo)).reversed())
                .limit(safeLimit)
                .toList();
    }

    private List<LatestActivityItem> buildTaskConfigActivities(int limit) {
        List<TaskConfig> rows = taskConfigMapper.selectList(
                new LambdaQueryWrapper<TaskConfig>()
                        .orderByDesc(TaskConfig::getUpdateTime)
                        .last("LIMIT " + limit)
        );

        List<LatestActivityItem> items = new ArrayList<>();
        for (TaskConfig task : rows) {
            boolean createdRecently = task.getCreateTime() != null
                    && task.getUpdateTime() != null
                    && Math.abs(task.getUpdateTime().getTime() - task.getCreateTime().getTime()) < 1000L;
            String action = createdRecently ? "新建任务" : "更新任务";
            String statusText = Objects.equals(task.getStatus(), 1) ? "启用" : "停用";
            items.add(LatestActivityItem.builder()
                    .type("TASK_CONFIG")
                    .level("info")
                    .title(action + "：" + nullSafe(task.getTaskName(), "未命名任务"))
                    .summary(String.format("任务类型：%s，奖励类型：%s，当前状态：%s",
                            nullSafe(task.getTaskType(), "-"),
                            nullSafe(task.getRewardType(), "-"),
                            statusText))
                    .time(formatDateTime(task.getUpdateTime()))
                    .build());
        }
        return items;
    }

    private List<LatestActivityItem> buildRiskActivities(int limit) {
        List<RiskDecisionLog> rows = riskDecisionLogMapper.selectList(
                new LambdaQueryWrapper<RiskDecisionLog>()
                        .in(RiskDecisionLog::getDecision, Arrays.asList("REJECT", "REVIEW", "FREEZE", "DEGRADE_PASS"))
                        .orderByDesc(RiskDecisionLog::getCreatedAt)
                        .last("LIMIT " + limit)
        );

        Set<Long> userIds = rows.stream()
            .map(RiskDecisionLog::getUserId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Set<Long> taskIds = rows.stream()
            .map(RiskDecisionLog::getTaskId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());

        Map<Long, String> userNameMap = userIds.isEmpty()
            ? Collections.emptyMap()
            : userMapper.selectBatchIds(userIds).stream()
            .collect(java.util.stream.Collectors.toMap(com.whu.graduation.taskincentive.dao.entity.User::getId,
                com.whu.graduation.taskincentive.dao.entity.User::getUsername,
                (a, b) -> a));
        Map<Long, String> taskNameMap = taskIds.isEmpty()
            ? Collections.emptyMap()
            : taskConfigMapper.selectBatchIds(taskIds).stream()
            .collect(java.util.stream.Collectors.toMap(TaskConfig::getId, TaskConfig::getTaskName, (a, b) -> a));

        List<LatestActivityItem> items = new ArrayList<>();
        for (RiskDecisionLog row : rows) {
            String userName = nullSafe(userNameMap.get(row.getUserId()), "未知用户");
            String taskName = nullSafe(taskNameMap.get(row.getTaskId()), "未知任务");
            items.add(LatestActivityItem.builder()
                    .type("RISK_DECISION")
                    .level(resolveRiskLevel(row.getDecision()))
                    .title("风控判定：" + nullSafe(row.getDecision(), "UNKNOWN"))
                .summary(String.format("用户 %s，任务 %s，原因码：%s",
                    userName,
                    taskName,
                            nullSafe(row.getReasonCode(), "-")))
                    .time(formatDateTime(row.getCreatedAt()))
                    .build());
        }
        return items;
    }

    private List<LatestActivityItem> buildRewardActivities(int limit) {
        List<UserRewardRecord> rows = userRewardRecordMapper.selectList(
                new LambdaQueryWrapper<UserRewardRecord>()
                        .in(UserRewardRecord::getGrantStatus, Arrays.asList(1, 3))
                        .orderByDesc(UserRewardRecord::getUpdateTime)
                        .last("LIMIT " + limit)
        );

        List<LatestActivityItem> items = new ArrayList<>();
        for (UserRewardRecord row : rows) {
            boolean failed = Objects.equals(row.getGrantStatus(), 3);
            items.add(LatestActivityItem.builder()
                    .type("REWARD_EXCEPTION")
                    .level(failed ? "danger" : "warning")
                    .title(failed ? "奖励发放失败" : "奖励发放处理中")
                    .summary(String.format("用户 %s，任务 %s，奖励类型：%s%s",
                            String.valueOf(row.getUserId()),
                            String.valueOf(row.getTaskId()),
                            nullSafe(row.getRewardType(), "-"),
                            failed && row.getErrorMsg() != null ? "，错误：" + row.getErrorMsg() : ""))
                    .time(formatDateTime(row.getUpdateTime() != null ? row.getUpdateTime() : row.getCreateTime()))
                    .build());
        }
        return items;
    }

    private String formatDateTime(Date date) {
        if (date == null) {
            return "";
        }
        return DATE_TIME_SDF.format(date);
    }

    private String resolveRiskLevel(String decision) {
        if ("FREEZE".equalsIgnoreCase(decision) || "REJECT".equalsIgnoreCase(decision)) {
            return "danger";
        }
        if ("REVIEW".equalsIgnoreCase(decision)) {
            return "warning";
        }
        return "info";
    }

    private String nullSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String getWeekName(Calendar c) {
        int dow = c.get(Calendar.DAY_OF_WEEK);
        switch (dow) {
            case Calendar.MONDAY: return "周一";
            case Calendar.TUESDAY: return "周二";
            case Calendar.WEDNESDAY: return "周三";
            case Calendar.THURSDAY: return "周四";
            case Calendar.FRIDAY: return "周五";
            case Calendar.SATURDAY: return "周六";
            case Calendar.SUNDAY: return "周日";
            default: return "";
        }
    }

    private String colorFromPercent(int p) {
        // 将 0..100 映射到从红色 (#ff4d4f) 到绿色 (#26ce83) 的渐变
        // RGB 空间中的简单线性插值
        int r1 = 0xff, g1 = 0x4d, b1 = 0x4f; // 红色
        int r2 = 0x26, g2 = 0xce, b2 = 0x83; // 绿色
        double t = Math.max(0, Math.min(100, p)) / 100.0;
        int r = (int) Math.round(r1 + (r2 - r1) * t);
        int g = (int) Math.round(g1 + (g2 - g1) * t);
        int b = (int) Math.round(b1 + (b2 - b1) * t);
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
