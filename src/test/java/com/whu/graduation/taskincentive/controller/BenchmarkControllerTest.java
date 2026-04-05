package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BenchmarkControllerTest {

    @Test
    void noop_shouldReturnOkStatusMap() {
        BenchmarkController controller = new BenchmarkController();

        ApiResponse<?> response = controller.noop();

        assertEquals(0, response.getCode());
        assertEquals("ok", response.getMsg());
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) response.getData();
        assertEquals("ok", data.get("status"));
    }
}

