package com.whu.graduation.taskincentive.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户奖励记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_reward_record")
public class UserRewardRecord {

    /**
     * 奖励记录ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 奖励类型：积分 REWARD_POINT / 徽章 REWARD_BADGE / 实物 REWARD_PHYSICAL
     */
    private String rewardType;

    /**
     * 奖励状态（仅实物奖励有效）：未领取 0 / 已领取 1
     */
    private Integer status;

    /**
     * 奖励数值或数量
     */
    private Integer rewardValue;

    /**
     * 发放时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
