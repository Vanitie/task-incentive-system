package com.whu.graduation.taskincentive.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 冻结奖励记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("reward_freeze_record")
public class RewardFreezeRecord {
    /**
     * 奖励记录ID
     */
    private Long id;
    /**
     * 奖励ID
     */
    private Long rewardId;
    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 任务ID
     */
    private Long taskId;
    /**
     * 冻结原因
     */
    private String freezeReason;
    /**
     * 状态（0冻结，1已解冻）
     */
    private Integer status;
    /**
     * 解冻时间
     */
    private Date unfreezeAt;
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
