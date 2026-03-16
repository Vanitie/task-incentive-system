package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户奖励记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRewardRecordServiceImpl extends ServiceImpl<UserRewardRecordMapper, UserRewardRecord>
        implements UserRewardRecordService {

    private final UserRewardRecordMapper userRewardRecordMapper;

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
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -6);
        Date firstDay = cal.getTime();

        // data start = firstDay -6 days
        Calendar minCal = Calendar.getInstance();
        minCal.setTime(firstDay);
        minCal.add(Calendar.DAY_OF_MONTH, -6);
        Date dataStart = minCal.getTime();
        // data end = today + 1
        Calendar endCal = Calendar.getInstance();
        endCal.setTime(todayStart);
        endCal.add(Calendar.DAY_OF_MONTH, 1);
        Date dataEnd = endCal.getTime();

        List<Map<String, Object>> rows = userRewardRecordMapper.selectUserIdsByDate(dataStart, dataEnd);
        Map<String, Set<Long>> dateToUsers = new HashMap<>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        for (Map<String, Object> r : rows) {
            Object d = r.get("the_date");
            Object uid = r.get("user_id");
            if (d == null || uid == null) continue;
            String ds = d.toString();
            Long userId = null;
            if (uid instanceof Number) userId = ((Number) uid).longValue();
            else try { userId = Long.parseLong(uid.toString()); } catch (Exception ignored){}
            if (userId == null) continue;
            dateToUsers.computeIfAbsent(ds, k -> new HashSet<>()).add(userId);
        }

        List<Long> result = new ArrayList<>();
        Calendar iter = Calendar.getInstance();
        iter.setTime(firstDay);
        while (!iter.getTime().after(todayStart)) {
            Set<Long> union = new HashSet<>();
            Calendar scan = (Calendar) iter.clone();
            scan.add(Calendar.DAY_OF_MONTH, -6);
            while (!scan.getTime().after(iter.getTime())) {
                String key = sdf.format(scan.getTime());
                Set<Long> s = dateToUsers.get(key);
                if (s != null && !s.isEmpty()) union.addAll(s);
                scan.add(Calendar.DAY_OF_MONTH, 1);
            }
            result.add((long) union.size());
            iter.add(Calendar.DAY_OF_MONTH, 1);
        }
        return result;
    }
}
