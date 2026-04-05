package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.dto.risk.RiskDecisionRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskMetricStoreTest {

    @Test
    void recordAndQuery_shouldCountMinuteHourDayIpAndDevice() {
        RiskMetricStore store = new RiskMetricStore();
        LocalDateTime time = LocalDateTime.of(2026, 4, 4, 10, 30, 5);

        RiskDecisionRequest req = RiskDecisionRequest.builder()
                .userId(1001L)
                .taskId(10L)
                .eventTime(time)
                .amount(5)
                .ip("10.0.0.1")
                .deviceId("device-a")
                .build();

        store.record(req);
        store.record(req);

        assertEquals(2L, store.getCount1m(1001L, 10L, time));
        assertEquals(2L, store.getCount1h(1001L, 10L, time));
        assertEquals(10L, store.getAmount1d(1001L, 10L, time));
        assertEquals(2L, store.getIpCount1m("10.0.0.1", time));
        assertEquals(2L, store.getDeviceCount1m("device-a", time));
        assertEquals(1L, store.getDistinctDevice1d(1001L, time));
    }

    @Test
    void record_shouldUseDefaultAmountAndHandleEmptyIpDevice() {
        RiskMetricStore store = new RiskMetricStore();
        LocalDateTime time = LocalDateTime.of(2026, 4, 4, 11, 0, 0);

        RiskDecisionRequest req = RiskDecisionRequest.builder()
                .userId(1002L)
                .taskId(20L)
                .eventTime(time)
                .amount(null)
                .ip("")
                .deviceId("")
                .build();

        store.record(req);

        assertEquals(1L, store.getAmount1d(1002L, 20L, time));
        assertEquals(0L, store.getIpCount1m("", time));
        assertEquals(0L, store.getDeviceCount1m("", time));
        assertEquals(0L, store.getDistinctDevice1d(1002L, time));
    }

    @Test
    void record_shouldAcceptNullEventTimeAndNullUserTask() {
        RiskMetricStore store = new RiskMetricStore();
        RiskDecisionRequest req = RiskDecisionRequest.builder()
                .eventTime(null)
                .userId(null)
                .taskId(null)
                .deviceId("device-x")
                .build();

        store.record(req);
    }

    @Test
    void queryMethods_shouldReturnZeroForMissingKeys() {
        RiskMetricStore store = new RiskMetricStore();
        LocalDateTime time = LocalDateTime.of(2026, 4, 4, 12, 0, 0);

        assertEquals(0L, store.getCount1m(1L, 2L, time));
        assertEquals(0L, store.getCount1h(1L, 2L, time));
        assertEquals(0L, store.getAmount1d(1L, 2L, time));
        assertEquals(0L, store.getDistinctDevice1d(null, time));
        assertEquals(0L, store.getIpCount1m(null, time));
        assertEquals(0L, store.getDeviceCount1m(null, time));
    }
}

