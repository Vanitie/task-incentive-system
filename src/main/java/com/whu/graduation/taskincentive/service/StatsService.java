package com.whu.graduation.taskincentive.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dto.BarChartData;
import com.whu.graduation.taskincentive.dto.DailyStatItem;
import com.whu.graduation.taskincentive.dto.LatestActivityItem;
import com.whu.graduation.taskincentive.dto.ProgressDataItem;

import java.util.List;

public interface StatsService {

    /**
     * 返回两个数组：本周(7天) 与 上周(7天) 的任务接取数和完成数
     */
    List<BarChartData> getTwoWeeksTaskReceiveAndComplete();

    /**
     * 本周内每日（周一..周日）的完成率百分比
     */
    List<ProgressDataItem> getThisWeekCompletionPercent();

    /**
     * 分页查询每日统计数据（按日期降序）
     */
    Page<DailyStatItem> pagedDailyStats(int page, int size);

    /**
     * 首页最新动态流
     */
    List<LatestActivityItem> latestActivities(int limit);
}
