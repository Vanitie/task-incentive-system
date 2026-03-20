package com.whu.graduation.taskincentive.service.risk.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dao.mapper.RiskQuotaMapper;
import com.whu.graduation.taskincentive.dto.risk.RiskQuotaRequest;
import com.whu.graduation.taskincentive.service.risk.RiskCacheStore;
import com.whu.graduation.taskincentive.service.risk.RiskQuotaService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 风控配额服务实现
 */
@Service
public class RiskQuotaServiceImpl implements RiskQuotaService {

    private final RiskQuotaMapper riskQuotaMapper;
    private final RiskCacheStore cacheStore;

    public RiskQuotaServiceImpl(RiskQuotaMapper riskQuotaMapper, RiskCacheStore cacheStore) {
        this.riskQuotaMapper = riskQuotaMapper;
        this.cacheStore = cacheStore;
    }

    @Override
    public Page<RiskQuota> page(Page<RiskQuota> page) {
        return riskQuotaMapper.selectPage(page, new QueryWrapper<RiskQuota>().orderByDesc("created_at"));
    }

    @Override
    public RiskQuota update(RiskQuotaRequest request) {
        RiskQuota quota = request.getId() == null ? null : riskQuotaMapper.selectById(request.getId());
        if (quota == null) {
            quota = RiskQuota.builder()
                    .id(IdWorker.getId())
                    .scopeType(request.getScopeType())
                    .scopeId(request.getScopeId())
                    .periodType(request.getPeriodType())
                    .limitValue(request.getLimitValue())
                    .usedValue(0)
                    .resetAt(new Date())
                    .createdAt(new Date())
                    .build();
            riskQuotaMapper.insert(quota);
        } else {
            quota.setScopeType(request.getScopeType());
            quota.setScopeId(request.getScopeId());
            quota.setPeriodType(request.getPeriodType());
            quota.setLimitValue(request.getLimitValue());
            riskQuotaMapper.updateById(quota);
        }
        refreshCache();
        return quota;
    }

    private void refreshCache() {
        List<RiskQuota> quotas = riskQuotaMapper.selectList(null);
        cacheStore.refreshQuotas(RiskCacheStore.buildQuotaMap(quotas));
    }
}
