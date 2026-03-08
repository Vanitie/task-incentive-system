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
    private TaskConfig taskConfig;

    /** 剩余库存，若非限量任务为 null */
    private Integer remainingStock;

    /** 用户是否已接取该任务 */
    private Boolean userAccepted;

    /** 当前用户是否可以接取该任务 */
    private Boolean canAccept;

    /** 若不可接取，给出原因（如：已结束/未开始/售罄/已接取） */
    private String reason;
}
