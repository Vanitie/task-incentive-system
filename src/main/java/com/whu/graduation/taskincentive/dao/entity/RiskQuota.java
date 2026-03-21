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
 * 风控配额
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("risk_quota")
public class RiskQuota {
    /**
     * 主键ID
     */
    private Long id;
    /**
     * 配额作用范围类型（user/task/activity/global）
     */
    private String scopeType;
    /**
     * 配额作用范围ID
     */
    private String scopeId;
    /**
     * 资源类型（POINT/BADGE/PHYSICAL/ALL 等）
     */
    private String resourceType;
    /**
     * 资源ID（具体奖品/徽章/任务奖励ID；无区分时用 ALL）
     */
    private String resourceId;
    /**
     * 时间周期类型（minute/hour/day）
     */
    private String periodType;
    /**
     * 限额值
     */
    private Integer limitValue;
    /**
     * 已用额度
     */
    private Integer usedValue;
    /**
     * 重置时间
     */
    private Date resetAt;
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
