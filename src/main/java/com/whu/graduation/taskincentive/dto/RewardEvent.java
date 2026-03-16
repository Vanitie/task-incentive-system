package com.whu.graduation.taskincentive.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 奖励事件 DTO，用于消息或事件传输，表示用户获得某个奖励
 */
@Data
@AllArgsConstructor
@Schema(description = "奖励事件 DTO，表示用户获得某个奖励")
public class RewardEvent {

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "奖励对象")
    private Reward reward;
}
