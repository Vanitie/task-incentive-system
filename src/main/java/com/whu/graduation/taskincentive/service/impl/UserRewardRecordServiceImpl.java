package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.service.TaskConfigService;
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

    private final UserRewardRecordMapper userRewardRecordMapper;
    private final UserMapper userMapper;
    private final TaskConfigService taskConfigService;

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

        List<UserRewardRecord> records = result.getRecords();
        if (records == null || records.isEmpty()) {
            return result;
        }

        // 批量查询用户名，避免 N+1
        Set<Long> userIds = records.stream()
                .map(UserRewardRecord::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userNameMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            userNameMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        }

        // 批量查询任务名，复用任务配置服务的批量接口
        Set<Long> taskIds = records.stream()
                .map(UserRewardRecord::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, TaskConfig> taskConfigMap = taskIds.isEmpty()
                ? Collections.emptyMap()
                : taskConfigService.getTaskConfigsByIds(taskIds);

        for (UserRewardRecord record : records) {
            record.setUserName(userNameMap.get(record.getUserId()));
            TaskConfig taskConfig = taskConfigMap.get(record.getTaskId());
            record.setTaskName(taskConfig == null ? null : taskConfig.getTaskName());
        }
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
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("statusCount", userRewardRecordMapper.countByGrantStatus());
        result.put("withoutMessageId", userRewardRecordMapper.countWithoutMessageId());
        result.put("duplicateMessageIds", userRewardRecordMapper.findDuplicateMessageIds(limit));
        result.put("abnormalSamples", userRewardRecordMapper.findAbnormalRecords(limit));
        result.put("grantStatusRef", java.util.Map.of(
                "0", "INIT",
                "1", "PROCESSING",
                "2", "SUCCESS",
                "3", "FAILED"
        ));
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
    public Map<String, Object> executePointReplayCompensation() {
        List<Map<String, Object>> diffs = buildPointDiffs();
        int updated = 0;
        List<Map<String, Object>> failed = new ArrayList<>();

        for (Map<String, Object> d : diffs) {
            Long userId = (Long) d.get("userId");
            Integer expected = (Integer) d.get("expectedPoints");
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
}
