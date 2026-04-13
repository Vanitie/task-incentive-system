package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.common.error.BusinessException;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskDecisionLogControllerTest {

    @Test
    void list_shouldReturnOk() {
        RiskDecisionLogService service = mock(RiskDecisionLogService.class);
        RiskDecisionLogController controller = new RiskDecisionLogController(service);
        Page<RiskDecisionLog> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(Collections.singletonList(new RiskDecisionLog()));
        when(service.page(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(page);

        ApiResponse<?> result = controller.list(1, 20, null, null, null, null, null, null, null);

        assertEquals(0, result.getCode());
    }

    @Test
    void list_shouldParseIso8601UtcDateParams() {
        RiskDecisionLogService service = mock(RiskDecisionLogService.class);
        RiskDecisionLogController controller = new RiskDecisionLogController(service);
        Page<RiskDecisionLog> page = new Page<>(1, 20);
        when(service.page(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(page);

        ApiResponse<?> result = controller.list(
                1,
                20,
                null,
                null,
                null,
                null,
                null,
                "2026-04-11T04:00:44.000Z",
                "2026-04-11T16:00:00.000Z"
        );

        ArgumentCaptor<Date> startCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> endCaptor = ArgumentCaptor.forClass(Date.class);
        verify(service).page(any(), any(), any(), any(), any(), any(), startCaptor.capture(), endCaptor.capture());

        assertEquals(0, result.getCode());
        assertNotNull(startCaptor.getValue());
        assertNotNull(endCaptor.getValue());
    }

    @Test
    void list_shouldThrowBusinessExceptionWhenDateParamInvalid() {
        RiskDecisionLogService service = mock(RiskDecisionLogService.class);
        RiskDecisionLogController controller = new RiskDecisionLogController(service);

        assertThrows(
                BusinessException.class,
                () -> controller.list(1, 20, null, null, null, null, null, "not-a-date", null)
        );
    }
}

