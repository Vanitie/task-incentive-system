package com.whu.graduation.taskincentive.engine;

import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionRequest;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;
import com.whu.graduation.taskincentive.event.UserEvent;
import com.whu.graduation.taskincentive.service.RewardService;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionService;
import com.whu.graduation.taskincentive.strategy.stock.StockStrategy;
import com.whu.graduation.taskincentive.strategy.task.TaskStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskEngineTest {

    private TaskEngine taskEngine;
    private TaskStrategy taskStrategy;
    private StockStrategy stockStrategy;
    private UserTaskInstanceService instanceService;
    private TaskConfigService taskConfigService;
    private RewardService rewardService;
    private RiskDecisionService riskDecisionService;
    private RedisTemplate<String, String> redisTemplate;
    private SetOperations<String, String> setOperations;

    @BeforeEach
    public void setUp() throws Exception {
        taskEngine = new TaskEngine();

        taskStrategy = org.mockito.Mockito.mock(TaskStrategy.class);
        stockStrategy = org.mockito.Mockito.mock(StockStrategy.class);
        instanceService = org.mockito.Mockito.mock(UserTaskInstanceService.class);
        taskConfigService = org.mockito.Mockito.mock(TaskConfigService.class);
        rewardService = org.mockito.Mockito.mock(RewardService.class);
        riskDecisionService = org.mockito.Mockito.mock(RiskDecisionService.class);
        redisTemplate = org.mockito.Mockito.mock(RedisTemplate.class);
        setOperations = org.mockito.Mockito.mock(SetOperations.class);

        Map<String, TaskStrategy> taskStrategyMap = new HashMap<>();
        taskStrategyMap.put("ACCUMULATE", taskStrategy);
        Map<String, StockStrategy> stockStrategyMap = new HashMap<>();
        stockStrategyMap.put("LIMITED", stockStrategy);

        setField("taskStrategyMap", taskStrategyMap);
        setField("stockStrategyMap", stockStrategyMap);
        setField("instanceService", instanceService);
        setField("taskConfigService", taskConfigService);
        setField("rewardService", rewardService);
        setField("riskDecisionService", riskDecisionService);
        setField("redisTemplate", redisTemplate);

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    public void processEvent_shouldGrantReward_whenRedisIntersectHitAndPassRisk() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("PASS").build());
        when(stockStrategy.acquireStock(10L, 1)).thenReturn(true);

        taskEngine.processEvent(event);

        ArgumentCaptor<Reward> rewardCaptor = ArgumentCaptor.forClass(Reward.class);
        verify(rewardService, times(1)).grantReward(anyLong(), rewardCaptor.capture());
        assertEquals(20, rewardCaptor.getValue().getAmount());
        assertEquals(1, rewardCaptor.getValue().getStageIndex());
        verify(instanceService, times(1)).updateAndPublish(instance);
    }

    @Test
    public void processEventDirect_shouldUseDbOnlyAndSyncPersist() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(taskConfigService.getTaskIdsByEventTypeDirect("USER_LEARN")).thenReturn(Set.of("10"));
        when(instanceService.selectByUserIdDirect(1001L)).thenReturn(List.of(instance));
        when(taskConfigService.getTaskConfigsByIdsDirect(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluateDirect(any())).thenReturn(RiskDecisionResponse.builder().decision("PASS").build());
        when(stockStrategy.acquireStock(10L, 1)).thenReturn(true);
        when(rewardService.grantRewardDirect(anyLong(), any(Reward.class))).thenReturn(true);
        when(instanceService.updateDirect(instance)).thenReturn(1);

        taskEngine.processEventDirect(event);

        verify(taskConfigService, times(1)).getTaskIdsByEventTypeDirect("USER_LEARN");
        verify(instanceService, times(1)).selectByUserIdDirect(1001L);
        verify(taskConfigService, times(1)).getTaskConfigsByIdsDirect(Set.of(10L));
        verify(instanceService, times(1)).updateDirect(instance);
        verify(instanceService, never()).updateAndPublish(any(UserTaskInstance.class));
        verify(riskDecisionService, times(1)).evaluateDirect(any());
        verify(riskDecisionService, never()).evaluate(any());
        verify(rewardService, times(1)).grantRewardDirect(anyLong(), any(Reward.class));
        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
        verify(setOperations, never()).intersect(any(String.class), any(String.class));
    }

    @Test
    public void processEvent_shouldSkipReward_whenRiskReject() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("REJECT").build());

        taskEngine.processEvent(event);

        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, never()).updateAndPublish(any(UserTaskInstance.class));
    }

    @Test
    public void processEvent_shouldFallbackToAppIntersect_whenRedisFails() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(2);

        when(setOperations.intersect(any(String.class), any(String.class))).thenThrow(new RuntimeException("redis down"));
        when(taskConfigService.getTaskIdsByEventType("USER_LEARN")).thenReturn(Set.of("10", "bad-id"));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("PASS").build());
        when(stockStrategy.acquireStock(10L, 1)).thenReturn(true);

        taskEngine.processEvent(event);

        verify(taskConfigService, times(1)).getTaskIdsByEventType("USER_LEARN");
        verify(rewardService, times(1)).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, times(1)).updateAndPublish(instance);
    }

    @Test
    public void processEvent_shouldSkipReward_whenStockNotEnough() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("PASS").build());
        when(stockStrategy.acquireStock(10L, 1)).thenReturn(false);

        taskEngine.processEvent(event);

        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, times(1)).updateAndPublish(instance);
    }

    @Test
    public void processEvent_shouldGrantDegradedReward_whenRiskDegradePass() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("DEGRADE_PASS").degradeRatio(0.25).build());
        when(stockStrategy.acquireStock(10L, 1)).thenReturn(true);

        taskEngine.processEvent(event);

        ArgumentCaptor<Reward> rewardCaptor = ArgumentCaptor.forClass(Reward.class);
        verify(rewardService, times(1)).grantReward(anyLong(), rewardCaptor.capture());
        assertEquals(5, rewardCaptor.getValue().getAmount());
        verify(instanceService, times(1)).updateAndPublish(instance);
    }

    @Test
    public void processEvent_shouldSkipAll_whenRiskResponseIsNull() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(null);

        taskEngine.processEvent(event);

        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, never()).updateAndPublish(any(UserTaskInstance.class));
    }

    @Test
    public void processEvent_shouldUseHalfRatio_whenDegradeRatioMissing() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("DEGRADE_PASS").build());
        when(stockStrategy.acquireStock(10L, 1)).thenReturn(true);

        taskEngine.processEvent(event);

        ArgumentCaptor<Reward> rewardCaptor = ArgumentCaptor.forClass(Reward.class);
        verify(rewardService, times(1)).grantReward(anyLong(), rewardCaptor.capture());
        assertEquals(10, rewardCaptor.getValue().getAmount());
    }

    @Test
    public void processEvent_shouldSkip_whenTaskNotActive() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        cfg.setStartTime(Date.from(event.getTime().plusHours(1).atZone(ZoneId.systemDefault()).toInstant()));
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));

        taskEngine.processEvent(event);

        verify(taskStrategy, never()).execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class));
        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, never()).updateAndPublish(any(UserTaskInstance.class));
    }

    @Test
    public void processEvent_shouldReturnEarly_whenRedisIntersectHasNoValidTaskId() {
        UserEvent event = buildEvent();

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("bad-id"));

        taskEngine.processEvent(event);

        verify(taskConfigService, never()).getTaskConfigsByIds(any());
        verify(instanceService, never()).selectByUserId(anyLong());
    }

    @Test
    public void processEvent_shouldSkip_whenNoStrategyFound() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        cfg.setTaskType("UNKNOWN");
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));

        taskEngine.processEvent(event);

        verify(taskStrategy, never()).execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class));
        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
    }

    @Test
    public void processEvent_shouldSkip_whenInstanceAlreadyCompleted() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(3);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));

        taskEngine.processEvent(event);

        verify(taskStrategy, never()).execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class));
        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
    }

    @Test
    public void processEvent_shouldBuildRiskRequestWithAllResourceType_whenRewardTypeEmpty() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        cfg.setRewardType("");
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("REVIEW").build());

        taskEngine.processEvent(event);

        ArgumentCaptor<RiskDecisionRequest> requestCaptor = ArgumentCaptor.forClass(RiskDecisionRequest.class);
        verify(riskDecisionService, times(1)).evaluate(requestCaptor.capture());
        assertEquals("ALL", requestCaptor.getValue().getResourceType());
        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
    }

    @Test
    public void processEvent_shouldReturnEarlyInFallback_whenUserInstancesEmpty() {
        UserEvent event = buildEvent();

        when(setOperations.intersect(any(String.class), any(String.class))).thenThrow(new RuntimeException("redis down"));
        when(taskConfigService.getTaskIdsByEventType("USER_LEARN")).thenReturn(Set.of("10"));
        when(instanceService.selectByUserId(1001L)).thenReturn(Collections.emptyList());

        taskEngine.processEvent(event);

        verify(taskConfigService, times(1)).getTaskIdsByEventType("USER_LEARN");
        verify(taskConfigService, never()).getTaskConfigsByIds(any());
        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
    }

    @Test
    public void processEvent_shouldReturnEarlyInFallback_whenEventTaskIdsEmpty() {
        UserEvent event = buildEvent();

        when(setOperations.intersect(any(String.class), any(String.class))).thenThrow(new RuntimeException("redis down"));
        when(taskConfigService.getTaskIdsByEventType("USER_LEARN")).thenReturn(Collections.emptySet());

        taskEngine.processEvent(event);

        verify(instanceService, never()).selectByUserId(anyLong());
        verify(taskConfigService, never()).getTaskConfigsByIds(any());
    }

    @Test
    public void processEvent_shouldContinue_whenTaskConfigMissing() {
        UserEvent event = buildEvent();
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Collections.emptyMap());
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));

        taskEngine.processEvent(event);

        verify(taskStrategy, never()).execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class));
        verify(instanceService, never()).updateAndPublish(any(UserTaskInstance.class));
    }

    @Test
    public void processEvent_shouldSkip_whenInstanceTaskMismatch() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);
        instance.setTaskId(11L);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));

        taskEngine.processEvent(event);

        verify(taskStrategy, never()).execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class));
        verify(instanceService, never()).updateAndPublish(any(UserTaskInstance.class));
    }

    @Test
    public void processEvent_shouldUpdateInstance_whenRewardStagesEmpty() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(Collections.emptyList());

        taskEngine.processEvent(event);

        verify(riskDecisionService, never()).evaluate(any());
        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, times(1)).updateAndPublish(instance);
    }

    @Test
    public void processEvent_shouldGrantReward_whenStockStrategyMissing() throws Exception {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);
        setField("stockStrategyMap", Collections.emptyMap());

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("PASS").build());

        taskEngine.processEvent(event);

        verify(rewardService, times(1)).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, times(1)).updateAndPublish(instance);
    }

    @Test
    public void processEvent_shouldStripRewardPrefix_whenBuildRiskRequest() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        cfg.setRewardType("REWARD_POINT");
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("REVIEW").build());

        taskEngine.processEvent(event);

        ArgumentCaptor<RiskDecisionRequest> requestCaptor = ArgumentCaptor.forClass(RiskDecisionRequest.class);
        verify(riskDecisionService, times(1)).evaluate(requestCaptor.capture());
        assertEquals("POINT", requestCaptor.getValue().getResourceType());
        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
    }

    @Test
    public void processEvent_shouldReturnEarlyInFallback_whenAcceptedTaskIdsEmpty() {
        UserEvent event = buildEvent();
        UserTaskInstance pending = buildInstance(0);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Collections.emptySet());
        when(taskConfigService.getTaskIdsByEventType("USER_LEARN")).thenReturn(Set.of("10"));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(pending));

        taskEngine.processEvent(event);

        verify(taskConfigService, never()).getTaskConfigsByIds(any());
        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
    }

    @Test
    public void processEvent_shouldSkipAll_whenRiskDecisionIsNullString() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision(null).build());

        taskEngine.processEvent(event);

        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, never()).updateAndPublish(any(UserTaskInstance.class));
    }

    @Test
    public void processEvent_shouldSkipReward_whenRiskFreeze() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("FREEZE").build());

        taskEngine.processEvent(event);

        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, never()).updateAndPublish(any(UserTaskInstance.class));
    }

    @Test
    public void processEvent_shouldUpdateInstance_whenRewardStagesNull() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(null);

        taskEngine.processEvent(event);

        verify(riskDecisionService, never()).evaluate(any());
        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, times(1)).updateAndPublish(instance);
    }

    @Test
    public void processEvent_shouldGrantOnlyStockOkStages_whenMultiStages() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1, 2));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("PASS").build());
        when(stockStrategy.acquireStock(10L, 1)).thenReturn(true);
        when(stockStrategy.acquireStock(10L, 2)).thenReturn(false);

        taskEngine.processEvent(event);

        verify(rewardService, times(1)).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, times(1)).updateAndPublish(instance);
    }

    @Test
    public void processEvent_shouldReturnEarlyInFallback_whenTaskIdsNull() {
        UserEvent event = buildEvent();

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Collections.emptySet());
        when(taskConfigService.getTaskIdsByEventType("USER_LEARN")).thenReturn(null);

        taskEngine.processEvent(event);

        verify(instanceService, never()).selectByUserId(anyLong());
        verify(taskConfigService, never()).getTaskConfigsByIds(any());
    }

    @Test
    public void processEvent_shouldReturnEarlyInFallback_whenUserInstancesNull() {
        UserEvent event = buildEvent();

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Collections.emptySet());
        when(taskConfigService.getTaskIdsByEventType("USER_LEARN")).thenReturn(Set.of("10"));
        when(instanceService.selectByUserId(1001L)).thenReturn(null);

        taskEngine.processEvent(event);

        verify(taskConfigService, never()).getTaskConfigsByIds(any());
    }

    @Test
    public void processEvent_shouldReturnEarlyInFallback_whenParsedEventTaskIdsEmpty() {
        UserEvent event = buildEvent();
        UserTaskInstance instance = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Collections.emptySet());
        when(taskConfigService.getTaskIdsByEventType("USER_LEARN")).thenReturn(Set.of("bad-id"));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(instance));

        taskEngine.processEvent(event);

        verify(taskConfigService, never()).getTaskConfigsByIds(any());
        verify(rewardService, never()).grantReward(anyLong(), any(Reward.class));
    }

    @Test
    public void processEvent_shouldSkipAll_whenRedisIntersectHitButUserInstancesNull() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(null);

        taskEngine.processEvent(event);

        verify(taskStrategy, never()).execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class));
        verify(instanceService, never()).updateAndPublish(any(UserTaskInstance.class));
    }

    @Test
    public void taskActiveAndInstanceValidHelpers_shouldCoverNullAndBoundaryBranches() throws Exception {
        Method isTaskActive = TaskEngine.class.getDeclaredMethod("isTaskActive", TaskConfig.class, LocalDateTime.class);
        isTaskActive.setAccessible(true);
        Method isInstanceValid = TaskEngine.class.getDeclaredMethod("isInstanceValid", UserTaskInstance.class, Long.class, Long.class);
        isInstanceValid.setAccessible(true);
        Method toRiskResourceType = TaskEngine.class.getDeclaredMethod("toRiskResourceType", String.class);
        toRiskResourceType.setAccessible(true);

        TaskConfig cfgStatusNull = buildTaskConfig(10L);
        cfgStatusNull.setStatus(null);
        assertFalse((Boolean) isTaskActive.invoke(taskEngine, cfgStatusNull, null));

        TaskConfig cfgStatusInvalid = buildTaskConfig(10L);
        cfgStatusInvalid.setStatus(2);
        assertFalse((Boolean) isTaskActive.invoke(taskEngine, cfgStatusInvalid, null));

        TaskConfig cfgEnded = buildTaskConfig(10L);
        cfgEnded.setEndTime(new Date(System.currentTimeMillis() - 1000));
        assertFalse((Boolean) isTaskActive.invoke(taskEngine, cfgEnded, null));

        TaskConfig cfgStarted = buildTaskConfig(10L);
        cfgStarted.setStartTime(new Date(System.currentTimeMillis() - 1000));
        assertTrue((Boolean) isTaskActive.invoke(taskEngine, cfgStarted, LocalDateTime.now()));

        TaskConfig cfgNotEnded = buildTaskConfig(10L);
        cfgNotEnded.setEndTime(new Date(System.currentTimeMillis() + 60_000));
        assertTrue((Boolean) isTaskActive.invoke(taskEngine, cfgNotEnded, LocalDateTime.now()));

        UserTaskInstance nullUser = buildInstance(1);
        nullUser.setUserId(null);
        assertFalse((Boolean) isInstanceValid.invoke(taskEngine, nullUser, 1001L, 10L));

        UserTaskInstance mismatchedUser = buildInstance(1);
        mismatchedUser.setUserId(999L);
        assertFalse((Boolean) isInstanceValid.invoke(taskEngine, mismatchedUser, 1001L, 10L));

        UserTaskInstance nullTask = buildInstance(1);
        nullTask.setTaskId(null);
        assertFalse((Boolean) isInstanceValid.invoke(taskEngine, nullTask, 1001L, 10L));

        UserTaskInstance wrongTask = buildInstance(1);
        wrongTask.setTaskId(11L);
        assertFalse((Boolean) isInstanceValid.invoke(taskEngine, wrongTask, 1001L, 10L));

        UserTaskInstance nullStatus = buildInstance(null);
        assertFalse((Boolean) isInstanceValid.invoke(taskEngine, nullStatus, 1001L, 10L));

        UserTaskInstance cancelled = buildInstance(4);
        assertFalse((Boolean) isInstanceValid.invoke(taskEngine, cancelled, 1001L, 10L));

        assertEquals("ALL", toRiskResourceType.invoke(taskEngine, new Object[]{null}));
        assertTrue((Boolean) isInstanceValid.invoke(taskEngine, buildInstance(1), 1001L, 10L));
    }

    @Test
    public void processEvent_shouldFilterInvalidInstancesInBothRedisAndFallbackPaths() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);

        UserTaskInstance statusNull = buildInstance(null);
        UserTaskInstance statusZero = buildInstance(0);
        UserTaskInstance taskNull = buildInstance(1);
        taskNull.setTaskId(null);
        UserTaskInstance valid = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Set.of("10"));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(statusNull, statusZero, taskNull, valid));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("PASS").build());
        when(stockStrategy.acquireStock(10L, 1)).thenReturn(true);

        taskEngine.processEvent(event);

        verify(rewardService, times(1)).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, times(1)).updateAndPublish(valid);
    }

    @Test
    public void processEvent_shouldFilterNullStatusInFallbackAcceptedTaskIds() {
        UserEvent event = buildEvent();
        TaskConfig cfg = buildTaskConfig(10L);

        UserTaskInstance statusNull = buildInstance(null);
        UserTaskInstance statusZero = buildInstance(0);
        UserTaskInstance taskNull = buildInstance(1);
        taskNull.setTaskId(null);
        UserTaskInstance valid = buildInstance(1);

        when(setOperations.intersect(any(String.class), any(String.class))).thenReturn(Collections.emptySet());
        when(taskConfigService.getTaskIdsByEventType("USER_LEARN")).thenReturn(Set.of("10"));
        when(instanceService.selectByUserId(1001L)).thenReturn(List.of(statusNull, statusZero, taskNull, valid));
        when(taskConfigService.getTaskConfigsByIds(Set.of(10L))).thenReturn(Map.of(10L, cfg));
        when(taskStrategy.execute(any(UserEvent.class), any(TaskConfig.class), any(UserTaskInstance.class))).thenReturn(List.of(1));
        when(riskDecisionService.evaluate(any())).thenReturn(RiskDecisionResponse.builder().decision("PASS").build());
        when(stockStrategy.acquireStock(10L, 1)).thenReturn(true);

        taskEngine.processEvent(event);

        verify(rewardService, times(1)).grantReward(anyLong(), any(Reward.class));
        verify(instanceService, times(1)).updateAndPublish(valid);
    }

    private UserEvent buildEvent() {
        UserEvent event = new UserEvent();
        event.setUserId(1001L);
        event.setEventType("USER_LEARN");
        event.setValue(1);
        event.setTime(LocalDateTime.now());
        event.setRequestId("req-1");
        return event;
    }

    private TaskConfig buildTaskConfig(Long taskId) {
        TaskConfig cfg = new TaskConfig();
        cfg.setId(taskId);
        cfg.setTaskType("ACCUMULATE");
        cfg.setStockType("LIMITED");
        cfg.setRewardType("POINT");
        cfg.setRewardValue(20);
        cfg.setStatus(1);
        return cfg;
    }

    private UserTaskInstance buildInstance(Integer status) {
        UserTaskInstance instance = new UserTaskInstance();
        instance.setId(1L);
        instance.setTaskId(10L);
        instance.setUserId(1001L);
        instance.setStatus(status);
        return instance;
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = TaskEngine.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(taskEngine, value);
    }
}

