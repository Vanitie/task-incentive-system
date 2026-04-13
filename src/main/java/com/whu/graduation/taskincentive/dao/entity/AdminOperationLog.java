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
 * 管理操作审计日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("admin_operation_log")
public class AdminOperationLog {

    private Long id;

    private Long operatorUserId;

    private Long targetUserId;

    private String actionType;

    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}

