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
 * 白名单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("risk_whitelist")
public class RiskWhitelist {
    /**
     * 主键ID
     */
    private Long id;
    /**
     * 名单类型（user/device/ip）
     */
    private String targetType;
    /**
     * 名单值
     */
    private String targetValue;
    /**
     * 来源
     */
    private String source;
    /**
     * 过期时间
     */
    private Date expireAt;
    /**
     * 状态（0无效，1有效）
     */
    private Integer status;
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
