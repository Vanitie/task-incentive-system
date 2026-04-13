package com.whu.graduation.taskincentive.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("task_config_history")
public class TaskConfigHistory {

    private Long id;

    private Long taskId;

    private Integer versionNo;

    private String taskName;

    private String taskType;

    private String stockType;

    private String triggerEvent;

    private String ruleConfig;

    private String rewardType;

    private Integer rewardValue;

    private Integer totalStock;

    private Integer status;

    private Date startTime;

    private Date endTime;

    private Date sourceUpdateTime;

    private String changeType;

    private String changedBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}

