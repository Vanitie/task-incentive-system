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
 * 用户行为日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_action_log")
public class UserActionLog {

    /**
     * 行为日志ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 行为类型：用户学习 USER_LEARN / 用户签到 USER_SIGN / 其他 OTHER
     */
    private String actionType;

    /**
     * 行为数值，例如分钟数/次数
     */
    private Integer actionValue;

    /**
     * 行为发生时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 用户名（展示字段，不入库）
     */
    @TableField(exist = false)
    private String userName;
}
