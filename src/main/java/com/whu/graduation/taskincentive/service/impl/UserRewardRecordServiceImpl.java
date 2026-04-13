package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.Badge;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dao.entity.UserBadge;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dao.mapper.BadgeMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserBadgeMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.UserBadgeService;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 用户奖励记录服务实现
 */
@Service
@RequiredArgsConstructor
public class UserRewardRecordServiceImpl extends ServiceImpl<UserRewardRecordMapper, UserRewardRecord>
        implements UserRewardRecordService {

    private static final int GRANT_INIT = 0;
    private static final int GRANT_PROCESSING = 1;
    private static final int GRANT_SUCCESS = 2;
    private static final int GRANT_FAILED = 3;

    private static final Set<String> POINT_REWARD_TYPES = Set.of("POINT", "REWARD_POINT", "OP_POINT", "POINT_CONSUME");
    private static final Set<String> BADGE_REWARD_TYPES = Set.of("BADGE", "REWARD_BADGE");
    private static final Set<String> ITEM_REWARD_TYPES = Set.of("ITEM", "REWARD_PHYSICAL", "REWARD_ITEM");

    private final UserRewardRecordMapper userRewardRecordMapper;
    private final UserMapper userMapper;
    private final TaskConfigService taskConfigService;
    private final UserBadgeService userBadgeService;
    private final UserBadgeMapper userBadgeMapper;
    private final BadgeMapper badgeMapper;

    @Override
    public boolean save(UserRewardRecord record) {
        record.setId(IdWorker.getId());
        return super.save(record);
    }

    @Override
    public boolean update(UserRewardRecord record) {
        return super.updateById(record);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public UserRewardRecord getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<UserRewardRecord> listAll() {
        return super.list();
    }

    @Override
    public List<UserRewardRecord> selectByUserId(Long userId) {
        return userRewardRecordMapper.selectByUserId(userId);
    }

    @Override
    public List<UserRewardRecord> selectUnclaimedPhysicalReward(Long userId) {
        return userRewardRecordMapper.selectUnclaimedPhysicalRewards(userId);
    }

    @Override
    public List<UserRewardRecord> selectByStatus(Long userId, Integer status) {
        return super.list(lambdaQuery()
                .eq(UserRewardRecord::getUserId, userId)
                .eq(UserRewardRecord::getStatus, status)
                .orderByAsc(UserRewardRecord::getCreateTime)
        );
    }

    @Override
    public Page<UserRewardRecord> selectByUserIdPage(Page<UserRewardRecord> page, Long userId, Integer status) {
        QueryWrapper<UserRewardRecord> wrapper = new QueryWrapper<UserRewardRecord>().eq("user_id", userId).orderByDesc("create_time");
        if (status != null) wrapper.eq("status", status);
        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    public long countUsersReceivedToday() {
        return userRewardRecordMapper.countDistinctUsersReceivedToday();
    }

    @Override
    public List<Long> getReceivedUsersLast7Days() {
        // 直接在数据库层面分组统计：按日期分组，组内对 user_id 去重计数
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -6);
        Date firstDay = cal.getTime();
        Calendar endCal = Calendar.getInstance();
        endCal.setTime(todayStart);
        endCal.add(Calendar.DAY_OF_MONTH, 1);
        Date end = endCal.getTime();
        List<Map<String, Object>> rows = userRewardRecordMapper.countDistinctUserIdsGroupByDate(firstDay, end);
        List<Long> result = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar iter = Calendar.getInstance();
        iter.setTime(firstDay);
        int idx = 0;
        while (!iter.getTime().after(todayStart)) {
            String key = sdf.format(iter.getTime());
            Long cnt = 0L;
            if (idx < rows.size()) {
                Map<String, Object> r = rows.get(idx);
                Object d = r.get("the_date");
                Object c = r.get("cnt");
                if (d != null && key.equals(d.toString())) {
                    if (c instanceof Number) cnt = ((Number)c).longValue();
                    else try { cnt = Long.parseLong(String.valueOf(c)); } catch(Exception ignored){}
                    idx++;
                }
            }
            result.add(cnt);
            iter.add(Calendar.DAY_OF_MONTH, 1);
        }
        return result;
    }

    @Override
    public Page<UserRewardRecord> listByConditions(Page<UserRewardRecord> page, Long userId, Long taskId, String rewardType, Integer status) {
        QueryWrapper<UserRewardRecord> wrapper = new QueryWrapper<>();
        if (userId != null) wrapper.eq("user_id", userId);
        if (taskId != null) wrapper.eq("task_id", taskId);
        if (rewardType != null && !rewardType.isEmpty()) wrapper.eq("reward_type", rewardType);
        if (status != null) wrapper.eq("status", status);
        wrapper.orderByDesc("create_time");
        Page<UserRewardRecord> result = this.baseMapper.selectPage(page, wrapper);
        enrichDisplayFields(result.getRecords());
        return result;
    }

    @Override
    public UserRewardRecord selectByMessageId(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return null;
        }
        return userRewardRecordMapper.selectByMessageId(messageId);
    }

    @Override
    public UserRewardRecord initRecordIfAbsent(String messageId, Long userId, Reward reward) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return null;
        }
        UserRewardRecord existing = userRewardRecordMapper.selectByMessageId(messageId);
        if (existing != null) {
            return existing;
        }

        UserRewardRecord record = UserRewardRecord.builder()
                .id(IdWorker.getId())
                .userId(userId)
                .taskId(reward == null ? null : reward.getTaskId())
                .rewardType(reward == null || reward.getRewardType() == null ? null : reward.getRewardType().toString())
                .status(0)
                .rewardValue(reward == null ? null : reward.getAmount())
                .messageId(messageId)
                .rewardId(reward == null ? null : reward.getRewardId())
                .grantStatus(GRANT_INIT)
                .errorMsg(null)
                .createTime(new Date())
                .build();
        try {
            super.save(record);
            return record;
        } catch (DuplicateKeyException e) {
            return userRewardRecordMapper.selectByMessageId(messageId);
        }
    }

    @Override
    public boolean markProcessing(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return false;
        }
        return userRewardRecordMapper.updateGrantStatusByMessageIdWithFromStatuses(
                messageId,
                GRANT_INIT,
                GRANT_FAILED,
                GRANT_PROCESSING,
                null
        ) > 0;
    }

    @Override
    public boolean markSuccess(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return false;
        }
        return userRewardRecordMapper.updateGrantStatusByMessageIdWithFromStatus(
                messageId,
                GRANT_PROCESSING,
                GRANT_SUCCESS,
                null
        ) > 0;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailedNewTx(String messageId, String errorMsg) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return false;
        }
        String reason = errorMsg;
        if (reason != null && reason.length() > 500) {
            reason = reason.substring(0, 500);
        }
        return userRewardRecordMapper.updateGrantStatusByMessageIdWithFromStatuses(
                messageId,
                GRANT_INIT,
                GRANT_PROCESSING,
                GRANT_FAILED,
                reason
        ) > 0;
    }

    @Override
    public Map<String, Object> reconcileSummary(int sampleLimit) {
        int limit = sampleLimit <= 0 ? 20 : sampleLimit;
        List<Map<String, Object>> pointDiffs = buildPointDiffs();
        List<Map<String, Object>> badgeDiffs = buildBadgeDiffSamples();
        List<Map<String, Object>> rewardTypeStatusStats = buildRewardTypeStatusStats();
        List<UserRewardRecord> abnormalSamples = userRewardRecordMapper.findAbnormalRecords(limit);
        enrichDisplayFields(abnormalSamples);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCount", userRewardRecordMapper.countByGrantStatus());
        result.put("withoutMessageId", userRewardRecordMapper.countWithoutMessageId());
        result.put("duplicateMessageIds", userRewardRecordMapper.findDuplicateMessageIds(limit));
        result.put("abnormalSamples", abnormalSamples);
        result.put("pointTotalDiff", calcPointTotalDiff(pointDiffs));
        result.put("pointDiffUsers", pointDiffs.size());
        result.put("badgeTotalDiff", badgeDiffs.size());
        result.put("itemPendingCount", countItemPendingClaims());
        result.put("failedGrantTotal", countFailedGrantTotal(rewardTypeStatusStats));
        result.put("rewardTypeStatusStats", rewardTypeStatusStats);
        result.put("grantStatusRef", java.util.Map.of(
                "0", "INIT",
                "1", "PROCESSING",
                "2", "SUCCESS",
                "3", "FAILED"
        ));
        return result;
    }

    @Override
    public Map<String, Object> previewReplayDiff(int sampleLimit) {
        int limit = sampleLimit <= 0 ? 20 : sampleLimit;
        List<Map<String, Object>> pointDiffs = buildPointDiffs();
        List<Map<String, Object>> badgeDiffs = buildBadgeDiffSamples();
        List<UserRewardRecord> failedRecords = listFailedGrantRecords();
        enrichDisplayFields(failedRecords);

        List<Map<String, Object>> failedSamples = failedRecords.stream()
                .limit(limit)
                .map(this::toFailedSample)
                .collect(Collectors.toList());

        List<Map<String, Object>> rewardTypeStatusStats = buildRewardTypeStatusStats();

        long totalDiffUsers = pointDiffs.size() + badgeDiffs.size() + failedRecords.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalDiffUsers", totalDiffUsers);
        result.put("sampleLimit", limit);
        result.put("pointTotalDiff", calcPointTotalDiff(pointDiffs));
        result.put("pointDiffUsers", pointDiffs.size());
        result.put("badgeTotalDiff", badgeDiffs.size());
        result.put("itemPendingCount", countItemPendingClaims());
        result.put("failedGrantTotal", failedRecords.size());
        result.put("rewardTypeStatusStats", rewardTypeStatusStats);
        result.put("pointSamples", pointDiffs.stream().limit(limit).collect(Collectors.toList()));
        result.put("badgeSamples", badgeDiffs.stream().limit(limit).collect(Collectors.toList()));
        result.put("failedSamples", failedSamples);
        // 兼容旧页面字段。
        result.put("samples", pointDiffs.stream().limit(limit).collect(Collectors.toList()));
        result.put("replayRule", "统一补偿：积分余额校准 + 徽章补发 + 失败发奖重试（积分/徽章/实物）");
        return result;
    }

    @Override
    public Map<String, Object> previewPointReplayDiff(int sampleLimit) {
        int limit = sampleLimit <= 0 ? 20 : sampleLimit;
        List<Map<String, Object>> diffs = buildPointDiffs();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalDiffUsers", diffs.size());
        result.put("sampleLimit", limit);
        result.put("samples", diffs.stream().limit(limit).collect(Collectors.toList()));
        result.put("replayRule", "基于成功日志重算积分：奖励发放(POINT/REWARD_POINT)+运营发放(OP_POINT)+消费预留(POINT_CONSUME)");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> executeReplayCompensation() {
        return executeReplayCompensation(null);
    }

    @Override
    @Transactional
    public Map<String, Object> executeReplayCompensation(String rewardType) {
        String executeScope = normalizeExecuteScope(rewardType);
        List<Map<String, Object>> allFailed = new ArrayList<>();

        int pointAdjustedUsers = 0;
        int badgeCompensatedUsers = 0;
        if ("ALL".equals(executeScope) || "POINT".equals(executeScope)) {
            pointAdjustedUsers = applyPointBalanceDiffs(buildPointDiffs(), allFailed);
        }
        if ("ALL".equals(executeScope) || "BADGE".equals(executeScope)) {
            badgeCompensatedUsers = applyBadgeCompensations(buildBadgeDiffSamples(), allFailed);
        }

        RetryExecutionResult retryResult = retryFailedGrantRecords(executeScope);
        allFailed.addAll(retryResult.failedSamples);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executedRewardType", executeScope);
        result.put("pointAdjustedUsers", pointAdjustedUsers);
        result.put("badgeCompensatedUsers", badgeCompensatedUsers);
        result.put("retriedFailedRecords", retryResult.successCount);
        result.put("failedRecords", allFailed.size());
        result.put("failedSamples", allFailed.stream().limit(20).collect(Collectors.toList()));
        // 兼容旧页面字段。
        result.put("updatedUsers", pointAdjustedUsers + badgeCompensatedUsers + retryResult.successCount);
        result.put("failedUsers", allFailed.size());
        result.put("rule", buildExecuteRule(executeScope));
        result.put("postCheck", previewReplayDiff(20));
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> executePointReplayCompensation() {
        List<Map<String, Object>> diffs = buildPointDiffs();
        List<Map<String, Object>> failed = new ArrayList<>();
        int updated = applyPointBalanceDiffs(diffs, failed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedUsers", updated);
        result.put("failedUsers", failed.size());
        result.put("failedSamples", failed.stream().limit(20).collect(Collectors.toList()));
        result.put("rule", "使用成功积分变更日志（奖励+运营+消费预留）全量重放覆盖用户积分余额");
        return result;
    }

    private List<Map<String, Object>> buildPointDiffs() {
        List<Map<String, Object>> expectedRows = userRewardRecordMapper.sumSuccessPointRewardsByUser();
        if (expectedRows == null || expectedRows.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIds = expectedRows.stream()
                .map(r -> parseLong(r.get("userId")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Integer> currentBalances = new HashMap<>();
        for (User u : userMapper.selectBatchIds(userIds)) {
            currentBalances.put(u.getId(), u.getPointBalance() == null ? 0 : u.getPointBalance());
        }

        List<Map<String, Object>> diffs = new ArrayList<>();
        for (Map<String, Object> row : expectedRows) {
            Long userId = parseLong(row.get("userId"));
            Integer expected = parseInt(row.get("expectedPoints"));
            if (userId == null || expected == null) {
                continue;
            }
            Integer current = currentBalances.get(userId);
            if (current == null || !current.equals(expected)) {
                Map<String, Object> diff = new LinkedHashMap<>();
                diff.put("userId", userId);
                diff.put("currentPoints", current);
                diff.put("expectedPoints", expected);
                diff.put("delta", (current == null ? 0 : current) - expected);
                diffs.add(diff);
            }
        }
        return diffs;
    }

    private List<Map<String, Object>> buildBadgeDiffSamples() {
        List<UserRewardRecord> successBadgeRecords = this.list(
                new QueryWrapper<UserRewardRecord>()
                        .in("grant_status", GRANT_SUCCESS)
                        .in("reward_type", BADGE_REWARD_TYPES)
                        .select("id", "user_id", "task_id", "reward_value", "create_time")
        );
        if (successBadgeRecords.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Integer> badgeCodes = successBadgeRecords.stream()
                .map(UserRewardRecord::getRewardValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (badgeCodes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, Long> badgeCodeToId = badgeMapper.selectList(
                new QueryWrapper<Badge>()
                        .in("code", badgeCodes)
                        .select("id", "code")
        ).stream().collect(Collectors.toMap(Badge::getCode, Badge::getId, (a, b) -> a));

        Set<Long> userIds = successBadgeRecords.stream()
                .map(UserRewardRecord::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> badgeIds = new java.util.HashSet<>(badgeCodeToId.values());

        Set<String> actualPairs = Collections.emptySet();
        if (!userIds.isEmpty() && !badgeIds.isEmpty()) {
            actualPairs = userBadgeMapper.selectList(
                    new QueryWrapper<UserBadge>()
                            .in("user_id", userIds)
                            .in("badge_id", badgeIds)
                            .select("user_id", "badge_id")
            ).stream().map(row -> row.getUserId() + "_" + row.getBadgeId()).collect(Collectors.toSet());
        }

        Map<Long, String> userNameMap = buildUserNameMap(userIds);
        Set<Long> taskIds = successBadgeRecords.stream()
                .map(UserRewardRecord::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> taskNameMap = buildTaskNameMap(taskIds);

        Map<String, Map<String, Object>> diffMap = new LinkedHashMap<>();
        for (UserRewardRecord row : successBadgeRecords) {
            Long userId = row.getUserId();
            Integer badgeCode = row.getRewardValue();
            if (userId == null || badgeCode == null) {
                continue;
            }
            Long badgeId = badgeCodeToId.get(badgeCode);
            String pairKey = userId + "_" + badgeCode;
            boolean missing;
            if (badgeId == null) {
                missing = true;
            } else {
                missing = !actualPairs.contains(userId + "_" + badgeId);
            }
            if (!missing || diffMap.containsKey(pairKey)) {
                continue;
            }

            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("userId", userId);
            sample.put("userName", userNameMap.get(userId));
            sample.put("taskId", row.getTaskId());
            sample.put("taskName", taskNameMap.get(row.getTaskId()));
            sample.put("badgeCode", badgeCode);
            sample.put("badgeId", badgeId);
            sample.put("recordId", row.getId());
            sample.put("createTime", row.getCreateTime());
            diffMap.put(pairKey, sample);
        }
        return new ArrayList<>(diffMap.values());
    }

    private List<Map<String, Object>> buildRewardTypeStatusStats() {
        List<Map<String, Object>> rawRows = this.baseMapper.selectMaps(
                new QueryWrapper<UserRewardRecord>()
                        .select("reward_type AS rewardType", "grant_status AS grantStatus", "COUNT(1) AS cnt")
                        .groupBy("reward_type", "grant_status")
        );

        Map<String, Map<String, Long>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRows) {
            String rewardType = normalizeRewardType(String.valueOf(row.get("rewardType")));
            Integer grantStatus = parseInt(row.get("grantStatus"));
            long cnt = parseLong(row.get("cnt")) == null ? 0L : parseLong(row.get("cnt"));
            Map<String, Long> statusMap = grouped.computeIfAbsent(rewardType, k -> new HashMap<>());
            statusMap.merge(String.valueOf(grantStatus == null ? -1 : grantStatus), cnt, Long::sum);
        }

        List<String> order = List.of("POINT", "BADGE", "ITEM", "UNKNOWN");
        List<Map<String, Object>> stats = new ArrayList<>();
        for (String type : order) {
            if (!grouped.containsKey(type)) {
                continue;
            }
            Map<String, Long> statusMap = grouped.get(type);
            long init = statusMap.getOrDefault("0", 0L);
            long processing = statusMap.getOrDefault("1", 0L);
            long success = statusMap.getOrDefault("2", 0L);
            long failed = statusMap.getOrDefault("3", 0L);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rewardType", type);
            item.put("initCount", init);
            item.put("processingCount", processing);
            item.put("successCount", success);
            item.put("failedCount", failed);
            item.put("abnormalCount", init + processing + failed);
            stats.add(item);
        }

        for (Map.Entry<String, Map<String, Long>> entry : grouped.entrySet()) {
            if (order.contains(entry.getKey())) {
                continue;
            }
            Map<String, Long> statusMap = entry.getValue();
            long init = statusMap.getOrDefault("0", 0L);
            long processing = statusMap.getOrDefault("1", 0L);
            long success = statusMap.getOrDefault("2", 0L);
            long failed = statusMap.getOrDefault("3", 0L);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rewardType", entry.getKey());
            item.put("initCount", init);
            item.put("processingCount", processing);
            item.put("successCount", success);
            item.put("failedCount", failed);
            item.put("abnormalCount", init + processing + failed);
            stats.add(item);
        }
        return stats;
    }

    private List<UserRewardRecord> listFailedGrantRecords() {
        return listFailedGrantRecords("ALL");
    }

    private List<UserRewardRecord> listFailedGrantRecords(String executeScope) {
        return this.list(
                new QueryWrapper<UserRewardRecord>()
                        .eq("grant_status", GRANT_FAILED)
                        .orderByAsc("create_time")
        ).stream().filter(record -> {
            if (record == null) {
                return false;
            }
            String normalizedType = normalizeRewardType(record.getRewardType());
            return shouldExecuteForType(executeScope, normalizedType);
        }).collect(Collectors.toList());
    }

    private int applyPointBalanceDiffs(List<Map<String, Object>> diffs, List<Map<String, Object>> failed) {
        int updated = 0;
        for (Map<String, Object> d : diffs) {
            Long userId = parseLong(d.get("userId"));
            Integer expected = parseInt(d.get("expectedPoints"));
            if (userId == null || expected == null) {
                continue;
            }
            try {
                int rows = userMapper.setUserPointBalance(userId, expected);
                if (rows > 0) {
                    updated++;
                } else {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("userId", userId);
                    f.put("error", "user not found");
                    failed.add(f);
                }
            } catch (Exception e) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("userId", userId);
                f.put("error", e.getMessage());
                failed.add(f);
            }
        }
        return updated;
    }

    private int applyBadgeCompensations(List<Map<String, Object>> badgeDiffs, List<Map<String, Object>> failed) {
        int success = 0;
        for (Map<String, Object> row : badgeDiffs) {
            Long userId = parseLong(row.get("userId"));
            Integer badgeCode = parseInt(row.get("badgeCode"));
            if (userId == null || badgeCode == null) {
                continue;
            }
            try {
                boolean ok = userBadgeService.grantBadge(userId, badgeCode);
                if (ok) {
                    success++;
                } else {
                    Map<String, Object> fail = new LinkedHashMap<>();
                    fail.put("userId", userId);
                    fail.put("rewardType", "BADGE");
                    fail.put("error", "badge grant failed");
                    failed.add(fail);
                }
            } catch (Exception e) {
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("userId", userId);
                fail.put("rewardType", "BADGE");
                fail.put("error", e.getMessage());
                failed.add(fail);
            }
        }
        return success;
    }

    private RetryExecutionResult retryFailedGrantRecords(String executeScope) {
        List<UserRewardRecord> failedRecords = listFailedGrantRecords(executeScope);
        RetryExecutionResult result = new RetryExecutionResult();
        for (UserRewardRecord record : failedRecords) {
            String rewardType = normalizeRewardType(record.getRewardType());
            try {
                boolean success = retrySingleFailedRecord(record, rewardType);
                if (!success) {
                    Map<String, Object> fail = toFailedSample(record);
                    fail.put("error", "retry returned false");
                    result.failedSamples.add(fail);
                    continue;
                }

                record.setGrantStatus(GRANT_SUCCESS);
                record.setErrorMsg(null);
                record.setUpdateTime(new Date());
                if ("ITEM".equals(rewardType) && record.getStatus() == null) {
                    record.setStatus(0);
                }
                if (!"ITEM".equals(rewardType) && (record.getStatus() == null || record.getStatus() == 0)) {
                    record.setStatus(1);
                }
                this.updateById(record);
                result.successCount++;
            } catch (Exception e) {
                Map<String, Object> fail = toFailedSample(record);
                fail.put("error", e.getMessage());
                result.failedSamples.add(fail);
            }
        }
        return result;
    }

    private boolean shouldExecuteForType(String executeScope, String rewardType) {
        if ("ALL".equals(executeScope)) {
            return true;
        }
        return executeScope.equals(rewardType);
    }

    private String normalizeExecuteScope(String rewardType) {
        if (rewardType == null || rewardType.trim().isEmpty()) {
            return "ALL";
        }
        String normalized = normalizeRewardType(rewardType);
        if ("POINT".equals(normalized) || "BADGE".equals(normalized) || "ITEM".equals(normalized)) {
            return normalized;
        }
        return "UNKNOWN";
    }

    private String buildExecuteRule(String executeScope) {
        if ("POINT".equals(executeScope)) {
            return "按奖励类型补偿：仅执行积分余额校准，并重试失败的积分发奖记录";
        }
        if ("BADGE".equals(executeScope)) {
            return "按奖励类型补偿：仅执行徽章补发，并重试失败的徽章发奖记录";
        }
        if ("ITEM".equals(executeScope)) {
            return "按奖励类型补偿：仅重试失败的实物发奖记录";
        }
        if ("UNKNOWN".equals(executeScope)) {
            return "按奖励类型补偿：请求奖励类型不支持，未执行任何补偿";
        }
        return "统一补偿：先执行失败发奖重试，再执行积分校准与徽章补发，覆盖积分/徽章/实物";
    }

    private boolean retrySingleFailedRecord(UserRewardRecord record, String rewardType) {
        if (record == null || record.getUserId() == null) {
            return false;
        }

        if ("POINT".equals(rewardType)) {
            if (record.getRewardValue() == null) {
                return false;
            }
            return userMapper.updateUserPoints(record.getUserId(), record.getRewardValue()) > 0;
        }
        if ("BADGE".equals(rewardType)) {
            if (record.getRewardValue() == null) {
                return false;
            }
            return userBadgeService.grantBadge(record.getUserId(), record.getRewardValue());
        }
        if ("ITEM".equals(rewardType)) {
            // 实物补偿在本地环境采用状态重试方式。
            return true;
        }
        return false;
    }

    private Map<String, Object> toFailedSample(UserRewardRecord record) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("recordId", record.getId());
        sample.put("userId", record.getUserId());
        sample.put("userName", record.getUserName());
        sample.put("taskId", record.getTaskId());
        sample.put("taskName", record.getTaskName());
        sample.put("rewardType", normalizeRewardType(record.getRewardType()));
        sample.put("rewardValue", record.getRewardValue());
        sample.put("grantStatus", record.getGrantStatus());
        sample.put("error", record.getErrorMsg());
        sample.put("createTime", record.getCreateTime());
        return sample;
    }

    private long calcPointTotalDiff(List<Map<String, Object>> diffs) {
        long total = 0L;
        for (Map<String, Object> row : diffs) {
            Integer delta = parseInt(row.get("delta"));
            if (delta != null) {
                total += Math.abs((long) delta);
            }
        }
        return total;
    }

    private long countItemPendingClaims() {
        return this.count(
                new QueryWrapper<UserRewardRecord>()
                        .in("reward_type", ITEM_REWARD_TYPES)
                        .eq("grant_status", GRANT_SUCCESS)
                        .eq("status", 0)
        );
    }

    private long countFailedGrantTotal(List<Map<String, Object>> rewardTypeStatusStats) {
        long total = 0L;
        for (Map<String, Object> row : rewardTypeStatusStats) {
            Long failed = parseLong(row.get("failedCount"));
            if (failed != null) {
                total += failed;
            }
        }
        return total;
    }

    private void enrichDisplayFields(List<UserRewardRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        Set<Long> userIds = records.stream()
                .map(UserRewardRecord::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> taskIds = records.stream()
                .map(UserRewardRecord::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> userNameMap = buildUserNameMap(userIds);
        Map<Long, String> taskNameMap = buildTaskNameMap(taskIds);

        for (UserRewardRecord record : records) {
            record.setUserName(userNameMap.get(record.getUserId()));
            record.setTaskName(taskNameMap.get(record.getTaskId()));
        }
    }

    private Map<Long, String> buildUserNameMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private Map<Long, String> buildTaskNameMap(Set<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return taskConfigService.getTaskConfigsByIds(taskIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    TaskConfig value = entry.getValue();
                    return value == null ? null : value.getTaskName();
                }));
    }

    private String normalizeRewardType(String rewardType) {
        if (rewardType == null || rewardType.trim().isEmpty()) {
            return "UNKNOWN";
        }
        String normalized = rewardType.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("POINT")) {
            return "POINT";
        }
        if (normalized.contains("BADGE")) {
            return "BADGE";
        }
        if (normalized.contains("ITEM") || normalized.contains("PHYSICAL")) {
            return "ITEM";
        }
        return normalized;
    }

    private Long parseLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static class RetryExecutionResult {
        int successCount;
        List<Map<String, Object>> failedSamples = new ArrayList<>();
    }
}
