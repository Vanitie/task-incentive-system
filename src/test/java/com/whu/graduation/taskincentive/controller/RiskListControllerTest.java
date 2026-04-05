package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskBlacklist;
import com.whu.graduation.taskincentive.dao.entity.RiskWhitelist;
import com.whu.graduation.taskincentive.dto.risk.RiskListRequest;
import com.whu.graduation.taskincentive.service.risk.RiskListService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskListControllerTest {

    @Test
    void endpoints_shouldReturnOk() {
        RiskListService service = mock(RiskListService.class);
        RiskListController controller = new RiskListController(service);

        Page<RiskBlacklist> blacklistPage = new Page<>(1, 20);
        blacklistPage.setTotal(1);
        blacklistPage.setRecords(Collections.singletonList(new RiskBlacklist()));
        Page<RiskWhitelist> whitelistPage = new Page<>(1, 20);
        whitelistPage.setTotal(1);
        whitelistPage.setRecords(Collections.singletonList(new RiskWhitelist()));

        when(service.pageBlacklist(any())).thenReturn(blacklistPage);
        when(service.pageWhitelist(any())).thenReturn(whitelistPage);
        when(service.addBlacklist(any())).thenReturn(new RiskBlacklist());
        when(service.addWhitelist(any())).thenReturn(new RiskWhitelist());

        assertEquals(0, controller.blacklist(1, 20).getCode());
        assertEquals(0, controller.whitelist(1, 20).getCode());
        assertEquals(0, controller.addBlacklist(new RiskListRequest()).getCode());
        assertEquals(0, controller.addWhitelist(new RiskListRequest()).getCode());
    }
}

