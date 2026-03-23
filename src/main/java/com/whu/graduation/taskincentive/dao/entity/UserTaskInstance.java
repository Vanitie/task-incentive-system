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
 * 用户任务实例实体（动态进度）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_task_instance")
public class UserTaskInstance {

    /**
     * 实例ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名（冗余展示字段）
     */
    private String userName;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 任务名（冗余展示字段）
     */
    private String taskName;

    /**
     * 当前完成进度
     */
    private Integer progress;

    /**
        * 任务状态：1-已领取(ACCEPTED) / 2-进行中(IN_PROGRESS) / 3-已完成(COMPLETED) / 4-已取消(CANCELLED)
     */
    private Integer status;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 扩展数据 JSON，例如连续签到天数、阶段完成状态等
     */
    private String extraData;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}