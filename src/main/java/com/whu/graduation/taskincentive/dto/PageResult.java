package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 简单分页结果封装
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private long total;
    private int page;
    private int size;
    private List<T> items;
}
