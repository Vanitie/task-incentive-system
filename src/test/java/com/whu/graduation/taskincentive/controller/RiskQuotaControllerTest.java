package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dto.risk.RiskQuotaRequest;
import com.whu.graduation.taskincentive.service.risk.RiskQuotaService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskQuotaControllerTest {

    @Test
    void endpoints_shouldReturnOk() {
        RiskQuotaService service = mock(RiskQuotaService.class);
        RiskQuotaController controller = new RiskQuotaController(service);

        Page<RiskQuota> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(Collections.singletonList(new RiskQuota()));
        when(service.page(any())).thenReturn(page);
        when(service.create(any())).thenReturn(new RiskQuota());
        when(service.update(any())).thenReturn(new RiskQuota());
        when(service.deleteById(1L)).thenReturn(true);

        assertEquals(0, controller.list(1, 20).getCode());
        assertEquals(0, controller.create(new RiskQuotaRequest()).getCode());
        assertEquals(0, controller.update(new RiskQuotaRequest()).getCode());
        assertEquals(0, controller.delete(1L).getCode());
    }
}

