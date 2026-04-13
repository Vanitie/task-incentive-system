package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 用户管理页 DTO
 */
public final class UserAdminDTO {

    private UserAdminDTO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAdminItem {
        private Long id;
        private String username;
        private String roles;
        private Integer pointBalance;
        private Boolean enabled;
        private Date registerTime;
        private Date lastActiveTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDetail {
        private UserAdminItem basic;
        private PointSummary pointSummary;
        private BehaviorSummary behavior7d;
        private BehaviorSummary behavior30d;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointSummary {
        private Integer currentPoints;
        private Long totalGain;
        private Long totalConsume;
        private Long recentChange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehaviorSummary {
        private Integer days;
        private Long behaviorCount;
        private Long signDays;
        private Double taskCompletionRate;
        private Double rewardRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogItem {
        private Long id;
        private Long operatorUserId;
        private Long targetUserId;
        private String actionType;
        private String detail;
        private Date operateTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditPage {
        private Long total;
        private List<AuditLogItem> items;
    }
}

