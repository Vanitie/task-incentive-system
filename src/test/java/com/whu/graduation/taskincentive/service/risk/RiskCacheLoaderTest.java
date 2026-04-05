package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.dao.entity.RiskBlacklist;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dao.entity.RiskWhitelist;
import com.whu.graduation.taskincentive.dao.mapper.RiskBlacklistMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskQuotaMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskRuleMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskWhitelistMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskCacheLoaderTest {

    @Test
    void load_shouldRefreshAllRiskCaches() {
        RiskRuleMapper ruleMapper = mock(RiskRuleMapper.class);
        RiskBlacklistMapper blacklistMapper = mock(RiskBlacklistMapper.class);
        RiskWhitelistMapper whitelistMapper = mock(RiskWhitelistMapper.class);
        RiskQuotaMapper quotaMapper = mock(RiskQuotaMapper.class);
        RiskCacheStore cacheStore = mock(RiskCacheStore.class);

        RiskCacheLoader loader = new RiskCacheLoader(ruleMapper, blacklistMapper, whitelistMapper, quotaMapper, cacheStore);

        RiskRule rule = RiskRule.builder().id(1L).status(1).build();
        when(ruleMapper.selectList(any())).thenReturn(Collections.singletonList(rule));

        RiskBlacklist blacklist = RiskBlacklist.builder().targetType("USER").targetValue("u1").status(1).build();
        when(blacklistMapper.selectList(any())).thenReturn(Collections.singletonList(blacklist));

        RiskWhitelist whitelist = RiskWhitelist.builder().targetType("IP").targetValue("127.0.0.1").status(1).build();
        when(whitelistMapper.selectList(any())).thenReturn(Collections.singletonList(whitelist));

        RiskQuota quota = RiskQuota.builder().scopeType("USER").scopeId("u1").resourceType("ALL").resourceId("ALL").periodType("DAY").build();
        when(quotaMapper.selectList(null)).thenReturn(Collections.singletonList(quota));

        loader.load();

        verify(cacheStore).refreshRules(Collections.singletonList(rule));

        ArgumentCaptor<Map<String, Set<String>>> blacklistCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cacheStore).refreshBlacklists(blacklistCaptor.capture());
        assertTrue(blacklistCaptor.getValue().containsKey(RiskCacheStore.listKey("USER")));
        assertEquals(Set.of("u1"), blacklistCaptor.getValue().get(RiskCacheStore.listKey("USER")));

        ArgumentCaptor<Map<String, Set<String>>> whitelistCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cacheStore).refreshWhitelists(whitelistCaptor.capture());
        assertTrue(whitelistCaptor.getValue().containsKey(RiskCacheStore.listKey("IP")));
        assertEquals(Set.of("127.0.0.1"), whitelistCaptor.getValue().get(RiskCacheStore.listKey("IP")));

        ArgumentCaptor<Map<String, RiskQuota>> quotaCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cacheStore).refreshQuotas(quotaCaptor.capture());
        assertEquals(1, quotaCaptor.getValue().size());
    }
}

