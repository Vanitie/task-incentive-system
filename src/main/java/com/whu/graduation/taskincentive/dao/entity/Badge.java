package com.whu.graduation.taskincentive.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 徽章实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("badge")
public class Badge {

    private Long id; // 雪花ID

    private String name; // 徽章名称

    private Integer code; // 徽章标识，与奖励值对应

    private String imageUrl; // 图片URL

    private String description; // 描述

    private Date createTime;

    private Date updateTime;
}