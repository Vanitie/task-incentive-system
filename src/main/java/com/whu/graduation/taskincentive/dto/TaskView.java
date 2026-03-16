package com.whu.graduation.taskincentive.dto;

import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于用户视图的任务展示 DTO：包含任务配置及运行时信息（剩余库存、是否已接取、是否可接取等）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskView {
    /** 任务配置对象（原始 TaskConfig） */
    private TaskConfig taskConfig;

    /** 剩余库存（仅限量任务返回具体数值，非限量返回 null） */
    private Integer remainingStock;

    /** 用户是否已接取该任务（针对当前用户） */
    private Boolean userAccepted;

    /** 当前用户是否可接取该任务（权限/次数/时间等校验结果） */
    private Boolean canAccept;

    /** 如果不可接取，给出不可接取原因描述（如：已结束/未开始/售罄/已接取等） */
    private String reason;
}
