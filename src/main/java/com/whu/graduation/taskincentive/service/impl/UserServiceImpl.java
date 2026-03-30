package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    private final UserMapper userMapper;
    private final UserTaskInstanceMapper userTaskInstanceMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public boolean save(User user) {
        user.setId(IdWorker.getId());
        return super.save(user);
    }

    @Override
    public boolean update(User user) {
        return super.updateById(user);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public User getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<User> listAll() {
        return super.list();
    }

    @Override
    public Page<User> selectPage(Page<User> page) {
        return this.baseMapper.selectPage(page, null);
    }

    @Override
    public User selectByUsername(String username) {
        return this.baseMapper.selectByUsername(username);
    }

    @Override
    public boolean updateUserPoints(Long userId, Integer points) {
        return this.baseMapper.updateUserPoints(userId, points) > 0;
    }

    @Override
    public boolean register(User user, String rawPassword, String roles) {
        if (user == null || user.getUsername() == null || rawPassword == null || rawPassword.trim().isEmpty()) {
            return false;
        }
        User exists = userMapper.selectByUsername(user.getUsername());
        if (exists != null) {
            return false;
        }
        user.setId(IdWorker.getId());
        user.setPassword(passwordEncoder.encode(rawPassword));
        if (user.getPointBalance() == null) {
            user.setPointBalance(0);
        }
        String normalizedRole = (roles == null || roles.trim().isEmpty()) ? "ROLE_USER" : roles.trim();
        if (!normalizedRole.startsWith("ROLE_")) {
            normalizedRole = "ROLE_" + normalizedRole;
        }
        user.setRoles(normalizedRole);
        return super.save(user);
    }

    @Override
    public User authenticate(String username, String rawPassword) {
        User u = userMapper.selectByUsername(username);
        if (u == null) return null;
        if (passwordEncoder.matches(rawPassword, u.getPassword())) {
            // 返回不带密码的用户对象
            User safe = new User();
            safe.setId(u.getId());
            safe.setUsername(u.getUsername());
            safe.setRoles(u.getRoles());
            safe.setPointBalance(u.getPointBalance());
            safe.setCreateTime(u.getCreateTime());
            safe.setUpdateTime(u.getUpdateTime());
            return safe;
        }
        return null;
    }

    @Override
    public long countAllUsers() {
        return userMapper.countAllUsers();
    }

    @Override
    public long countActiveUsersSince(Date since) {
        if (since == null) return 0L;
        return userTaskInstanceMapper.countDistinctUsersSince(since);
    }

    @Override
    public long countUsersToday() {
        return userTaskInstanceMapper.countDistinctUsersToday();
    }

    @Override
    public List<Long> getUserCountLast7Days() {
        // 计算区间：从 6 天前 00:00:00 到 明天 00:00:00（不含），一次性按天统计新增用户数，然后做前缀和得到累计数
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -6);
        Date start = cal.getTime();

        // end 为明天 00:00
        Calendar endCal = Calendar.getInstance();
        endCal.set(Calendar.HOUR_OF_DAY, 0);
        endCal.set(Calendar.MINUTE, 0);
        endCal.set(Calendar.SECOND, 0);
        endCal.set(Calendar.MILLISECOND, 0);
        endCal.add(Calendar.DAY_OF_MONTH, 1);
        Date end = endCal.getTime();

        // 一次性按天统计新增用户数（DATE(create_time)）
        List<Map<String, Object>> rows = userMapper.countUsersGroupByDate(start, end);
        Map<String, Long> dailyNew = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (Map<String, Object> r : rows) {
            Object d = r.get("the_date");
            Object c = r.get("cnt");
            if (d == null) continue;
            String ds = d.toString();
            long cnt = 0L;
            if (c instanceof Number) cnt = ((Number) c).longValue();
            else try { cnt = Long.parseLong(String.valueOf(c)); } catch (Exception ignored) {}
            dailyNew.put(ds, cnt);
        }

        // 基础累计数：start 之前的用户总数（create_time < start）
        long base = userMapper.countUsersBefore(start);

        // 构造从 start 到 todayStart 的每天新增数组，并做前缀和得到累计数
        List<Long> result = new ArrayList<>();
        long running = base;
        Calendar iter = Calendar.getInstance();
        iter.setTime(start);
        while (!iter.getTime().after(todayStart)) {
            String key = sdf.format(iter.getTime());
            long newUsers = dailyNew.getOrDefault(key, 0L);
            running += newUsers;
            result.add(running);
            iter.add(Calendar.DAY_OF_MONTH, 1);
        }
        return result;
    }

    @Override
    public List<Long> getActiveUserCountLast7Days() {
        // 优化：一次查询范围内所有记录的日期和user_id，然后在内存构建每天的用户集合，再用滑动窗口并集计算每个目标日的活跃用户数
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime(); // 今天 00:00
        cal.add(Calendar.DAY_OF_MONTH, -6);
        Date firstDay = cal.getTime(); // 6 天前 00:00 (第一个目标日)

        // 最小数据起点：firstDay - 6 days (因为第一个目标日的窗口要向前6天)
        Calendar minCal = Calendar.getInstance();
        minCal.setTime(firstDay);
        minCal.add(Calendar.DAY_OF_MONTH, -6);
        Date dataStart = minCal.getTime();

        // 最大数据终点：today + 1 day 00:00
        Calendar endCal = Calendar.getInstance();
        endCal.setTime(todayStart);
        endCal.add(Calendar.DAY_OF_MONTH, 1);
        Date dataEnd = endCal.getTime();

        // 一次性拉取这段时间内每条记录的日期和 user_id
        List<Map<String, Object>> rows = userTaskInstanceMapper.selectUserIdsByDate(dataStart, dataEnd);

        // 把 rows 按日期分组，map key 为 yyyy-MM-dd 字符串
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

        // 对每个目标日 D，从 D-6 到 D（包含）做并集
        List<Long> result = new ArrayList<>();
        Calendar iter = Calendar.getInstance();
        iter.setTime(firstDay);
        while (!iter.getTime().after(todayStart)) {
            // window days: from D-6 .. D
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

    @Override
    public List<Long> getTaskReceiveUserCountLast7Days() {
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
        // 查询：SELECT DATE(create_time) as the_date, COUNT(DISTINCT user_id) as cnt FROM user_task_instance WHERE create_time >= ? AND create_time < ? GROUP BY DATE(create_time) ORDER BY DATE(create_time) ASC
        List<Map<String, Object>> rows = userTaskInstanceMapper.countDistinctUserIdsGroupByDate(firstDay, end);
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
}
