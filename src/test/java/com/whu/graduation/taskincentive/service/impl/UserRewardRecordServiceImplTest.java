package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class UserRewardRecordServiceImplTest {

    private UserRewardRecordMapper userRewardRecordMapper;
    private UserRewardRecordServiceImpl service;

    @BeforeEach
    public void setUp() {
        userRewardRecordMapper = Mockito.mock(UserRewardRecordMapper.class);
        service = new UserRewardRecordServiceImpl(userRewardRecordMapper);
    }

    @Test
    public void testGetReceivedUsersLast7Days_slidingUnion() throws Exception {
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

        Map<String, Set<Long>> dateToUsers = new HashMap<>();
        Calendar tmp = Calendar.getInstance(); tmp.setTime(dataStart);
        for (int i = 0; i < 13; i++) {
            dateToUsers.put(sdf.format(tmp.getTime()), new HashSet<>());
            tmp.add(Calendar.DAY_OF_MONTH, 1);
        }
        // populate some users
        Calendar d1 = Calendar.getInstance(); d1.setTime(dataStart); d1.add(Calendar.DAY_OF_MONTH, 2);
        dateToUsers.get(sdf.format(d1.getTime())).addAll(Arrays.asList(10L,11L));
        Calendar d4 = Calendar.getInstance(); d4.setTime(dataStart); d4.add(Calendar.DAY_OF_MONTH, 4);
        dateToUsers.get(sdf.format(d4.getTime())).addAll(Arrays.asList(11L,12L));
        Calendar d9 = Calendar.getInstance(); d9.setTime(dataStart); d9.add(Calendar.DAY_OF_MONTH, 9);
        dateToUsers.get(sdf.format(d9.getTime())).add(13L);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Set<Long>> e : dateToUsers.entrySet()) {
            for (Long uid : e.getValue()) {
                Map<String, Object> m = new HashMap<>();
                m.put("the_date", e.getKey());
                m.put("user_id", uid);
                rows.add(m);
            }
        }

        when(userRewardRecordMapper.selectUserIdsByDate(dataStart, dataEnd)).thenReturn(rows);

        List<Long> res = service.getReceivedUsersLast7Days();

        // expected sliding union
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
