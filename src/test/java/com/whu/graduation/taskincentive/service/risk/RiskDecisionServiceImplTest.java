package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.constant.RiskConstants;
import com.whu.graduation.taskincentive.dao.mapper.RiskBlacklistMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskDecisionLogMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskQuotaMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskRuleMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskWhitelistMapper;
import com.whu.graduation.taskincentive.dao.mapper.RewardFreezeRecordMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionRequest;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;
import com.whu.graduation.taskincentive.mq.RiskDecisionPersistMessage;
import com.whu.graduation.taskincentive.mq.RiskDecisionPersistProducer;
import com.whu.graduation.taskincentive.service.risk.impl.RiskDecisionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

public class RiskDecisionServiceImplTest {

    private RiskDecisionEngine decisionEngine;
    private RiskMetricStore metricStore;
    private RiskCacheStore cacheStore;
    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RiskDecisionPersistProducer persistProducer;
    private RiskRuleMapper riskRuleMapper;
    private RiskQuotaMapper riskQuotaMapper;
    private RiskWhitelistMapper riskWhitelistMapper;
    private RiskBlacklistMapper riskBlacklistMapper;
    private RiskDecisionLogMapper riskDecisionLogMapper;
    private RewardFreezeRecordMapper rewardFreezeRecordMapper;
    private UserRewardRecordMapper userRewardRecordMapper;
    private RiskDecisionServiceImpl service;

