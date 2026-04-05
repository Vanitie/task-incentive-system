package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionRequest;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskDecisionControllerTest {

    @Test
    void evaluate_shouldReturnOk() {
        RiskDecisionService service = mock(RiskDecisionService.class);
        RiskDecisionController controller = new RiskDecisionController(service);
        RiskDecisionResponse response = RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").build();
        when(service.evaluate(any())).thenReturn(response);

        ApiResponse<RiskDecisionResponse> result = controller.evaluate(new RiskDecisionRequest());

        assertEquals(0, result.getCode());
        assertEquals("PASS", result.getData().getDecision());
    }
}

