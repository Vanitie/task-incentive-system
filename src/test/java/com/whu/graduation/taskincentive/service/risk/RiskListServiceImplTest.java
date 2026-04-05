package com.whu.graduation.taskincentive.service.risk;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskBlacklist;
import com.whu.graduation.taskincentive.dao.entity.RiskWhitelist;
import com.whu.graduation.taskincentive.dao.mapper.RiskBlacklistMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskWhitelistMapper;
import com.whu.graduation.taskincentive.dto.risk.RiskListRequest;
import com.whu.graduation.taskincentive.service.risk.impl.RiskListServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskListServiceImplTest {

    @Test
    void pageAndAdd_shouldWorkForBlacklistAndWhitelist() {
        RiskBlacklistMapper blacklistMapper = mock(RiskBlacklistMapper.class);
        RiskWhitelistMapper whitelistMapper = mock(RiskWhitelistMapper.class);
        RiskCacheStore cacheStore = mock(RiskCacheStore.class);
        RiskListServiceImpl service = new RiskListServiceImpl(blacklistMapper, whitelistMapper, cacheStore);

        Page<RiskBlacklist> blackPage = new Page<>(1, 10);
        blackPage.setRecords(Collections.singletonList(new RiskBlacklist()));
        when(blacklistMapper.selectPage(any(), any())).thenReturn(blackPage);
        when(blacklistMapper.selectList(any())).thenReturn(Collections.singletonList(
                RiskBlacklist.builder().targetType("USER").targetValue("u1").status(1).build()
        ));

        Page<RiskWhitelist> whitePage = new Page<>(1, 10);
        whitePage.setRecords(Collections.singletonList(new RiskWhitelist()));
        when(whitelistMapper.selectPage(any(), any())).thenReturn(whitePage);
        when(whitelistMapper.selectList(any())).thenReturn(Collections.singletonList(
                RiskWhitelist.builder().targetType("USER").targetValue("u2").status(1).build()
        ));

        RiskListRequest request = RiskListRequest.builder()
                .targetType("USER")
                .targetValue("u100")
                .source("test")
                .expireAt(new Date())
                .build();

        assertEquals(1, service.pageBlacklist(new Page<>(1, 10)).getRecords().size());
        assertEquals(1, service.pageWhitelist(new Page<>(1, 10)).getRecords().size());

        RiskBlacklist blacklist = service.addBlacklist(request);
        RiskWhitelist whitelist = service.addWhitelist(request);

        assertNotNull(blacklist.getId());
        assertNotNull(whitelist.getId());
        assertEquals(1, blacklist.getStatus());
        assertEquals(1, whitelist.getStatus());

        verify(blacklistMapper).insert(any(RiskBlacklist.class));
        verify(whitelistMapper).insert(any(RiskWhitelist.class));
        verify(cacheStore).refreshBlacklists(any());
        verify(cacheStore).refreshWhitelists(any());
    }
}

