package com.whu.graduation.taskincentive.strategy.task;

import com.alibaba.fastjson.JSON;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.ContinuousExtraData;
import com.whu.graduation.taskincentive.dto.WindowAccumulateExtraData;
import com.whu.graduation.taskincentive.event.UserEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskStrategiesTest {

    @Test
    public void accumulate_shouldGrant_whenReachTarget() {
        AccumulateTaskStrategy strategy = new AccumulateTaskStrategy();
        TaskConfig cfg = taskConfig("{\"targetValue\":10}");
        UserTaskInstance instance = instance(7, null);
        UserEvent event = event(3, LocalDateTime.now());

        List<Integer> stages = strategy.execute(event, cfg, instance);

        assertEquals(List.of(1), stages);
        assertEquals(10, instance.getProgress());
        assertEquals(1, instance.getStatus());
    }

    @Test
    public void continuous_shouldResetToOne_whenNotConsecutiveDay() {
        ContinuousTaskStrategy strategy = new ContinuousTaskStrategy();
        TaskConfig cfg = taskConfig("{\"targetDays\":3}");
        ContinuousExtraData extra = new ContinuousExtraData();
        extra.setContinuousDays(5);
        extra.setLastSignDate(LocalDate.now().minusDays(2));
        UserTaskInstance instance = instance(0, JSON.toJSONString(extra));
        UserEvent event = event(1, LocalDateTime.now());

        List<Integer> stages = strategy.execute(event, cfg, instance);

        assertTrue(stages.isEmpty());
        ContinuousExtraData parsed = JSON.parseObject(instance.getExtraData(), ContinuousExtraData.class);
        assertEquals(1, parsed.getContinuousDays());
    }

    @Test
    public void stair_shouldOnlyGrantNewStage() {
        StairTaskStrategy strategy = new StairTaskStrategy();
        TaskConfig cfg = taskConfig("{\"stages\":[10,30,60],\"rewards\":[1,1,1]}");
        UserTaskInstance instance = instance(28, "{\"grantedStages\":[1]}");
        UserEvent event = event(5, LocalDateTime.now());

        List<Integer> stages = strategy.execute(event, cfg, instance);

        assertEquals(List.of(2), stages);
        assertEquals(33, instance.getProgress());
        assertTrue(instance.getExtraData().contains("2"));
    }

    @Test
    public void windowAccumulate_shouldDropExpiredEntries_andGrant() {
        WindowAccumulateTaskStrategy strategy = new WindowAccumulateTaskStrategy();
        TaskConfig cfg = taskConfig("{\"targetValue\":10,\"windowMinutes\":30}");

        LocalDateTime now = LocalDateTime.now();
        String oldAndNew = "{\"entries\":[{\"time\":\"" + now.minusMinutes(40) + "\",\"value\":5},{\"time\":\"" + now.minusMinutes(10) + "\",\"value\":4}]}";
        UserTaskInstance instance = instance(0, oldAndNew);

        List<Integer> stages = strategy.execute(event(6, now), cfg, instance);

        assertEquals(List.of(1), stages);
        assertEquals(10, instance.getProgress());
        assertEquals(1, instance.getStatus());
    }

    @Test
    public void windowAccumulate_shouldReturnEmpty_whenRuleInvalidOrWindowInvalid() {
        WindowAccumulateTaskStrategy strategy = new WindowAccumulateTaskStrategy();
        UserTaskInstance instance = instance(0, null);

        List<Integer> invalidRuleStages = strategy.execute(event(2, LocalDateTime.now()), taskConfig("{"), instance);
        List<Integer> invalidWindowStages = strategy.execute(event(2, LocalDateTime.now()), taskConfig("{\"targetValue\":10,\"windowMinutes\":0}"), instance);

        assertTrue(invalidRuleStages.isEmpty());
        assertTrue(invalidWindowStages.isEmpty());
    }

    @Test
    public void windowAccumulate_shouldHandleMalformedExtraDataAndNullEventValue() {
        WindowAccumulateTaskStrategy strategy = new WindowAccumulateTaskStrategy();
        TaskConfig cfg = taskConfig("{\"targetValue\":2,\"windowMinutes\":30}");
        UserTaskInstance instance = instance(0, "not-json");

        List<Integer> stages = strategy.execute(event(null, LocalDateTime.now()), cfg, instance);

        assertTrue(stages.isEmpty());
        assertEquals(0, instance.getProgress());
        WindowAccumulateExtraData extraData = JSON.parseObject(instance.getExtraData(), WindowAccumulateExtraData.class);
        assertEquals(1, extraData.getEntries().size());
    }

    @Test
    public void windowAccumulate_shouldIgnoreNullEntriesAndNullEntryValueInWindow() {
        WindowAccumulateTaskStrategy strategy = new WindowAccumulateTaskStrategy();
        LocalDateTime now = LocalDateTime.now();
        TaskConfig cfg = taskConfig("{\"targetValue\":5,\"windowMinutes\":30}");
        String raw = "{\"entries\":[null,{\"time\":null,\"value\":7},{\"time\":\"" + now.minusMinutes(5) + "\",\"value\":null}]}";
        UserTaskInstance instance = instance(0, raw);

        List<Integer> stages = strategy.execute(event(5, now), cfg, instance);

        assertEquals(List.of(1), stages);
        assertEquals(5, instance.getProgress());
        WindowAccumulateExtraData extraData = JSON.parseObject(instance.getExtraData(), WindowAccumulateExtraData.class);
        assertEquals(2, extraData.getEntries().size());
    }

    @Test
    public void accumulate_shouldReturnEmpty_whenRuleMalformed() {
        AccumulateTaskStrategy strategy = new AccumulateTaskStrategy();
        TaskConfig cfg = taskConfig("{");
        UserTaskInstance instance = instance(2, null);

        List<Integer> stages = strategy.execute(event(3, LocalDateTime.now()), cfg, instance);

        assertTrue(stages.isEmpty());
        assertEquals(5, instance.getProgress());
        assertEquals(0, instance.getStatus());
    }

    @Test
    public void continuous_shouldGrant_whenConsecutiveAndReachTarget() {
        ContinuousTaskStrategy strategy = new ContinuousTaskStrategy();
        TaskConfig cfg = taskConfig("{\"targetDays\":3}");
        ContinuousExtraData extra = new ContinuousExtraData();
        extra.setContinuousDays(2);
        extra.setLastSignDate(LocalDate.now().minusDays(1));
        UserTaskInstance instance = instance(0, JSON.toJSONString(extra));

        List<Integer> stages = strategy.execute(event(1, LocalDateTime.now()), cfg, instance);

        assertEquals(List.of(1), stages);
        assertEquals(1, instance.getStatus());
        ContinuousExtraData parsed = JSON.parseObject(instance.getExtraData(), ContinuousExtraData.class);
        assertEquals(3, parsed.getContinuousDays());
    }

    @Test
    public void stair_shouldReturnEmpty_whenRuleMalformed() {
        StairTaskStrategy strategy = new StairTaskStrategy();
        TaskConfig cfg = taskConfig("{");
        UserTaskInstance instance = instance(0, null);

        List<Integer> stages = strategy.execute(event(5, LocalDateTime.now()), cfg, instance);

        assertTrue(stages.isEmpty());
        assertEquals(0, instance.getProgress());
    }

    private TaskConfig taskConfig(String ruleConfig) {
        TaskConfig cfg = new TaskConfig();
        cfg.setId(10L);
        cfg.setRuleConfig(ruleConfig);
        return cfg;
    }

    private UserTaskInstance instance(Integer progress, String extraData) {
        UserTaskInstance instance = new UserTaskInstance();
        instance.setProgress(progress);
        instance.setStatus(0);
        instance.setExtraData(extraData);
        return instance;
    }

    private UserEvent event(Integer value, LocalDateTime time) {
        UserEvent e = new UserEvent();
        e.setValue(value);
        e.setTime(time);
        return e;
    }
}

