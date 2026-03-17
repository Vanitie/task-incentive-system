package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /**
     * 分页查询所有徽章
     */
    @Override
    public Page<Badge> selectPage(Page<Badge> page) {
        return super.page(page);
    }

    @Override
    public Page<Badge> searchByName(String name, Page<Badge> page) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Badge> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            wrapper.like("name", name);
        }
        return super.page(page, wrapper);
    }
}