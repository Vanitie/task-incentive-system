package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserServiceImplTest {

    private UserMapper userMapper;
    private UserTaskInstanceMapper userTaskInstanceMapper;
    private UserServiceImpl userService;

    @BeforeEach
    public void setUp() {
        userMapper = Mockito.mock(UserMapper.class);
        userTaskInstanceMapper = Mockito.mock(UserTaskInstanceMapper.class);
        userService = new UserServiceImpl(userMapper, userTaskInstanceMapper);
        try {
            java.lang.reflect.Field fBase = ServiceImpl.class.getDeclaredField("baseMapper");
            fBase.setAccessible(true);
            fBase.set(userService, userMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void register_shouldReturnFalse_whenInputInvalidOrUserExists() {
        assertFalse(userService.register(new User(), " ", "USER"));

        User exists = new User();
        exists.setUsername("u1");
        when(userMapper.selectByUsername("u1")).thenReturn(exists);
        User u = new User();
        u.setUsername("u1");
        assertFalse(userService.register(u, "p", "USER"));
    }

    @Test
    public void register_shouldNormalizeRoleAndHashPassword() {
        when(userMapper.selectByUsername("u2")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenReturn(1);
        User u = new User();
        u.setUsername("u2");

        boolean ok = userService.register(u, "raw-pass", "ADMIN");

        assertTrue(ok);
        verify(userMapper).insert(any(User.class));
        assertNotNull(u.getId());
        assertEquals("ROLE_ADMIN", u.getRoles());
        assertEquals(0, u.getPointBalance());
        assertNotEquals("raw-pass", u.getPassword());
    }

    @Test
    public void register_shouldUseDefaultRole_whenRolesBlank() {
        when(userMapper.selectByUsername("u3")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenReturn(1);
        User u = new User();
        u.setUsername("u3");

        boolean ok = userService.register(u, "p3", " ");

        assertTrue(ok);
        assertEquals("ROLE_USER", u.getRoles());
    }

    @Test
    public void register_shouldKeepRolePrefix_andKeepPointBalanceWhenProvided() {
        when(userMapper.selectByUsername("u4")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenReturn(1);
        User u = new User();
        u.setUsername("u4");
        u.setPointBalance(88);

        boolean ok = userService.register(u, "p4", "ROLE_ADMIN");

        assertTrue(ok);
        assertEquals("ROLE_ADMIN", u.getRoles());
        assertEquals(88, u.getPointBalance());
    }

    @Test
    public void authenticate_shouldReturnNullWhenMissingOrPasswordMismatch() {
        when(userMapper.selectByUsername("no-user")).thenReturn(null);
        assertNull(userService.authenticate("no-user", "x"));

        User u = new User();
        u.setId(1L);
        u.setUsername("u1");
        u.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("correct"));
        when(userMapper.selectByUsername("u1")).thenReturn(u);
        assertNull(userService.authenticate("u1", "wrong"));
    }

    @Test
    public void authenticate_shouldReturnSafeUser_whenPasswordMatches() {
        User u = new User();
        u.setId(9L);
        u.setUsername("safe");
        u.setRoles("ROLE_USER");
        u.setPointBalance(7);
        u.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("ok"));
        when(userMapper.selectByUsername("safe")).thenReturn(u);

        User out = userService.authenticate("safe", "ok");

        assertNotNull(out);
        assertEquals(9L, out.getId());
        assertEquals("safe", out.getUsername());
        assertNull(out.getPassword());
    }

    @Test
    public void countActiveUsersSince_shouldReturnZeroWhenNull() {
        assertEquals(0L, userService.countActiveUsersSince(null));
    }

    @Test
    public void countActiveUsersSince_shouldDelegate_whenNonNull() {
        Date since = new Date();
        when(userTaskInstanceMapper.countDistinctUsersSince(since)).thenReturn(12L);

        long out = userService.countActiveUsersSince(since);

        assertEquals(12L, out);
    }

    @Test
    public void counters_shouldDelegateToMapper() {
        when(userMapper.countAllUsers()).thenReturn(123L);
        when(userTaskInstanceMapper.countDistinctUsersToday()).thenReturn(11L);

        assertEquals(123L, userService.countAllUsers());
        assertEquals(11L, userService.countUsersToday());
    }

    @Test
    public void updateUserPoints_shouldReflectAffectedRows() {
        when(userMapper.updateUserPoints(100L, 20)).thenReturn(1);
        when(userMapper.updateUserPoints(101L, 20)).thenReturn(0);

        assertTrue(userService.updateUserPoints(100L, 20));
        assertFalse(userService.updateUserPoints(101L, 20));
    }

    @Test
    public void getTaskReceiveUserCountLast7Days_shouldTreatNonNumericCntAsZero() {
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, Object> row = new HashMap<>();
        row.put("the_date", sdf.format(firstDay));
        row.put("cnt", "bad-number");
        when(userTaskInstanceMapper.countDistinctUserIdsGroupByDate(firstDay, end)).thenReturn(List.of(row));

        List<Long> out = userService.getTaskReceiveUserCountLast7Days();

        assertEquals(7, out.size());
        assertEquals(0L, out.get(0));
    }

    @Test
    public void getTaskReceiveUserCountLast7Days_shouldHandleNullDateAndStringNumber() {
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, Object> row0 = new HashMap<>();
        row0.put("the_date", null); // 覆盖 d == null 分支
        row0.put("cnt", 9);

        Map<String, Object> row1 = new HashMap<>();
        row1.put("the_date", sdf.format(firstDay));
        row1.put("cnt", "12"); // 覆盖字符串可解析分支

        when(userTaskInstanceMapper.countDistinctUserIdsGroupByDate(firstDay, end)).thenReturn(List.of(row1, row0));

        List<Long> out = userService.getTaskReceiveUserCountLast7Days();

        assertEquals(7, out.size());
        assertEquals(12L, out.get(0));
    }

    @Test
    public void testGetUserCountLast7Days_prefixSumWithBase() {
        // replicate date calculations from service
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, -6);
        Date start = cal.getTime();

        // end = tomorrow 00:00
        Calendar endCal = Calendar.getInstance();
        endCal.set(Calendar.HOUR_OF_DAY, 0);
        endCal.set(Calendar.MINUTE, 0);
        endCal.set(Calendar.SECOND, 0);
        endCal.set(Calendar.MILLISECOND, 0);
        endCal.add(Calendar.DAY_OF_MONTH, 1);
        Date end = endCal.getTime();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        // prepare mocked daily new users: only some days have new users
        Map<String, Long> dailyNew = new HashMap<>();
        // day offsets from start: 0..6
        dailyNew.put(sdf.format(start), 2L); // day0
        Calendar c2 = Calendar.getInstance(); c2.setTime(start); c2.add(Calendar.DAY_OF_MONTH, 2);
        dailyNew.put(sdf.format(c2.getTime()), 3L); // day2
        Calendar c6 = Calendar.getInstance(); c6.setTime(start); c6.add(Calendar.DAY_OF_MONTH, 6);
        dailyNew.put(sdf.format(c6.getTime()), 5L); // day6

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : dailyNew.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("the_date", e.getKey());
            m.put("cnt", e.getValue());
            rows.add(m);
        }

        // mock mapper
        when(userMapper.countUsersGroupByDate(start, end)).thenReturn(rows);
        long base = 100L;
        when(userMapper.countUsersBefore(start)).thenReturn(base);

        List<Long> res = userService.getUserCountLast7Days();

        // expected: cumulative from base + dailyNew prefix sums
        List<Long> expected = new ArrayList<>();
        long running = base;
        for (int i = 0; i < 7; i++) {
            Calendar d = Calendar.getInstance(); d.setTime(start); d.add(Calendar.DAY_OF_MONTH, i);
            String key = sdf.format(d.getTime());
            running += dailyNew.getOrDefault(key, 0L);
            expected.add(running);
        }

        assertEquals(expected, res);
    }

    @Test
    public void testGetActiveUserCountLast7Days_slidingUnion() {
        // compute firstDay and data window like service
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -6);
        Date firstDay = cal.getTime();

        Calendar minCal = Calendar.getInstance();
        minCal.setTime(firstDay);
        minCal.add(Calendar.DAY_OF_MONTH, -6);
        Date dataStart = minCal.getTime();

        Calendar endCal = Calendar.getInstance();
        endCal.setTime(todayStart);
        endCal.add(Calendar.DAY_OF_MONTH, 1);
        Date dataEnd = endCal.getTime();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        // prepare mocked rows: date -> user ids
        // We'll setup a simple scenario where users across days overlap
        // date strings from dataStart to dataEnd-1
        Map<String, Set<Long>> dateToUsers = new HashMap<>();
        // helper to add user ids to a date
        Calendar tmp = Calendar.getInstance(); tmp.setTime(dataStart);
        for (int i = 0; i < 13; i++) { // 13 days roughly (6+7)
            String key = sdf.format(tmp.getTime());
            dateToUsers.put(key, new HashSet<>());
            tmp.add(Calendar.DAY_OF_MONTH, 1);
        }
        // assign some users
        // at date = dataStart + 1 -> users 1,2
        Calendar d1 = Calendar.getInstance(); d1.setTime(dataStart); d1.add(Calendar.DAY_OF_MONTH, 1);
        dateToUsers.get(sdf.format(d1.getTime())).addAll(Arrays.asList(1L,2L));
        // at date = dataStart + 3 -> users 2,3
        Calendar d3 = Calendar.getInstance(); d3.setTime(dataStart); d3.add(Calendar.DAY_OF_MONTH, 3);
        dateToUsers.get(sdf.format(d3.getTime())).addAll(Arrays.asList(2L,3L));
        // at date = dataStart + 7 -> user 4
        Calendar d7 = Calendar.getInstance(); d7.setTime(dataStart); d7.add(Calendar.DAY_OF_MONTH, 7);
        dateToUsers.get(sdf.format(d7.getTime())).add(4L);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Set<Long>> e : dateToUsers.entrySet()) {
            String date = e.getKey();
            for (Long uid : e.getValue()) {
                Map<String, Object> m = new HashMap<>();
                m.put("the_date", date);
                m.put("user_id", uid);
                rows.add(m);
            }
        }

        when(userTaskInstanceMapper.selectUserIdsByDate(dataStart, dataEnd)).thenReturn(rows);

        List<Long> res = userService.getActiveUserCountLast7Days();

        // compute expected: for each target day D from firstDay..todayStart, union of D-6..D
        List<Long> expected = new ArrayList<>();
        Calendar iter = Calendar.getInstance(); iter.setTime(firstDay);
        while (!iter.getTime().after(todayStart)) {
            Set<Long> union = new HashSet<>();
            Calendar scan = (Calendar) iter.clone(); scan.add(Calendar.DAY_OF_MONTH, -6);
            while (!scan.getTime().after(iter.getTime())) {
                String key = sdf.format(scan.getTime());
                Set<Long> s = dateToUsers.get(key);
                if (s != null) union.addAll(s);
                scan.add(Calendar.DAY_OF_MONTH, 1);
            }
            expected.add((long) union.size());
            iter.add(Calendar.DAY_OF_MONTH, 1);
        }

        assertEquals(expected, res);
    }

    @Test
    public void getActiveUserCountLast7Days_shouldIgnoreInvalidUserId() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -6);
        Date firstDay = cal.getTime();
        Calendar minCal = Calendar.getInstance();
        minCal.setTime(firstDay);
        minCal.add(Calendar.DAY_OF_MONTH, -6);
        Date dataStart = minCal.getTime();
        Calendar endCal = Calendar.getInstance();
        endCal.setTime(todayStart);
        endCal.add(Calendar.DAY_OF_MONTH, 1);
        Date dataEnd = endCal.getTime();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, Object> row = new HashMap<>();
        row.put("the_date", null); // 覆盖 d == null 分支
        row.put("user_id", "not-a-number");
        when(userTaskInstanceMapper.selectUserIdsByDate(dataStart, dataEnd)).thenReturn(List.of(row));

        List<Long> out = userService.getActiveUserCountLast7Days();

        assertEquals(7, out.size());
        assertTrue(out.stream().allMatch(v -> v == 0L));
    }

    @Test
    public void getActiveUserCountLast7Days_shouldIgnoreRowsWithNullDateOrUserId() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -6);
        Date firstDay = cal.getTime();
        Calendar minCal = Calendar.getInstance();
        minCal.setTime(firstDay);
        minCal.add(Calendar.DAY_OF_MONTH, -6);
        Date dataStart = minCal.getTime();
        Calendar endCal = Calendar.getInstance();
        endCal.setTime(todayStart);
        endCal.add(Calendar.DAY_OF_MONTH, 1);
        Date dataEnd = endCal.getTime();

        Map<String, Object> r1 = new HashMap<>();
        r1.put("the_date", null);
        r1.put("user_id", 1L);
        Map<String, Object> r2 = new HashMap<>();
        r2.put("the_date", "2026-01-01");
        r2.put("user_id", null);
        Map<String, Object> r3 = new HashMap<>();
        r3.put("the_date", "2026-01-01");
        r3.put("user_id", "7");
        when(userTaskInstanceMapper.selectUserIdsByDate(dataStart, dataEnd)).thenReturn(List.of(r1, r2, r3));

        List<Long> out = userService.getActiveUserCountLast7Days();

        assertEquals(7, out.size());
        assertTrue(out.stream().allMatch(v -> v >= 0L));
    }

    @Test
    public void delegateCrudMethods_shouldCoverServiceImplBranches() {
        User u = new User();
        u.setId(9001L);
        u.setUsername("u9001");

        when(userMapper.insert(any(User.class))).thenReturn(1);
        assertTrue(userService.save(u));

        when(userMapper.updateById(any(User.class))).thenReturn(1);
        assertTrue(userService.update(u));

        when(userMapper.selectById(9001L)).thenReturn(u);
        assertSame(u, userService.getById(9001L));

        when(userMapper.selectList(any())).thenReturn(List.of(u));
        assertEquals(1, userService.listAll().size());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<User> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        when(userMapper.selectPage(eq(page), isNull())).thenReturn(page);
        assertSame(page, userService.selectPage(page));

        when(userMapper.selectByUsername("u9001")).thenReturn(u);
        assertSame(u, userService.selectByUsername("u9001"));
    }
}
