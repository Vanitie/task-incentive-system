package com.whu.graduation.taskincentive.service.risk.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.common.error.BusinessException;
import com.whu.graduation.taskincentive.common.error.ErrorCode;
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
        if (request.getId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "更新配额缺少ID");
        }
        RiskQuota quota = riskQuotaMapper.selectById(request.getId());
        if (quota == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "配额不存在");
        }
        ensureUnique(request.getScopeType(), request.getScopeId(), request.getResourceType(), request.getResourceId(), request.getPeriodType(), request.getId());
        quota.setScopeType(request.getScopeType());
        quota.setScopeId(request.getScopeId());
        quota.setResourceType(request.getResourceType());
        quota.setResourceId(request.getResourceId());
        quota.setPeriodType(request.getPeriodType());
        quota.setLimitValue(request.getLimitValue());
        riskQuotaMapper.updateById(quota);
        refreshCache();
        return quota;
    }

    @Override
    public RiskQuota create(RiskQuotaRequest request) {
        ensureUnique(request.getScopeType(), request.getScopeId(), request.getResourceType(), request.getResourceId(), request.getPeriodType(), null);
        RiskQuota quota = RiskQuota.builder()
                .id(IdWorker.getId())
                .scopeType(request.getScopeType())
                .scopeId(request.getScopeId())
                .resourceType(request.getResourceType())
                .resourceId(request.getResourceId())
                .periodType(request.getPeriodType())
                .limitValue(request.getLimitValue())
                .usedValue(0)
                .resetAt(new Date())
                .createdAt(new Date())
                .build();
        riskQuotaMapper.insert(quota);
        refreshCache();
        return quota;
    }

    @Override
    public boolean deleteById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "删除配额缺少ID");
        }
        int rows = riskQuotaMapper.deleteById(id);
        refreshCache();
        return rows > 0;
    }

    private void ensureUnique(String scopeType, String scopeId, String resourceType, String resourceId, String periodType, Long excludeId) {
        QueryWrapper<RiskQuota> wrapper = new QueryWrapper<RiskQuota>()
                .eq("scope_type", scopeType)
                .eq("scope_id", scopeId)
                .eq("resource_type", resourceType)
                .eq("resource_id", resourceId)
                .eq("period_type", periodType);
        RiskQuota existing = riskQuotaMapper.selectOne(wrapper);
        if (existing == null) return;
        if (excludeId == null || !excludeId.equals(existing.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "同一范围与资源配额已存在");
        }
    }

    private void refreshCache() {
        List<RiskQuota> quotas = riskQuotaMapper.selectList(null);
        cacheStore.refreshQuotas(RiskCacheStore.buildQuotaMap(quotas));
    }
}
