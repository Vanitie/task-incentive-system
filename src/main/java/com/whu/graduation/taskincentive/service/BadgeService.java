package com.whu.graduation.taskincentive.service;

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
}