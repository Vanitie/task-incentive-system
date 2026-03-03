package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.Badge;
import com.whu.graduation.taskincentive.dao.mapper.BadgeMapper;
import com.whu.graduation.taskincentive.service.BadgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 徽章服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeServiceImpl extends ServiceImpl<BadgeMapper, Badge> implements BadgeService {

    @Override
    public boolean save(Badge badge) {
        badge.setId(IdWorker.getId());
        return super.save(badge);
    }

    @Override
    public boolean update(Badge badge) {
        return super.updateById(badge);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public Badge getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<Badge> listAll() {
        return super.list();
    }
}