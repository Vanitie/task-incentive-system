package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class UserRewardRecordServiceImplTest {

    private UserRewardRecordMapper userRewardRecordMapper;
    private UserMapper userMapper;
    private TaskConfigService taskConfigService;
    private UserRewardRecordServiceImpl service;

    @BeforeEach
    public void setUp() {
        userRewardRecordMapper = Mockito.mock(UserRewardRecordMapper.class);
        userMapper = Mockito.mock(UserMapper.class);
        taskConfigService = Mockito.mock(TaskConfigService.class);
        service = new UserRewardRecordServiceImpl(userRewardRecordMapper, userMapper, taskConfigService);
    }

    @Test
    public void testGetReceivedUsersLast7Days_dailyDistinctCounts() {
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

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> d0 = new HashMap<>();
        d0.put("the_date", sdf.format(firstDay));
        d0.put("cnt", 2);
        rows.add(d0);

        Calendar day2 = Calendar.getInstance();
        day2.setTime(firstDay);
        day2.add(Calendar.DAY_OF_MONTH, 2);
        Map<String, Object> d2 = new HashMap<>();
        d2.put("the_date", sdf.format(day2.getTime()));
        d2.put("cnt", 5);
        rows.add(d2);

        when(userRewardRecordMapper.countDistinctUserIdsGroupByDate(firstDay, end)).thenReturn(rows);

        List<Long> res = service.getReceivedUsersLast7Days();

        List<Long> expected = Arrays.asList(2L, 0L, 5L, 0L, 0L, 0L, 0L);

        assertEquals(expected, res);
    }
}
