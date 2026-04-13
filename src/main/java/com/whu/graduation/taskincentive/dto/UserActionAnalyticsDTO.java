package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户行为分析相关 DTO
 */
public final class UserActionAnalyticsDTO {

    private UserActionAnalyticsDTO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String bucket;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeRatioItem {
        private String actionType;
        private Long count;
        private Double ratio;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserLayerItem {
        private String layer;
        private Long signCount;
        private Long learnCount;
        private Long acceptCount;
        private Long rewardCount;
        private Long totalCount;
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
    public static class ConversionDashboard {
        private Double completionRate;
        private Double rewardRate;
        private Double avgActionsPerUser;
        private Long totalActions;
        private Long activeUsers;
        private Long completedActions;
        private Long rewardActions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopNItem {
        private String name;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopNResult {
        private List<TopNItem> topUsers;
        private List<TopNItem> topActionTypes;
    }
}

