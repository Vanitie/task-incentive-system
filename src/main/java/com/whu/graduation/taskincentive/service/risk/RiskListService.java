package com.whu.graduation.taskincentive.service.risk;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskBlacklist;
import com.whu.graduation.taskincentive.dao.entity.RiskWhitelist;
import com.whu.graduation.taskincentive.dto.risk.RiskListRequest;

/**
 * 风控名单服务
 */
public interface RiskListService {
    Page<RiskBlacklist> pageBlacklist(Page<RiskBlacklist> page);
    Page<RiskWhitelist> pageWhitelist(Page<RiskWhitelist> page);
    RiskBlacklist addBlacklist(RiskListRequest request);
    RiskWhitelist addWhitelist(RiskListRequest request);
}
