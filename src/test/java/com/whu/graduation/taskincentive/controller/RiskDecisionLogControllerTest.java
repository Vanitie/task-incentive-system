package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionLogService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskDecisionLogControllerTest {

    @Test
    void list_shouldReturnOk() {
        RiskDecisionLogService service = mock(RiskDecisionLogService.class);
        RiskDecisionLogController controller = new RiskDecisionLogController(service);
        Page<RiskDecisionLog> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(Collections.singletonList(new RiskDecisionLog()));
        when(service.page(any(), any(), any(), any(), any())).thenReturn(page);

        ApiResponse<?> result = controller.list(1, 20, null, null, null, null);

        assertEquals(0, result.getCode());
    }
}

