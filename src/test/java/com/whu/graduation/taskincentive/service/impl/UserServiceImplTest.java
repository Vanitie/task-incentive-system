package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
