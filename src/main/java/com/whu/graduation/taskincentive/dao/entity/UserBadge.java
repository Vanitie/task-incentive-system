package com.whu.graduation.taskincentive.dao.entity;

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

    private Long id; // 雪花ID

    private Long userId; // 用户ID

    private Long badgeId; // 徽章ID

    private Date acquireTime; // 获得时间
}