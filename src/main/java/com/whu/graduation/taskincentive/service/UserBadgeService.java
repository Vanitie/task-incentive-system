package com.whu.graduation.taskincentive.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserBadge;
import java.util.List;

/**
 * 用户徽章服务
 */
public interface UserBadgeService {

    boolean save(UserBadge userBadge);

    boolean update(UserBadge userBadge);

    boolean deleteById(Long id);

    UserBadge getById(Long id);

    List<UserBadge> listAll();

    List<UserBadge> listByUserId(Long userId);

    Page<UserBadge> selectByUserIdPage(Page<UserBadge> page, Long userId);

    boolean grantBadge(Long userId, Integer badgeCode);
}