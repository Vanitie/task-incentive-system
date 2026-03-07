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
 * 徽章实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("badge")
public class Badge {

    /**
     * 雪花ID
     */
    private Long id;

    /**
     * 徽章名称
     */
    private String name;

    /**
     * 徽章标识，与奖励值对应
     */
    private Integer code;

    /**
     * 图片URL
     */
    private String imageUrl;

    /**
     * 描述
     */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}