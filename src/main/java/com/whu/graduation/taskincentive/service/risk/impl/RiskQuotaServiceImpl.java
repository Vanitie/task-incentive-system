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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 风控配额服务实现
 */
@Service
public class RiskQuotaServiceImpl implements RiskQuotaService {

    private final RiskQuotaMapper riskQuotaMapper;
    private final RiskCacheStore cacheStore;
    private final RedisTemplate<String, String> redisTemplate;

    public RiskQuotaServiceImpl(RiskQuotaMapper riskQuotaMapper,
                                RiskCacheStore cacheStore,
                                RedisTemplate<String, String> redisTemplate) {
        this.riskQuotaMapper = riskQuotaMapper;
        this.cacheStore = cacheStore;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Page<RiskQuota> page(Page<RiskQuota> page) {
        Page<RiskQuota> result = riskQuotaMapper.selectPage(page, new QueryWrapper<RiskQuota>().orderByDesc("created_at"));
        List<RiskQuota> records = result.getRecords();
        if (records == null || records.isEmpty()) {
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        List<String> redisKeys = new ArrayList<>(records.size());
        for (RiskQuota quota : records) {
            redisKeys.add(buildQuotaRedisKey(quota, now));
        }

        List<String> values = null;
        try {
            values = redisTemplate.opsForValue().multiGet(redisKeys);
        } catch (Exception ignored) {
        }

        for (int i = 0; i < records.size(); i++) {
            RiskQuota quota = records.get(i);
            String value = (values != null && i < values.size()) ? values.get(i) : null;
            quota.setUsedValue(parseInt(value));
            quota.setResetAt(nextResetAt(now, quota.getPeriodType()));
        }
        return result;
    }

    private String buildQuotaRedisKey(RiskQuota quota, LocalDateTime time) {
        String scopeType = quota.getScopeType() == null ? "" : quota.getScopeType().toUpperCase();
        String scopeId = quota.getScopeId() == null ? "ALL" : quota.getScopeId();
        String resourceType = quota.getResourceType() == null || quota.getResourceType().isEmpty()
                ? "ALL" : quota.getResourceType().toUpperCase();
        String resourceId = quota.getResourceId() == null || quota.getResourceId().isEmpty()
                ? "ALL" : quota.getResourceId();
        String periodType = normalizePeriodType(quota.getPeriodType());

        String key = RiskCacheStore.quotaKey(scopeType, scopeId, resourceType, resourceId, periodType);
        String bucket = bucketKey(periodType, time);
        return "risk:quota:" + key + ":" + bucket;
    }

    private String bucketKey(String periodType, LocalDateTime time) {
        if ("HOUR".equalsIgnoreCase(periodType)) {
            return time.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        }
        if ("MINUTE".equalsIgnoreCase(periodType)) {
            return time.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        }
        return time.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String normalizePeriodType(String periodType) {
        if (periodType == null || periodType.isEmpty()) {
            return "DAY";
        }
        return periodType.toUpperCase();
    }

    private Date nextResetAt(LocalDateTime now, String periodType) {
        String normalized = normalizePeriodType(periodType);
        LocalDateTime next;
        if ("MINUTE".equalsIgnoreCase(normalized)) {
            next = now.plusMinutes(1).withSecond(0).withNano(0);
        } else if ("HOUR".equalsIgnoreCase(normalized)) {
            next = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        } else {
            next = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        return Date.from(next.atZone(ZoneId.systemDefault()).toInstant());
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
        quota.setQuotaName(request.getQuotaName());
        quota.setScopeType(request.getScopeType());
        quota.setScopeId(request.getScopeId());
        quota.setResourceType(request.getResourceType());
        quota.setResourceId(request.getResourceId());
        quota.setPeriodType(request.getPeriodType());
        quota.setLimitValue(request.getLimitValue());
        quota.setResetAt(nextResetAt(LocalDateTime.now(), request.getPeriodType()));
        riskQuotaMapper.updateById(quota);
        refreshCache();
        return quota;
    }

    @Override
    public RiskQuota create(RiskQuotaRequest request) {
        ensureUnique(request.getScopeType(), request.getScopeId(), request.getResourceType(), request.getResourceId(), request.getPeriodType(), null);
        RiskQuota quota = RiskQuota.builder()
                .id(IdWorker.getId())
                .quotaName(request.getQuotaName())
                .scopeType(request.getScopeType())
                .scopeId(request.getScopeId())
                .resourceType(request.getResourceType())
                .resourceId(request.getResourceId())
                .periodType(request.getPeriodType())
                .limitValue(request.getLimitValue())
                .usedValue(0)
                .resetAt(nextResetAt(LocalDateTime.now(), request.getPeriodType()))
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
