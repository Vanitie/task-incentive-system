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
     * 任务ID
     */
    private Long taskId;

    /**
     * 当前完成进度
     */
    private Integer progress;

    /**
     * 任务完成状态：未完成 STATUS_INCOMPLETE / 已完成 STATUS_COMPLETED
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