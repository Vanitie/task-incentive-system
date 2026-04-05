package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DailyStatItem> pg = statsService.pagedDailyStats(1, 5);
        assertNotNull(pg);
        assertEquals(5, pg.getRecords().size());
        // first record statDate equals today's date
        String todayStr = SDF.format(cal.getTime());
        assertEquals(todayStr, pg.getRecords().get(0).getStatDate());
    }

    @Test
    public void testPagedDailyStats_shouldReturnEmpty_whenPageOutOfRange() {
        when(userMapper.countUsersGroupByDate(any(Date.class), any(Date.class))).thenReturn(Collections.emptyList());
        when(userMapper.countUsersBefore(any(Date.class))).thenReturn(0L);
        when(userTaskInstanceMapper.selectUserIdsByDate(any(Date.class), any(Date.class))).thenReturn(Collections.emptyList());
        when(userTaskInstanceMapper.countTasksGroupByDate(any(Date.class), any(Date.class))).thenReturn(Collections.emptyList());
        when(userTaskInstanceMapper.countTasksByStatusGroupByDate(any(Date.class), any(Date.class), eq(1))).thenReturn(Collections.emptyList());

        Page<DailyStatItem> pg = statsService.pagedDailyStats(999, 10);

        assertNotNull(pg);
        assertTrue(pg.getRecords().isEmpty());
        assertEquals(30, pg.getTotal());
    }

    @Test
    public void testGetTwoWeeksTaskReceiveAndComplete_shouldTreatInvalidCntAsZero() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int offsetToMon = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        Calendar thisMon = (Calendar) cal.clone();
        thisMon.add(Calendar.DAY_OF_MONTH, offsetToMon);
        Calendar lastMon = (Calendar) thisMon.clone();
        lastMon.add(Calendar.DAY_OF_MONTH, -7);
        Calendar nextMon = (Calendar) thisMon.clone();
        nextMon.add(Calendar.DAY_OF_MONTH, 7);

        Map<String, Object> recv = new HashMap<>();
        recv.put("the_date", SDF.format(lastMon.getTime()));
        recv.put("cnt", "bad");
        when(userTaskInstanceMapper.countTasksGroupByDate(lastMon.getTime(), nextMon.getTime())).thenReturn(List.of(recv));
        when(userTaskInstanceMapper.countTasksByStatusGroupByDate(lastMon.getTime(), nextMon.getTime(), 1)).thenReturn(Collections.emptyList());

        List<BarChartData> out = statsService.getTwoWeeksTaskReceiveAndComplete();

        assertEquals(2, out.size());
        assertEquals(0L, out.get(1).getTaskReceived().get(0));
    }

    @Test
    public void testGetThisWeekCompletionPercent_shouldIgnoreNullDateAndParseStringCnt() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int offsetToMon = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        Calendar thisMon = (Calendar) cal.clone();
        thisMon.add(Calendar.DAY_OF_MONTH, offsetToMon);

        Calendar endCal = (Calendar) thisMon.clone();
        endCal.add(Calendar.DAY_OF_MONTH, 7);
        Date start = thisMon.getTime();
        Date end = endCal.getTime();

        String day1 = SDF.format(thisMon.getTime());
        Calendar day2Cal = (Calendar) thisMon.clone();
        day2Cal.add(Calendar.DAY_OF_MONTH, 1);
        String day2 = SDF.format(day2Cal.getTime());

        List<Map<String, Object>> recvRows = new ArrayList<>();
        Map<String, Object> recv1 = new HashMap<>();
        recv1.put("the_date", day1);
        recv1.put("cnt", "10");
        recvRows.add(recv1);
        Map<String, Object> recv2 = new HashMap<>();
        recv2.put("the_date", null);
        recv2.put("cnt", 999L);
        recvRows.add(recv2);

        List<Map<String, Object>> compRows = new ArrayList<>();
        Map<String, Object> comp1 = new HashMap<>();
        comp1.put("the_date", day1);
        comp1.put("cnt", "bad");
        compRows.add(comp1);
        Map<String, Object> comp2 = new HashMap<>();
        comp2.put("the_date", day2);
        comp2.put("cnt", "5");
        compRows.add(comp2);

        when(userTaskInstanceMapper.countTasksGroupByDate(start, end)).thenReturn(recvRows);
        when(userTaskInstanceMapper.countTasksByStatusGroupByDate(start, end, 1)).thenReturn(compRows);

        List<ProgressDataItem> out = statsService.getThisWeekCompletionPercent();

        assertEquals(7, out.size());
        assertEquals(0, out.get(0).getPercentage());
        assertEquals(0, out.get(1).getPercentage());
    }

    @Test
    public void testPagedDailyStats_shouldParseActiveUserIdsAndCompletionRate() {
        when(userMapper.countUsersGroupByDate(any(Date.class), any(Date.class))).thenReturn(Collections.emptyList());
        when(userMapper.countUsersBefore(any(Date.class))).thenReturn(0L);

        List<Map<String, Object>> activeRows = new ArrayList<>();
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        String todayKey = SDF.format(today.getTime());

        Map<String, Object> r1 = new HashMap<>();
        r1.put("the_date", todayKey);
        r1.put("user_id", "100");
        activeRows.add(r1);
        Map<String, Object> r2 = new HashMap<>();
        r2.put("the_date", todayKey);
        r2.put("user_id", 101L);
        activeRows.add(r2);
        Map<String, Object> r3 = new HashMap<>();
        r3.put("the_date", todayKey);
        r3.put("user_id", "bad");
        activeRows.add(r3);
        Map<String, Object> r4 = new HashMap<>();
        r4.put("the_date", null);
        r4.put("user_id", 102L);
        activeRows.add(r4);

        Map<String, Object> recv = new HashMap<>();
        recv.put("the_date", todayKey);
        recv.put("cnt", 4L);
        Map<String, Object> comp = new HashMap<>();
        comp.put("the_date", todayKey);
        comp.put("cnt", "2");

        when(userTaskInstanceMapper.selectUserIdsByDate(any(Date.class), any(Date.class))).thenReturn(activeRows);
        when(userTaskInstanceMapper.countTasksGroupByDate(any(Date.class), any(Date.class))).thenReturn(List.of(recv));
        when(userTaskInstanceMapper.countTasksByStatusGroupByDate(any(Date.class), any(Date.class), eq(1))).thenReturn(List.of(comp));

        Page<DailyStatItem> pg = statsService.pagedDailyStats(1, 1);

        assertEquals(1, pg.getRecords().size());
        DailyStatItem item = pg.getRecords().get(0);
        assertEquals(todayKey, item.getStatDate());
        assertEquals(2L, item.getActiveUser());
        assertEquals("50%", item.getCompletionRate());
    }

    @Test
    public void testPagedDailyStats_shouldSkipNullDateRows_andTreatInvalidCntAsZero() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        String todayKey = SDF.format(today.getTime());

        List<Map<String, Object>> newUserRows = new ArrayList<>();
        Map<String, Object> n1 = new HashMap<>();
        n1.put("the_date", todayKey);
        n1.put("cnt", "bad");
        newUserRows.add(n1);
        Map<String, Object> n2 = new HashMap<>();
        n2.put("the_date", null);
        n2.put("cnt", 999L);
        newUserRows.add(n2);

        List<Map<String, Object>> activeRows = new ArrayList<>();
        Map<String, Object> a1 = new HashMap<>();
        a1.put("the_date", todayKey);
        a1.put("user_id", null);
        activeRows.add(a1);

        List<Map<String, Object>> recvRows = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("the_date", todayKey);
        r1.put("cnt", "invalid");
        recvRows.add(r1);

        List<Map<String, Object>> compRows = new ArrayList<>();
        Map<String, Object> c1 = new HashMap<>();
        c1.put("the_date", todayKey);
        c1.put("cnt", "8");
        compRows.add(c1);

        when(userMapper.countUsersGroupByDate(any(Date.class), any(Date.class))).thenReturn(newUserRows);
        when(userMapper.countUsersBefore(any(Date.class))).thenReturn(10L);
        when(userTaskInstanceMapper.selectUserIdsByDate(any(Date.class), any(Date.class))).thenReturn(activeRows);
        when(userTaskInstanceMapper.countTasksGroupByDate(any(Date.class), any(Date.class))).thenReturn(recvRows);
        when(userTaskInstanceMapper.countTasksByStatusGroupByDate(any(Date.class), any(Date.class), eq(1))).thenReturn(compRows);

        Page<DailyStatItem> pg = statsService.pagedDailyStats(1, 1);

        assertEquals(1, pg.getRecords().size());
        DailyStatItem item = pg.getRecords().get(0);
        assertEquals(todayKey, item.getStatDate());
        assertEquals(10L, item.getUserTotal());
        assertEquals(0L, item.getTaskReceived());
        assertEquals("0%", item.getCompletionRate());
    }

    @Test
    public void testColorFromPercent_shouldClampBelowZeroAndAboveHundred() throws Exception {
        java.lang.reflect.Method m = StatsServiceImpl.class.getDeclaredMethod("colorFromPercent", int.class);
        m.setAccessible(true);

        String low = (String) m.invoke(statsService, -10);
        String zero = (String) m.invoke(statsService, 0);
        String high = (String) m.invoke(statsService, 200);
        String hundred = (String) m.invoke(statsService, 100);

        assertEquals(zero, low);
        assertEquals(hundred, high);
    }
}
