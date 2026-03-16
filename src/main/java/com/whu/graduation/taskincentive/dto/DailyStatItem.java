package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单日统计项（用于分页展示每日的关键指标）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyStatItem {
    /** 统计日期，格式 yyyy-MM-dd */
    private String statDate;

    /** 截至该日的用户总数（累计） */
    private Long userTotal;

    /** 当日活跃用户数（去重接取任务的用户数） */
    private Long activeUser;

    /** 当日任务接取数（创建记录数） */
    private Long taskReceived;

    /** 当日任务完成数（status=完成 的任务数） */
    private Long taskCompleted;

    /** 完成率字符串，例如 "95%" */
    private String completionRate; // e.g. "95%"
}
