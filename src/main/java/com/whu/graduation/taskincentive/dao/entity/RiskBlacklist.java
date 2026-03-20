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
 * 黑名单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("risk_blacklist")
public class RiskBlacklist {
    private Long id;
    private String targetType;
    private String targetValue;
    private String source;
    private Date expireAt;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
