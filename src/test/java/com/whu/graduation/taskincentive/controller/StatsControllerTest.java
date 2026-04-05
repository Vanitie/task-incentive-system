package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dto.BarChartData;
import com.whu.graduation.taskincentive.dto.DailyStatItem;
import com.whu.graduation.taskincentive.dto.ProgressDataItem;
import com.whu.graduation.taskincentive.service.StatsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsControllerTest {

    @Test
    void methods_shouldDelegateToService() {
        StatsService statsService = mock(StatsService.class);
        List<BarChartData> bars = List.of(new BarChartData());
        List<ProgressDataItem> progress = List.of(new ProgressDataItem());
        Page<DailyStatItem> page = new Page<>(2, 5);

        when(statsService.getTwoWeeksTaskReceiveAndComplete()).thenReturn(bars);
        when(statsService.getThisWeekCompletionPercent()).thenReturn(progress);
        when(statsService.pagedDailyStats(2, 5)).thenReturn(page);

        StatsController controller = new StatsController(statsService);

        assertEquals(bars, controller.getTwoWeeksTaskReceiveAndComplete().getData());
        assertEquals(progress, controller.getThisWeekCompletionPercent().getData());
        assertEquals(page, controller.pagedDailyStats(2, 5).getData());

        verify(statsService).getTwoWeeksTaskReceiveAndComplete();
        verify(statsService).getThisWeekCompletionPercent();
        verify(statsService).pagedDailyStats(2, 5);
    }
}

