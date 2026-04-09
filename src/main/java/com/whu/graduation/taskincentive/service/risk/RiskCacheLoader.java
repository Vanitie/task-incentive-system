package com.whu.graduation.taskincentive.service.risk;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.graduation.taskincentive.dao.entity.RiskBlacklist;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dao.entity.RiskWhitelist;
import com.whu.graduation.taskincentive.dao.mapper.RiskBlacklistMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskQuotaMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskRuleMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskWhitelistMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 风控缓存加载器
 */
@Component
public class RiskCacheLoader {

    private final RiskRuleMapper riskRuleMapper;
    private final RiskBlacklistMapper riskBlacklistMapper;
    private final RiskWhitelistMapper riskWhitelistMapper;
    private final RiskQuotaMapper riskQuotaMapper;
    private final RiskCacheStore cacheStore;

    public RiskCacheLoader(RiskRuleMapper riskRuleMapper,
                           RiskBlacklistMapper riskBlacklistMapper,
                           RiskWhitelistMapper riskWhitelistMapper,
                           RiskQuotaMapper riskQuotaMapper,
                           RiskCacheStore cacheStore) {
        this.riskRuleMapper = riskRuleMapper;
        this.riskBlacklistMapper = riskBlacklistMapper;
        this.riskWhitelistMapper = riskWhitelistMapper;
        this.riskQuotaMapper = riskQuotaMapper;
        this.cacheStore = cacheStore;
    }

    public LoadStats load() {
        List<RiskRule> rules = riskRuleMapper.selectList(new QueryWrapper<RiskRule>().eq("status", 1));
        cacheStore.refreshRules(rules);

        List<RiskBlacklist> bl = riskBlacklistMapper.selectList(new QueryWrapper<RiskBlacklist>().eq("status", 1));
        Map<String, Set<String>> blMap = RiskCacheStore.buildListMap(bl,
                o -> ((RiskBlacklist) o).getTargetType(),
                o -> ((RiskBlacklist) o).getTargetValue());
        cacheStore.refreshBlacklists(blMap);

        List<RiskWhitelist> wl = riskWhitelistMapper.selectList(new QueryWrapper<RiskWhitelist>().eq("status", 1));
        Map<String, Set<String>> wlMap = RiskCacheStore.buildListMap(wl,
                o -> ((RiskWhitelist) o).getTargetType(),
                o -> ((RiskWhitelist) o).getTargetValue());
        cacheStore.refreshWhitelists(wlMap);

        List<RiskQuota> quotas = riskQuotaMapper.selectList(null);
        cacheStore.refreshQuotas(RiskCacheStore.buildQuotaMap(quotas));

        return new LoadStats(
                rules == null ? 0 : rules.size(),
                bl == null ? 0 : bl.size(),
                wl == null ? 0 : wl.size(),
                quotas == null ? 0 : quotas.size());
    }

    @Getter
    @AllArgsConstructor
    public static class LoadStats {
        private final int activeRuleCount;
        private final int blacklistCount;
        private final int whitelistCount;
        private final int quotaCount;
    }
}
