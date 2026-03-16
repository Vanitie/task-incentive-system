package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.dto.BarChartData;
import com.whu.graduation.taskincentive.dto.DailyStatItem;
import com.whu.graduation.taskincentive.dto.ProgressDataItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class StatsServiceImplTest {

    private UserMapper userMapper;
    private UserTaskInstanceMapper userTaskInstanceMapper;
    private UserRewardRecordMapper userRewardRecordMapper;
    private StatsServiceImpl statsService;

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");

    @BeforeEach
    public void setUp() {
        userMapper = Mockito.mock(UserMapper.class);
        userTaskInstanceMapper = Mockito.mock(UserTaskInstanceMapper.class);
        userRewardRecordMapper = Mockito.mock(UserRewardRecordMapper.class);
        statsService = new StatsServiceImpl(userMapper, userTaskInstanceMapper, userRewardRecordMapper);
    }

    @Test
    public void testGetTwoWeeksTaskReceiveAndComplete_groupByAggregation() {
        // prepare date range like service
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int offsetToMon = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        Calendar thisMon = (Calendar) cal.clone(); thisMon.add(Calendar.DAY_OF_MONTH, offsetToMon);
        Calendar lastMon = (Calendar) thisMon.clone(); lastMon.add(Calendar.DAY_OF_MONTH, -7);

        Calendar iter = (Calendar) lastMon.clone();
        List<Map<String,Object>> recvRows = new ArrayList<>();
        List<Map<String,Object>> compRows = new ArrayList<>();
        // create 14 days data: recv = 10+i, comp = 5+i
        for (int i = 0; i < 14; i++) {
            Map<String,Object> r1 = new HashMap<>();
            r1.put("the_date", SDF.format(iter.getTime()));
            r1.put("cnt", 10L + i);
            recvRows.add(r1);
            Map<String,Object> r2 = new HashMap<>();
            r2.put("the_date", SDF.format(iter.getTime()));
            r2.put("cnt", 5L + i);
            compRows.add(r2);
            iter.add(Calendar.DAY_OF_MONTH, 1);
        }

        Date rangeStart = lastMon.getTime();
        Calendar tmp = (Calendar) thisMon.clone(); tmp.add(Calendar.DAY_OF_MONTH, 7);
        Date rangeEnd = tmp.getTime();

        when(userTaskInstanceMapper.countTasksGroupByDate(rangeStart, rangeEnd)).thenReturn(recvRows);
        when(userTaskInstanceMapper.countTasksByStatusGroupByDate(rangeStart, rangeEnd, 1)).thenReturn(compRows);

        List<BarChartData> res = statsService.getTwoWeeksTaskReceiveAndComplete();
        assertNotNull(res);
        assertEquals(2, res.size());
        BarChartData thisWeek = res.get(0);
        BarChartData lastWeek = res.get(1);
        // lastWeek should correspond to first 7 days (10..16), thisWeek to next 7 (17..23)
        assertEquals(7, thisWeek.getTaskReceived().size());
        assertEquals(7, lastWeek.getTaskReceived().size());
        for (int i = 0; i < 7; i++) {
            // thisWeek: days 7..13 -> values 17..23
            assertEquals(10L + 7 + i, thisWeek.getTaskReceived().get(i));
            assertEquals(5L + 7 + i, thisWeek.getTaskCompleted().get(i));
            // lastWeek: days 0..6 -> values 10..16
            assertEquals(10L + i, lastWeek.getTaskReceived().get(i));
            assertEquals(5L + i, lastWeek.getTaskCompleted().get(i));
        }
    }

    @Test
    public void testGetThisWeekCompletionPercent_computationAndColor() {
        // build one-week data
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int offsetToMon = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        Calendar thisMon = (Calendar) cal.clone(); thisMon.add(Calendar.DAY_OF_MONTH, offsetToMon);

        Calendar endCal = (Calendar) thisMon.clone(); endCal.add(Calendar.DAY_OF_MONTH, 7);
        Date start = thisMon.getTime(); Date end = endCal.getTime();

        List<Map<String,Object>> recvRows = new ArrayList<>();
        List<Map<String,Object>> compRows = new ArrayList<>();
        Calendar iter = (Calendar) thisMon.clone();
        // set totals: 10,20,0,5,5,10,15 and comps: 5,10,0,5,2,5,15
        long[] totals = {10,20,0,5,5,10,15};
        long[] comps =  {5,10,0,5,2,5,15};
        for (int i = 0; i < 7; i++) {
            Map<String,Object> r1 = new HashMap<>(); r1.put("the_date", SDF.format(iter.getTime())); r1.put("cnt", totals[i]); recvRows.add(r1);
            Map<String,Object> r2 = new HashMap<>(); r2.put("the_date", SDF.format(iter.getTime())); r2.put("cnt", comps[i]); compRows.add(r2);
            iter.add(Calendar.DAY_OF_MONTH, 1);
        }
        when(userTaskInstanceMapper.countTasksGroupByDate(start, end)).thenReturn(recvRows);
        when(userTaskInstanceMapper.countTasksByStatusGroupByDate(start, end, 1)).thenReturn(compRows);

        List<ProgressDataItem> items = statsService.getThisWeekCompletionPercent();
        assertNotNull(items); assertEquals(7, items.size());
        for (int i = 0; i < 7; i++) {
            ProgressDataItem it = items.get(i);
            int expectedPercent = 0;
            if (totals[i] > 0) expectedPercent = (int) Math.round((double) comps[i] * 100.0 / (double) totals[i]);
            assertEquals(expectedPercent, it.getPercentage());
            // color should be hex string of length 7 starting with '#'
            assertNotNull(it.getColor());
            assertTrue(it.getColor().startsWith("#") && it.getColor().length() == 7);
        }
    }

    @Test
    public void testPagedDailyStats_paginationAndValues() {
        // prepare 30 days of data ending today
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        int days = 30;
        Calendar earliest = (Calendar) cal.clone(); earliest.add(Calendar.DAY_OF_MONTH, -(days-1));
        Date rangeStart = earliest.getTime();
        Calendar rangeEndCal = (Calendar) cal.clone(); rangeEndCal.add(Calendar.DAY_OF_MONTH, 1);
        Date rangeEnd = rangeEndCal.getTime();

        List<Map<String,Object>> newUserRows = new ArrayList<>();
        List<Map<String,Object>> activeRows = new ArrayList<>();
        List<Map<String,Object>> recvRows = new ArrayList<>();
        List<Map<String,Object>> compRows = new ArrayList<>();

        Calendar iter = (Calendar) earliest.clone();
        for (int i = 0; i < days; i++) {
            String key = SDF.format(iter.getTime());
            Map<String,Object> a = new HashMap<>(); a.put("the_date", key); a.put("cnt", 1L); newUserRows.add(a);
            Map<String,Object> b = new HashMap<>(); b.put("the_date", key); b.put("cnt", (long)(i%5)); activeRows.add(b);
            Map<String,Object> c = new HashMap<>(); c.put("the_date", key); c.put("cnt", (long)(2+i)); recvRows.add(c);
            Map<String,Object> d = new HashMap<>(); d.put("the_date", key); d.put("cnt", (long)(1+i)); compRows.add(d);
            iter.add(Calendar.DAY_OF_MONTH, 1);
        }
        when(userMapper.countUsersGroupByDate(rangeStart, rangeEnd)).thenReturn(newUserRows);
        when(userMapper.countUsersBefore(rangeStart)).thenReturn(100L);
        when(userTaskInstanceMapper.countActiveUsersGroupByDate(rangeStart, rangeEnd)).thenReturn(activeRows);
        when(userTaskInstanceMapper.countTasksGroupByDate(rangeStart, rangeEnd)).thenReturn(recvRows);
        when(userTaskInstanceMapper.countTasksByStatusGroupByDate(rangeStart, rangeEnd, 1)).thenReturn(compRows);

        // page 1 size 5 -> should return today..today-4
        org.springframework.data.domain.Pageable fake = null;
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DailyStatItem> pg = statsService.pagedDailyStats(1, 5);
        assertNotNull(pg);
        assertEquals(5, pg.getRecords().size());
        // first record statDate equals today's date
        String todayStr = SDF.format(cal.getTime());
        assertEquals(todayStr, pg.getRecords().get(0).getStatDate());
    }
}
