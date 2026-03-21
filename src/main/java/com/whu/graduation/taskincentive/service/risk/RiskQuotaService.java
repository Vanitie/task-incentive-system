package com.whu.graduation.taskincentive.service.risk;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dto.risk.RiskQuotaRequest;

/**
 * 风控配额服务
 */
public interface RiskQuotaService {
    Page<RiskQuota> page(Page<RiskQuota> page);
    RiskQuota update(RiskQuotaRequest request);
    boolean deleteById(Long id);
    RiskQuota create(RiskQuotaRequest request);
}
