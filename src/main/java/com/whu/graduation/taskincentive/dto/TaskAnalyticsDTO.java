package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

public final class TaskAnalyticsDTO {

    private TaskAnalyticsDTO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricOverview {
        private Integer days;
        private Long acceptedCount;
        private Long completedCount;
        private Long rewardedCount;
        private Double completionRate;
        private Double rewardRate;
        private Double avgCompletionMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudienceLayerItem {
        private String layer;
        private Long count;
        private Double rate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudienceHit {
        private Long targetUsers;
        private Long reachedUsers;
        private Double reachRate;
        private List<AudienceLayerItem> layerBreakdown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeatmapCell {
        private Integer dayOfWeek;
        private Integer hourOfDay;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RewardElasticityItem {
        private String rewardBucket;
        private Long acceptedCount;
        private Long completedCount;
        private Long rewardedCount;
        private Double completionRate;
        private Double rewardRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionCompare {
        private String baselineVersion;
        private String compareVersion;
        private Date baselineStart;
        private Date baselineEnd;
        private Date compareStart;
        private Date compareEnd;
        private MetricOverview baseline;
        private MetricOverview compare;
        private Double deltaCompletionRate;
        private Double deltaRewardRate;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthMetrics {
        private Double failedRate;
        private Double timeoutRate;
        private Long compensationCount;
        private Long idempotentConflictCount;
        private Long totalRewardRequests;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskConfigInsight {
        private Integer days;
        private Long acceptedCount;
        private Long completedCount;
        private Long rewardedCount;
        private Double completionRate;
        private Double rewardRate;
        private Double avgCompletionMinutes;
        private Double failedRate;
        private Double timeoutRate;
        private Long compensationCount;
        private Long idempotentConflictCount;
        private Long totalRewardRequests;
        private Long conversionLossCount;
        private Long rewardLossCount;
        private Double qualityScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunnelMetrics {
        private Long exposed;
        private Long accepted;
        private Long completed;
        private Long rewarded;
        private Double acceptRate;
        private Double completeRate;
        private Double rewardRate;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String bucket;
        private Long acceptedCount;
        private Long completedCount;
        private Long rewardedCount;
        private Long failedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionPerformance {
        private String name;
        private Long acceptedCount;
        private Long completedCount;
        private Long rewardedCount;
        private Double completionRate;
        private Double rewardRate;
        private Double avgCompletionMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserTopNItem {
        private Long userId;
        private String userName;
        private Long acceptedCount;
        private Long topTaskCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorReasonItem {
        private String reason;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyMetrics {
        private Double errorRate;
        private List<ErrorReasonItem> topErrorReasons;
        private Long replayCount;
        private Long idempotentConflictCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostRoiMetrics {
        private Long completedCount;
        private Double rewardCost;
        private Double costPerCompletion;
        private Double roi;
    }
}

