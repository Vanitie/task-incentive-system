package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 简单分页结果封装，用于 controller 返回分页数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private int page;

    /** 每页大小 */
    private int size;

    /** 当前页数据列表 */
    private List<T> items;
}