    @BeforeEach
    public void setUp() {
        decisionEngine = mock(RiskDecisionEngine.class);
        metricStore = mock(RiskMetricStore.class);
        cacheStore = new RiskCacheStore();
        redisTemplate = mockRedisTemplate();
        valueOperations = mockValueOperations();
        persistProducer = mock(RiskDecisionPersistProducer.class);
        riskRuleMapper = mock(RiskRuleMapper.class);
        riskQuotaMapper = mock(RiskQuotaMapper.class);
        riskWhitelistMapper = mock(RiskWhitelistMapper.class);
        riskBlacklistMapper = mock(RiskBlacklistMapper.class);
        riskDecisionLogMapper = mock(RiskDecisionLogMapper.class);
        rewardFreezeRecordMapper = mock(RewardFreezeRecordMapper.class);
        userRewardRecordMapper = mock(UserRewardRecordMapper.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        service = new RiskDecisionServiceImpl(decisionEngine, metricStore, cacheStore, redisTemplate, persistProducer);
        setField(service, "riskRuleMapper", riskRuleMapper);
        setField(service, "riskQuotaMapper", riskQuotaMapper);
        setField(service, "riskWhitelistMapper", riskWhitelistMapper);
        setField(service, "riskBlacklistMapper", riskBlacklistMapper);
        setField(service, "riskDecisionLogMapper", riskDecisionLogMapper);
        setField(service, "rewardFreezeRecordMapper", rewardFreezeRecordMapper);
        setField(service, "userRewardRecordMapper", userRewardRecordMapper);

        when(riskDecisionLogMapper.selectCount(any())).thenReturn(0L);
        when(userRewardRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(riskRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(riskQuotaMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(riskWhitelistMapper.selectOne(any())).thenReturn(null);
        when(riskBlacklistMapper.selectOne(any())).thenReturn(null);
        when(riskDecisionLogMapper.selectOne(any())).thenReturn(null);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private RedisTemplate<String, String> mockRedisTemplate() {
        return mock(RedisTemplate.class);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> mockValueOperations() {
        return mock(ValueOperations.class);
    }

    @Test
    public void evaluate_shouldRejectReplay_whenDedupFails() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(false);

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("REJECT", resp.getDecision());
        assertEquals(RiskConstants.REASON_REPLAY, resp.getReasonCode());
        verify(metricStore, never()).record(any());
    }

    @Test
    public void evaluateDirect_shouldPersistToDb_andSkipKafkaProducer() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(7).build()
        );

        RiskDecisionResponse resp = service.evaluateDirect(baseRequest());

        assertEquals("PASS", resp.getDecision());
        verify(riskDecisionLogMapper, times(1)).insert(any());
        verify(rewardFreezeRecordMapper, never()).insert(any());
        verify(persistProducer, never()).send(anyString(), any());
    }

    @Test
    public void evaluateDirect_shouldInsertFreezeRecord_whenEngineReturnsNull() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(null);

        RiskDecisionResponse resp = service.evaluateDirect(baseRequest());

        assertEquals("FREEZE", resp.getDecision());
        verify(riskDecisionLogMapper, times(1)).insert(any());
        verify(rewardFreezeRecordMapper, times(1)).insert(any());
        verify(persistProducer, never()).send(anyString(), any());
    }

    @Test
    public void evaluate_shouldPass_whenRequestIdMissing() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(5).build()
        );
        RiskDecisionRequest req = baseRequest();
        req.setRequestId(null);

        RiskDecisionResponse resp = service.evaluate(req);

        assertEquals("PASS", resp.getDecision());
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES));
    }

    @Test
    public void evaluate_shouldPass_whenRequestIdEmptyString() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(5).build()
        );
        RiskDecisionRequest req = baseRequest();
        req.setRequestId("");

        RiskDecisionResponse resp = service.evaluate(req);

        assertEquals("PASS", resp.getDecision());
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES));
    }

    @Test
    public void evaluate_shouldPass_whenDedupRedisReturnsNull() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(null);
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(6).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
        assertEquals("PASS", resp.getReasonCode());
    }

    @Test
    public void evaluate_shouldPass_whenHitWhitelist() {
        cacheStore.refreshWhitelists(Map.of(RiskCacheStore.listKey("USER"), Set.of("1001")));

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
        assertEquals(RiskConstants.REASON_WHITELIST, resp.getReasonCode());
        verify(persistProducer).send(eq("1001"), any());
    }

    @Test
    public void evaluate_shouldPass_whenWhitelistHitsByDevice() {
        cacheStore.refreshWhitelists(Map.of(RiskCacheStore.listKey("DEVICE"), Set.of("device-1")));

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
        assertEquals(RiskConstants.REASON_WHITELIST, resp.getReasonCode());
    }

    @Test
    public void evaluate_shouldReject_whenHitBlacklist() {
        cacheStore.refreshBlacklists(Map.of(RiskCacheStore.listKey("USER"), Set.of("1001")));

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("REJECT", resp.getDecision());
        assertEquals(RiskConstants.REASON_BLACKLIST, resp.getReasonCode());
    }

    @Test
    public void evaluate_shouldReject_whenBlacklistHitsByIp() {
        cacheStore.refreshBlacklists(Map.of(RiskCacheStore.listKey("IP"), Set.of("10.0.0.1")));

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("REJECT", resp.getDecision());
        assertEquals(RiskConstants.REASON_BLACKLIST, resp.getReasonCode());
    }

    @Test
    public void evaluate_shouldReject_whenQuotaExceeded() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("USER")
                .scopeId("1001")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(0)
                .build();
        cacheStore.refreshQuotas(Map.of("q1", quota));
        when(valueOperations.increment(anyString())).thenReturn(1L);

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("REJECT", resp.getDecision());
        assertEquals(RiskConstants.REASON_QUOTA_EXCEEDED, resp.getReasonCode());
    }

    @Test
    public void evaluate_shouldBackfillHitRules_whenRuleReasonPresentButHitsEmpty() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder()
                        .decision("REJECT")
                        .reasonCode("DEVICE_BURST")
                        .hitRules(Collections.emptyList())
                        .riskScore(80)
                        .build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("REJECT", resp.getDecision());
        assertNotNull(resp.getHitRules());
        assertFalse(resp.getHitRules().isEmpty());
        assertEquals("DEVICE_BURST", resp.getHitRules().get(0).getRuleName());

        ArgumentCaptor<RiskDecisionPersistMessage> messageCaptor = ArgumentCaptor.forClass(RiskDecisionPersistMessage.class);
        verify(persistProducer).send(eq("1001"), messageCaptor.capture());
        String hitRulesJson = messageCaptor.getValue().getDecisionLog().getHitRules();
        assertNotNull(hitRulesJson);
        assertTrue(hitRulesJson.contains("DEVICE_BURST"));
    }

    @Test
    public void evaluate_shouldSkipQuota_whenScopeDoesNotMatch() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("TASK")
                .scopeId("999")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(0)
                .build();
        cacheStore.refreshQuotas(Map.of("q1", quota));
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(4).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
    }

    @Test
    public void evaluate_shouldReject_whenGlobalMinuteQuotaExceeded() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("GLOBAL")
                .scopeId("ALL")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("MINUTE")
                .limitValue(0)
                .build();
        cacheStore.refreshQuotas(Map.of("q-minute", quota));
        when(valueOperations.increment(anyString())).thenReturn(1L);

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("REJECT", resp.getDecision());
        assertEquals(RiskConstants.REASON_QUOTA_EXCEEDED, resp.getReasonCode());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        assertTrue(keyCaptor.getValue().contains(":MINUTE:"));
    }

    @Test
    public void evaluate_shouldReject_whenTaskHourQuotaExceeded_withAllResourceId() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("TASK")
                .scopeId("10")
                .resourceType("POINT")
                .resourceId("ALL")
                .periodType("HOUR")
                .limitValue(0)
                .build();
        cacheStore.refreshQuotas(Map.of("q-hour", quota));
        when(valueOperations.increment(anyString())).thenReturn(1L);

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("REJECT", resp.getDecision());
        assertEquals(RiskConstants.REASON_QUOTA_EXCEEDED, resp.getReasonCode());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        assertTrue(keyCaptor.getValue().contains(":HOUR:"));
    }

    @Test
    public void evaluate_shouldIgnoreQuota_whenUnknownScopeType() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("UNKNOWN")
                .scopeId("ALL")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(0)
                .build();
        cacheStore.refreshQuotas(Map.of("q-unknown", quota));
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(3).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
    }

    @Test
    public void evaluate_shouldIgnoreQuota_whenLimitValueIsNull() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("USER")
                .scopeId("1001")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(null)
                .build();
        cacheStore.refreshQuotas(Map.of("q-null", quota));
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(2).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    public void evaluate_shouldFreeze_whenEngineReturnsNull() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(null);

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("FREEZE", resp.getDecision());
        assertEquals(RiskConstants.REASON_DEFAULT, resp.getReasonCode());
        verify(persistProducer).send(eq("1001"), any());
    }

    @Test
    public void evaluate_shouldFreeze_whenEngineReturnsUnknownDecision() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("UNKNOWN").riskScore(33).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("FREEZE", resp.getDecision());
        assertEquals(RiskConstants.REASON_DEFAULT, resp.getReasonCode());
        assertNotNull(resp.getTraceId());
    }

    @Test
    public void evaluate_shouldPassThroughDecision_whenEngineReturnsPass() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(8).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
        assertEquals("PASS", resp.getReasonCode());
        verify(metricStore).record(any());
    }

    @Test
    public void evaluate_shouldBuildFreezeRecordInPersistMessage() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(null);

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("FREEZE", resp.getDecision());
        ArgumentCaptor<RiskDecisionPersistMessage> captor = ArgumentCaptor.forClass(RiskDecisionPersistMessage.class);
        verify(persistProducer).send(eq("1001"), captor.capture());
        RiskDecisionPersistMessage message = captor.getValue();
        assertEquals("FREEZE", message.getDecisionLog().getDecision());
        assertNotNull(message.getFreezeRecord());
        assertEquals(RiskConstants.REASON_DEFAULT, message.getFreezeRecord().getFreezeReason());
    }

    @Test
    public void evaluate_shouldNotBuildFreezeRecordForPass() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(8).build()
        );

        service.evaluate(baseRequest());

        ArgumentCaptor<RiskDecisionPersistMessage> captor = ArgumentCaptor.forClass(RiskDecisionPersistMessage.class);
        verify(persistProducer).send(eq("1001"), captor.capture());
        assertNull(captor.getValue().getFreezeRecord());
    }

    @Test
    public void evaluate_shouldReturnDecision_whenPersistProducerThrows() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(8).build()
        );
        doThrow(new RuntimeException("mq-down")).when(persistProducer).send(anyString(), any());

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
        assertEquals("PASS", resp.getReasonCode());
    }

    @Test
    public void evaluate_shouldFreeze_whenUnexpectedException() {
        when(metricStore.getCount1m(any(), any(), any())).thenThrow(new RuntimeException("boom"));
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(1).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("FREEZE", resp.getDecision());
        assertEquals(RiskConstants.REASON_DEFAULT, resp.getReasonCode());
    }

    @Test
    public void evaluate_shouldBuildContextWithDefaultAmountAndExt() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(7).build()
        );
        RiskDecisionRequest req = baseRequest();
        req.setAmount(null);
        req.setExt(Map.of("scene", "S1"));

        service.evaluate(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> ctxCaptor = ArgumentCaptor.forClass(Map.class);
        verify(decisionEngine).evaluateRules(any(), ctxCaptor.capture());
        assertEquals(1, ctxCaptor.getValue().get("amount"));
        assertEquals("S1", ctxCaptor.getValue().get("scene"));
    }

    @Test
    public void evaluate_shouldUseUserKeyZero_whenRequestUserIdIsNull() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(5).build()
        );
        RiskDecisionRequest req = baseRequest();
        req.setUserId(null);

        RiskDecisionResponse resp = service.evaluate(req);

        assertEquals("PASS", resp.getDecision());
        verify(persistProducer).send(eq("0"), any());
    }

    @Test
    public void evaluate_shouldFreeze_whenDecisionLowerCaseCannotParse() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("pass").riskScore(9).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("FREEZE", resp.getDecision());
        assertEquals(RiskConstants.REASON_DEFAULT, resp.getReasonCode());
    }

    @Test
    public void evaluate_shouldPass_whenQuotaIncrementReturnsNull() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("USER")
                .scopeId("1001")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(0)
                .build();
        cacheStore.refreshQuotas(Map.of("q-null-count", quota));
        when(valueOperations.increment(anyString())).thenReturn(null);
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(2).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
    }

    @Test
    public void evaluate_shouldIgnoreUserQuota_whenUserIdMissing() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("USER")
                .scopeId("ALL")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(0)
                .build();
        cacheStore.refreshQuotas(Map.of("q-user", quota));
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(2).build()
        );
        RiskDecisionRequest req = baseRequest();
        req.setUserId(null);

        RiskDecisionResponse resp = service.evaluate(req);

        assertEquals("PASS", resp.getDecision());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    public void evaluate_shouldIgnoreTaskQuota_whenTaskIdMissing() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("TASK")
                .scopeId("ALL")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(0)
                .build();
        cacheStore.refreshQuotas(Map.of("q-task", quota));
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(2).build()
        );
        RiskDecisionRequest req = baseRequest();
        req.setTaskId(null);

        RiskDecisionResponse resp = service.evaluate(req);

        assertEquals("PASS", resp.getDecision());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    public void evaluate_shouldIgnoreQuota_whenResourceTypeOrIdMismatch() {
        RiskQuota quotaTypeMismatch = RiskQuota.builder()
                .scopeType("USER")
                .scopeId("1001")
                .resourceType("BADGE")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(0)
                .build();
        RiskQuota quotaIdMismatch = RiskQuota.builder()
                .scopeType("USER")
                .scopeId("1001")
                .resourceType("POINT")
                .resourceId("999")
                .periodType("DAY")
                .limitValue(0)
                .build();
        cacheStore.refreshQuotas(Map.of("q-type", quotaTypeMismatch, "q-id", quotaIdMismatch));
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(2).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    public void evaluate_shouldIgnoreGlobalQuota_whenScopeIdNotAll() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("GLOBAL")
                .scopeId("NOT_ALL")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(0)
                .build();
        cacheStore.refreshQuotas(Map.of("q-global", quota));
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(2).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    public void evaluate_shouldFreeze_whenEngineDecisionIsNull() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision(null).riskScore(1).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("FREEZE", resp.getDecision());
        assertEquals(RiskConstants.REASON_DEFAULT, resp.getReasonCode());
    }

    @Test
    public void evaluate_shouldDefaultRiskScoreToZero_whenEngineRiskScoreMissing() {
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(null).build()
        );

        RiskDecisionResponse resp = service.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
        assertEquals(0, resp.getRiskScore());

        ArgumentCaptor<RiskDecisionPersistMessage> captor = ArgumentCaptor.forClass(RiskDecisionPersistMessage.class);
        verify(persistProducer).send(eq("1001"), captor.capture());
        assertEquals(0, captor.getValue().getDecisionLog().getRiskScore());
    }

    @Test
    public void evaluate_shouldPass_whenQuotaUsesDefaultScopeResourceAndPeriod() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("USER")
                .scopeId(null)
                .resourceType(null)
                .resourceId(null)
                .periodType("")
                .limitValue(2)
                .build();
        cacheStore.refreshQuotas(Map.of("q-default", quota));
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(6).build()
        );

        RiskDecisionRequest req = baseRequest();
        req.setEventTime(null);
        req.setResourceType("");
        req.setResourceId(null);

        RiskDecisionResponse resp = service.evaluate(req);

        assertEquals("PASS", resp.getDecision());
        verify(valueOperations).increment(anyString());
    }

    @Test
    public void evaluate_shouldPass_whenWhitelistAndBlacklistNoMatchAndIdentifiersNull() {
        cacheStore.refreshWhitelists(Map.of(
                RiskCacheStore.listKey("USER"), Set.of("u-x"),
                RiskCacheStore.listKey("DEVICE"), Set.of("d-x"),
                RiskCacheStore.listKey("IP"), Set.of("i-x")
        ));
        cacheStore.refreshBlacklists(Map.of(
                RiskCacheStore.listKey("USER"), Set.of("u-y")
        ));
        when(decisionEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(2).build()
        );

        RiskDecisionRequest req = baseRequest();
        req.setUserId(null);
        req.setDeviceId(null);
        req.setIp(null);

        RiskDecisionResponse resp = service.evaluate(req);

        assertEquals("PASS", resp.getDecision());
    }

    @Test
    public void privateHelpers_shouldCoverToJsonNormalizeAndParseFallbackBranches() throws Exception {
        Method toJson = RiskDecisionServiceImpl.class.getDeclaredMethod("toJson", Object.class);
        toJson.setAccessible(true);
        Method parseAction = RiskDecisionServiceImpl.class.getDeclaredMethod("parseAction", String.class);
        parseAction.setAccessible(true);
        Method normalizeType = RiskDecisionServiceImpl.class.getDeclaredMethod("normalizeResourceType", String.class);
        normalizeType.setAccessible(true);
        Method normalizeId = RiskDecisionServiceImpl.class.getDeclaredMethod("normalizeResourceId", String.class);
        normalizeId.setAccessible(true);
        Method hitList = RiskDecisionServiceImpl.class.getDeclaredMethod("hitList", Map.class, RiskDecisionRequest.class);
        hitList.setAccessible(true);

        Object badJson = new Object() {
            public String getExplode() {
                throw new RuntimeException("boom");
            }

            @Override
            public String toString() {
                return "bad-json";
            }
        };
        String jsonFallback = (String) toJson.invoke(service, badJson);
        assertEquals("bad-json", jsonFallback);

        assertNull(parseAction.invoke(service, new Object[]{null}));
        assertEquals("ALL", normalizeType.invoke(service, ""));
        assertEquals("ALL", normalizeId.invoke(service, new Object[]{null}));
        assertFalse((Boolean) hitList.invoke(service, null, baseRequest()));
    }

    @Test
    public void privateHelpers_shouldCoverRemainingQuotaAndListBranches() throws Exception {
        Method hitList = RiskDecisionServiceImpl.class.getDeclaredMethod("hitList", Map.class, RiskDecisionRequest.class);
        hitList.setAccessible(true);
        Method checkQuota = RiskDecisionServiceImpl.class.getDeclaredMethod("checkQuota", RiskDecisionRequest.class, Map.class);
        checkQuota.setAccessible(true);
        Method isQuotaApplicable = RiskDecisionServiceImpl.class.getDeclaredMethod("isQuotaApplicable", RiskQuota.class, RiskDecisionRequest.class, String.class, String.class);
        isQuotaApplicable.setAccessible(true);
        Method consumeQuota = RiskDecisionServiceImpl.class.getDeclaredMethod("consumeQuota", RiskQuota.class, LocalDateTime.class);
        consumeQuota.setAccessible(true);
        Method normalizeId = RiskDecisionServiceImpl.class.getDeclaredMethod("normalizeResourceId", String.class);
        normalizeId.setAccessible(true);

        RiskDecisionRequest req = baseRequest();
        req.setUserId(9999L);
        req.setDeviceId("device-hit");
        req.setIp("1.1.1.1");
        Map<String, Set<String>> lists = Map.of(
                RiskCacheStore.listKey("USER"), Set.of("not-hit"),
                RiskCacheStore.listKey("DEVICE"), Set.of("device-hit"),
                RiskCacheStore.listKey("IP"), Set.of("1.1.1.1")
        );
        assertTrue((Boolean) hitList.invoke(service, lists, req));

        RiskDecisionRequest applyReq = baseRequest();
        RiskQuota userSpecific = RiskQuota.builder().scopeType("USER").scopeId("1001").resourceType("POINT").resourceId("10").limitValue(1).build();
        assertTrue((Boolean) isQuotaApplicable.invoke(service, userSpecific, applyReq, "POINT", "10"));

        RiskQuota taskSpecific = RiskQuota.builder().scopeType("TASK").scopeId("10").resourceType("POINT").resourceId("10").limitValue(1).build();
        assertTrue((Boolean) isQuotaApplicable.invoke(service, taskSpecific, applyReq, "POINT", "10"));

        assertFalse((Boolean) isQuotaApplicable.invoke(service, null, applyReq, "POINT", "10"));
        assertTrue((Boolean) checkQuota.invoke(service, applyReq, null));

        RiskQuota consumeNullScope = RiskQuota.builder()
                .scopeType(null)
                .scopeId("ALL")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType(null)
                .limitValue(5)
                .build();
        assertTrue((Boolean) consumeQuota.invoke(service, consumeNullScope, LocalDateTime.now()));

        assertEquals("ALL", normalizeId.invoke(service, ""));
    }

    @Test
    public void evaluate_shouldPass_whenCacheStoreReturnsNullQuotas() {
        RiskCacheStore nullQuotaStore = mock(RiskCacheStore.class);
        when(nullQuotaStore.getWhitelists()).thenReturn(Map.of());
        when(nullQuotaStore.getBlacklists()).thenReturn(Map.of());
        when(nullQuotaStore.getQuotas()).thenReturn(null);
        when(nullQuotaStore.getActiveRules()).thenReturn(java.util.Collections.emptyList());

        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(true);

        RiskDecisionEngine localEngine = mock(RiskDecisionEngine.class);
        when(localEngine.evaluateRules(any(), any())).thenReturn(
                RiskDecisionResponse.builder().decision("PASS").reasonCode("PASS").riskScore(5).build()
        );

        RiskDecisionServiceImpl localService = new RiskDecisionServiceImpl(localEngine, metricStore, nullQuotaStore, redis, persistProducer);
        RiskDecisionResponse resp = localService.evaluate(baseRequest());

        assertEquals("PASS", resp.getDecision());
        verify(ops, never()).increment(anyString());
    }

    @Test
    public void privateHelpers_shouldCoverRemainingHitListAndScopeOutcomeBranches() throws Exception {
        Method hitList = RiskDecisionServiceImpl.class.getDeclaredMethod("hitList", Map.class, RiskDecisionRequest.class);
        hitList.setAccessible(true);
        Method isQuotaApplicable = RiskDecisionServiceImpl.class.getDeclaredMethod("isQuotaApplicable", RiskQuota.class, RiskDecisionRequest.class, String.class, String.class);
        isQuotaApplicable.setAccessible(true);

        RiskDecisionRequest reqNoDeviceKey = baseRequest();
        reqNoDeviceKey.setDeviceId("device-x");
        reqNoDeviceKey.setIp("ip-x");
        Map<String, Set<String>> noDeviceNoIpLists = Map.of(
                RiskCacheStore.listKey("USER"), Set.of("not-match")
        );
        assertFalse((Boolean) hitList.invoke(service, noDeviceNoIpLists, reqNoDeviceKey));

        RiskDecisionRequest reqIpHit = baseRequest();
        reqIpHit.setUserId(999L);
        reqIpHit.setDeviceId("no-hit");
        reqIpHit.setIp("9.9.9.9");
        Map<String, Set<String>> ipHitLists = Map.of(
                RiskCacheStore.listKey("USER"), Set.of("u-no"),
                RiskCacheStore.listKey("DEVICE"), Set.of("d-no"),
                RiskCacheStore.listKey("IP"), Set.of("9.9.9.9")
        );
        assertTrue((Boolean) hitList.invoke(service, ipHitLists, reqIpHit));

        RiskDecisionRequest reqOnlyIp = baseRequest();
        reqOnlyIp.setUserId(null);
        reqOnlyIp.setDeviceId(null);
        reqOnlyIp.setIp("8.8.8.8");
        Map<String, Set<String>> onlyIpLists = Map.of(
                RiskCacheStore.listKey("IP"), Set.of("8.8.8.8")
        );
        assertTrue((Boolean) hitList.invoke(service, onlyIpLists, reqOnlyIp));

        RiskDecisionRequest req = baseRequest();
        RiskQuota userMismatch = RiskQuota.builder().scopeType("USER").scopeId("1002").resourceType("POINT").resourceId("10").limitValue(1).build();
        assertFalse((Boolean) isQuotaApplicable.invoke(service, userMismatch, req, "POINT", "10"));

        RiskQuota taskMismatch = RiskQuota.builder().scopeType("TASK").scopeId("11").resourceType("POINT").resourceId("10").limitValue(1).build();
        assertFalse((Boolean) isQuotaApplicable.invoke(service, taskMismatch, req, "POINT", "10"));

        RiskQuota taskAll = RiskQuota.builder().scopeType("TASK").scopeId("ALL").resourceType("POINT").resourceId("10").limitValue(1).build();
        assertTrue((Boolean) isQuotaApplicable.invoke(service, taskAll, req, "POINT", "10"));

        RiskDecisionRequest reqIpMiss = baseRequest();
        reqIpMiss.setUserId(null);
        reqIpMiss.setDeviceId(null);
        reqIpMiss.setIp("2.2.2.2");
        Map<String, Set<String>> ipMissLists = Map.of(
                RiskCacheStore.listKey("IP"), Set.of("3.3.3.3")
        );
        assertFalse((Boolean) hitList.invoke(service, ipMissLists, reqIpMiss));
    }

    @Test
    public void privateHelpers_shouldCoverConsumeQuotaWhenTtlNonPositive() throws Exception {
        Method consumeQuota = RiskDecisionServiceImpl.class.getDeclaredMethod("consumeQuota", RiskQuota.class, LocalDateTime.class);
        consumeQuota.setAccessible(true);

        RiskQuota quota = RiskQuota.builder()
                .scopeType("USER")
                .scopeId("1001")
                .resourceType("POINT")
                .resourceId("10")
                .periodType("DAY")
                .limitValue(10)
                .build();

        TimeZone oldTz = TimeZone.getDefault();
        try {
            // Pacific/Apia skipped 2011-12-30; this can make next-day boundary diff become non-positive.
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Apia"));
            Boolean ok = (Boolean) consumeQuota.invoke(service, quota, LocalDateTime.of(2011, 12, 30, 0, 0));
            assertTrue(ok);
        } finally {
            TimeZone.setDefault(oldTz);
        }
    }

    private RiskDecisionRequest baseRequest() {
        return RiskDecisionRequest.builder()
                .requestId("req-1")
                .eventId("evt-1")
                .userId(1001L)
                .taskId(10L)
                .eventType("USER_LEARN")
                .eventTime(LocalDateTime.now())
                .amount(10)
                .resourceType("POINT")
                .resourceId("10")
                .deviceId("device-1")
                .ip("10.0.0.1")
                .channel("test")
                .build();
    }

    @Test
    public void privateHelpers_shouldCoverNormalizeResourceAndParseActionBranches() throws Exception {
        Method normalizeResourceType = RiskDecisionServiceImpl.class.getDeclaredMethod("normalizeResourceType", String.class);
        normalizeResourceType.setAccessible(true);
        assertEquals("ALL", normalizeResourceType.invoke(service, new Object[]{null}));
        assertEquals("ALL", normalizeResourceType.invoke(service, ""));
        assertEquals("POINT", normalizeResourceType.invoke(service, "POINT"));

        Method normalizeResourceId = RiskDecisionServiceImpl.class.getDeclaredMethod("normalizeResourceId", String.class);
        normalizeResourceId.setAccessible(true);
        assertEquals("ALL", normalizeResourceId.invoke(service, new Object[]{null}));
        assertEquals("ALL", normalizeResourceId.invoke(service, ""));
        assertEquals("10", normalizeResourceId.invoke(service, "10"));

        Method parseAction = RiskDecisionServiceImpl.class.getDeclaredMethod("parseAction", String.class);
        parseAction.setAccessible(true);
        assertNull(parseAction.invoke(service, new Object[]{null}));
        assertNull(parseAction.invoke(service, "NOT_EXIST"));
        Object pass = parseAction.invoke(service, "PASS");
        assertNotNull(pass);
        assertEquals("PASS", pass.toString());
    }
}
