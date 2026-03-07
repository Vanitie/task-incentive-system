package com.whu.graduation.taskincentive.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户徽章实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_badge")
public class UserBadge {

    /**
     * 雪花ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 徽章ID
     */
    private Long badgeId;

    /**
     * 获得时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date acquireTime;
}