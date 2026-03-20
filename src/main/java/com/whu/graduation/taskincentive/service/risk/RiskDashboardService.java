package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.dto.risk.RiskDashboardOverviewResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskDashboardTrendItem;

import java.util.Date;
import java.util.List;

/**
 * 风控看板服务
 */
public interface RiskDashboardService {
    RiskDashboardOverviewResponse overview(Date start, Date end);

    List<RiskDashboardTrendItem> dailyTrend(Date start, Date end);
}
