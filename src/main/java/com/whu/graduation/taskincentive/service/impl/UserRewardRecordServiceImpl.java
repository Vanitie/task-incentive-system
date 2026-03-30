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
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户奖励记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRewardRecordServiceImpl extends ServiceImpl<UserRewardRecordMapper, UserRewardRecord>
        implements UserRewardRecordService {

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
}
