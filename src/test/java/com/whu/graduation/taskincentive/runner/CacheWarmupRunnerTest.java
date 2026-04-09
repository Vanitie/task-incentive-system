package com.whu.graduation.taskincentive.runner;

import com.whu.graduation.taskincentive.config.AppProperties;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import com.whu.graduation.taskincentive.service.risk.RiskCacheLoader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheWarmupRunnerTest {

    @Test
    void run_shouldSkipWhenDisabledInLegacyMode() {
        AppProperties props = new AppProperties();
        AppProperties.CacheWarmup cfg = new AppProperties.CacheWarmup();
        cfg.setEnabled(false);
        props.setCacheWarmup(cfg);

        RiskCacheLoader riskCacheLoader = mock(RiskCacheLoader.class);
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        UserTaskInstanceService userTaskInstanceService = mock(UserTaskInstanceService.class);

        CacheWarmupRunner runner = new CacheWarmupRunner(props, riskCacheLoader, taskConfigService, userTaskInstanceService);
        runner.run(null);

        verify(riskCacheLoader, never()).load();
        verify(taskConfigService, never()).warmupAllTaskConfigs(anyInt(), anyLong(), eq(true));
        verify(userTaskInstanceService, never()).warmupHotUserTaskInstances(anyInt(), anyInt(), anyInt(), anyLong(), anyInt(), anyLong());
    }

    @Test
    void run_shouldExecuteAllWarmupPhasesInLegacyMode() {
        AppProperties props = new AppProperties();
        AppProperties.CacheWarmup cfg = new AppProperties.CacheWarmup();
        cfg.setEnabled(true);
        cfg.setFailFast(false);
        cfg.setLoadRisk(true);
        cfg.setLoadTaskConfig(true);
        cfg.setLoadHotUsers(true);
        cfg.setTaskConfigBatchSize(99);
        cfg.setTaskConfigRedisTtlSeconds(88L);
        cfg.setHotUserLimit(77);
        cfg.setInstancesPerHotUser(66);
        cfg.setMaxTotalHotUserInstances(55);
        cfg.setHotUserBatchSize(33);
        cfg.setUserTaskRedisTtlMinutes(44L);
        cfg.setMaxDurationSeconds(10L);
        props.setCacheWarmup(cfg);

        RiskCacheLoader riskCacheLoader = mock(RiskCacheLoader.class);
        when(riskCacheLoader.load()).thenReturn(new RiskCacheLoader.LoadStats(1, 2, 3, 4));

        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        when(taskConfigService.warmupAllTaskConfigs(99, 88L, true)).thenReturn(10);

        UserTaskInstanceService userTaskInstanceService = mock(UserTaskInstanceService.class);
        when(userTaskInstanceService.warmupHotUserTaskInstances(eq(77), eq(66), eq(55), eq(44L), eq(33), anyLong()))
                .thenReturn(new UserTaskInstanceService.HotUserWarmupStats(5, 6, false));

        CacheWarmupRunner runner = new CacheWarmupRunner(props, riskCacheLoader, taskConfigService, userTaskInstanceService);
        runner.run(null);

        verify(riskCacheLoader).load();
        verify(taskConfigService).warmupAllTaskConfigs(99, 88L, true);
        verify(userTaskInstanceService).warmupHotUserTaskInstances(eq(77), eq(66), eq(55), eq(44L), eq(33), anyLong());
    }

    @Test
    void run_shouldExecuteMemoryOnlyMode() {
        AppProperties props = new AppProperties();
        AppProperties.CacheWarmup cfg = new AppProperties.CacheWarmup();
        cfg.setMode("memory_only");
        cfg.setTaskConfigBatchSize(99);
        cfg.setTaskConfigRedisTtlSeconds(88L);
        props.setCacheWarmup(cfg);

        RiskCacheLoader riskCacheLoader = mock(RiskCacheLoader.class);
        when(riskCacheLoader.load()).thenReturn(new RiskCacheLoader.LoadStats(1, 2, 3, 4));

        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        when(taskConfigService.warmupAllTaskConfigs(99, 88L, false)).thenReturn(10);

        UserTaskInstanceService userTaskInstanceService = mock(UserTaskInstanceService.class);

        CacheWarmupRunner runner = new CacheWarmupRunner(props, riskCacheLoader, taskConfigService, userTaskInstanceService);
        runner.run(new DefaultApplicationArguments(new String[]{}));

        verify(riskCacheLoader).load();
        verify(taskConfigService).warmupAllTaskConfigs(99, 88L, false);
        verify(userTaskInstanceService, never()).warmupHotUserTaskInstances(anyInt(), anyInt(), anyInt(), anyLong(), anyInt(), anyLong());
    }

    @Test
    void run_shouldUseCommandLineModeOverConfig() {
        AppProperties props = new AppProperties();
        AppProperties.CacheWarmup cfg = new AppProperties.CacheWarmup();
        cfg.setMode("full");
        props.setCacheWarmup(cfg);

        RiskCacheLoader riskCacheLoader = mock(RiskCacheLoader.class);
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        UserTaskInstanceService userTaskInstanceService = mock(UserTaskInstanceService.class);

        CacheWarmupRunner runner = new CacheWarmupRunner(props, riskCacheLoader, taskConfigService, userTaskInstanceService);
        runner.run(new DefaultApplicationArguments(new String[]{"--cache-warmup-mode=off"}));

        verify(riskCacheLoader, never()).load();
        verify(taskConfigService, never()).warmupAllTaskConfigs(anyInt(), anyLong(), eq(true));
        verify(userTaskInstanceService, never()).warmupHotUserTaskInstances(anyInt(), anyInt(), anyInt(), anyLong(), anyInt(), anyLong());
    }

    @Test
    void run_shouldThrowWhenFailFastAndPhaseFails() {
        AppProperties props = new AppProperties();
        AppProperties.CacheWarmup cfg = new AppProperties.CacheWarmup();
        cfg.setMode("full");
        cfg.setFailFast(true);
        props.setCacheWarmup(cfg);

        RiskCacheLoader riskCacheLoader = mock(RiskCacheLoader.class);
        when(riskCacheLoader.load()).thenThrow(new RuntimeException("db down"));

        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        UserTaskInstanceService userTaskInstanceService = mock(UserTaskInstanceService.class);

        CacheWarmupRunner runner = new CacheWarmupRunner(props, riskCacheLoader, taskConfigService, userTaskInstanceService);
        assertThrows(IllegalStateException.class, () -> runner.run(null));
    }
}
