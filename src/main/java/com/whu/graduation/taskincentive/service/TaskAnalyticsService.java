package com.whu.graduation.taskincentive.service;

import com.whu.graduation.taskincentive.dto.TaskAnalyticsDTO;

import java.util.List;

public interface TaskAnalyticsService {

    TaskAnalyticsDTO.MetricOverview taskConfigOverview(Long taskId, int days);

    TaskAnalyticsDTO.AudienceHit taskConfigAudienceHit(Long taskId, int days);

    List<TaskAnalyticsDTO.HeatmapCell> taskConfigTimeHeatmap(Long taskId, int days);

    List<TaskAnalyticsDTO.RewardElasticityItem> taskConfigRewardElasticity(Long taskId, int days);

    TaskAnalyticsDTO.VersionCompare taskConfigVersionCompare(Long taskId,
                                                             String baselineVersion,
                                                             String compareVersion,
                                                             String compareStart,
                                                             String compareEnd);

    TaskAnalyticsDTO.HealthMetrics taskConfigHealth(Long taskId, int days);

    TaskAnalyticsDTO.TaskConfigInsight taskConfigInsight(Long taskId, int days);

    TaskAnalyticsDTO.MetricOverview userTaskOverview(String startTime,
                                                     String endTime,
                                                     String taskType,
                                                     String campaignId,
                                                     String userLayer);

    TaskAnalyticsDTO.FunnelMetrics userTaskFunnel(String startTime,
                                                  String endTime,
                                                  String taskType,
                                                  String campaignId,
                                                  String userLayer);

    List<TaskAnalyticsDTO.TrendPoint> userTaskTrend(String startTime,
                                                    String endTime,
                                                    String granularity,
                                                    String taskType,
                                                    String campaignId);

    List<TaskAnalyticsDTO.DimensionPerformance> userTaskTypePerformance(String startTime,
                                                                        String endTime,
                                                                        Integer topN,
                                                                        String sortBy);

    List<TaskAnalyticsDTO.DimensionPerformance> userTaskLayerPerformance(String startTime,
                                                                         String endTime,
                                                                         Integer topN,
                                                                         String sortBy);

    List<TaskAnalyticsDTO.UserTopNItem> userTaskTopN(String startTime,
                                                     String endTime,
                                                     Integer n,
                                                     String taskType,
                                                     String orderBy);

    List<TaskAnalyticsDTO.UserTopNItem> userTaskTopNAccepted(String startTime,
                                                             String endTime,
                                                             Integer n,
                                                             String taskType,
                                                             String orderBy);

    TaskAnalyticsDTO.AnomalyMetrics userTaskAnomaly(String startTime,
                                                    String endTime,
                                                    String taskType,
                                                    String campaignId);

    TaskAnalyticsDTO.CostRoiMetrics userTaskCostRoi(String startTime,
                                                    String endTime,
                                                    String taskType,
                                                    String campaignId);
}
