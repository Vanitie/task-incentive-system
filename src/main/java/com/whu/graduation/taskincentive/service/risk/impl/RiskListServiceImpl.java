package com.whu.graduation.taskincentive.service.risk.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskBlacklist;
import com.whu.graduation.taskincentive.dao.entity.RiskWhitelist;
import com.whu.graduation.taskincentive.dao.mapper.RiskBlacklistMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskWhitelistMapper;
import com.whu.graduation.taskincentive.dto.risk.RiskListRequest;
import com.whu.graduation.taskincentive.service.risk.RiskCacheStore;
import com.whu.graduation.taskincentive.service.risk.RiskListService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 风控名单服务实现
 */
@Service
public class RiskListServiceImpl implements RiskListService {

    private final RiskBlacklistMapper blacklistMapper;
    private final RiskWhitelistMapper whitelistMapper;
    private final RiskCacheStore cacheStore;

    public RiskListServiceImpl(RiskBlacklistMapper blacklistMapper,
                               RiskWhitelistMapper whitelistMapper,
                               RiskCacheStore cacheStore) {
        this.blacklistMapper = blacklistMapper;
        this.whitelistMapper = whitelistMapper;
        this.cacheStore = cacheStore;
    }

    @Override
    public Page<RiskBlacklist> pageBlacklist(Page<RiskBlacklist> page) {
        return blacklistMapper.selectPage(page, new QueryWrapper<RiskBlacklist>().orderByDesc("created_at"));
    }

    @Override
    public Page<RiskWhitelist> pageWhitelist(Page<RiskWhitelist> page) {
        return whitelistMapper.selectPage(page, new QueryWrapper<RiskWhitelist>().orderByDesc("created_at"));
    }

    @Override
    public RiskBlacklist addBlacklist(RiskListRequest request) {
        RiskBlacklist bl = RiskBlacklist.builder()
                .id(IdWorker.getId())
                .targetType(request.getTargetType())
                .targetValue(request.getTargetValue())
                .source(request.getSource())
                .expireAt(request.getExpireAt())
                .status(request.getStatus() == null ? 1 : request.getStatus())
                .createdAt(new Date())
                .build();
        blacklistMapper.insert(bl);
        refreshBlacklist();
        return bl;
    }

    @Override
    public RiskWhitelist addWhitelist(RiskListRequest request) {
        RiskWhitelist wl = RiskWhitelist.builder()
                .id(IdWorker.getId())
                .targetType(request.getTargetType())
                .targetValue(request.getTargetValue())
                .source(request.getSource())
                .expireAt(request.getExpireAt())
                .status(request.getStatus() == null ? 1 : request.getStatus())
                .createdAt(new Date())
                .build();
        whitelistMapper.insert(wl);
        refreshWhitelist();
        return wl;
    }

    private void refreshBlacklist() {
        List<RiskBlacklist> list = blacklistMapper.selectList(new QueryWrapper<RiskBlacklist>().eq("status", 1));
        Map<String, Set<String>> map = RiskCacheStore.buildListMap(list,
                o -> ((RiskBlacklist) o).getTargetType(),
                o -> ((RiskBlacklist) o).getTargetValue());
        cacheStore.refreshBlacklists(map);
    }

    private void refreshWhitelist() {
        List<RiskWhitelist> list = whitelistMapper.selectList(new QueryWrapper<RiskWhitelist>().eq("status", 1));
        Map<String, Set<String>> map = RiskCacheStore.buildListMap(list,
                o -> ((RiskWhitelist) o).getTargetType(),
                o -> ((RiskWhitelist) o).getTargetValue());
        cacheStore.refreshWhitelists(map);
    }
}
