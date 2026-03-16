package com.whu.graduation.taskincentive.dto;

import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "用户任务视图 DTO，包含任务配置与运行时信息")
public class TaskView {
    @Schema(description = "任务配置对象（TaskConfig）")
    private TaskConfig taskConfig;

    @Schema(description = "剩余库存，若非限量任务为 null")
    private Integer remainingStock;

    @Schema(description = "用户是否已接取该任务")
    private Boolean userAccepted;

    @Schema(description = "当前用户是否可以接取该任务")
    private Boolean canAccept;

    @Schema(description = "若不可接取，给出原因（如：已结束/未开始/售罄/已接取）")
    private String reason;
}
