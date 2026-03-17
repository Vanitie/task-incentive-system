package com.whu.graduation.taskincentive.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.Badge;
import java.util.List;

/**
 * 徽章服务
 */
public interface BadgeService {

    boolean save(Badge badge);

    boolean update(Badge badge);

    boolean deleteById(Long id);

    Badge getById(Long id);

    List<Badge> listAll();

    Page<Badge> selectPage(Page<Badge> page);

    /**
     * 按名称模糊搜索徽章，分页
     */
    Page<Badge> searchByName(String name, Page<Badge> page);
}