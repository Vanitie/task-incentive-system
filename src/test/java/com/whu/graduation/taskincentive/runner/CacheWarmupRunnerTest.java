package com.whu.graduation.taskincentive.runner;

import com.whu.graduation.taskincentive.config.AppProperties;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import com.whu.graduation.taskincentive.service.risk.RiskCacheLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheWarmupRunnerTest {

    @Test
    void run_shouldSkipWhenDisabled() {
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
        verify(taskConfigService, never()).warmupAllTaskConfigs(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong());
        verify(userTaskInstanceService, never()).warmupHotUserTaskInstances(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void run_shouldExecuteAllWarmupPhases() {
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
        cfg.setUserTaskRedisTtlMinutes(44L);
        props.setCacheWarmup(cfg);

        RiskCacheLoader riskCacheLoader = mock(RiskCacheLoader.class);
        when(riskCacheLoader.load()).thenReturn(new RiskCacheLoader.LoadStats(1, 2, 3, 4));

        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        when(taskConfigService.warmupAllTaskConfigs(99, 88L)).thenReturn(10);

        UserTaskInstanceService userTaskInstanceService = mock(UserTaskInstanceService.class);
        when(userTaskInstanceService.warmupHotUserTaskInstances(77, 66, 55, 44L))
                .thenReturn(new UserTaskInstanceService.HotUserWarmupStats(5, 6, false));

        CacheWarmupRunner runner = new CacheWarmupRunner(props, riskCacheLoader, taskConfigService, userTaskInstanceService);
        runner.run(null);

        verify(riskCacheLoader).load();
        verify(taskConfigService).warmupAllTaskConfigs(99, 88L);
        verify(userTaskInstanceService).warmupHotUserTaskInstances(77, 66, 55, 44L);
    }

    @Test
    void run_shouldThrowWhenFailFastAndPhaseFails() {
        AppProperties props = new AppProperties();
        AppProperties.CacheWarmup cfg = new AppProperties.CacheWarmup();
        cfg.setEnabled(true);
        cfg.setFailFast(true);
        cfg.setLoadRisk(true);
        cfg.setLoadTaskConfig(false);
        cfg.setLoadHotUsers(false);
        props.setCacheWarmup(cfg);

        RiskCacheLoader riskCacheLoader = mock(RiskCacheLoader.class);
        when(riskCacheLoader.load()).thenThrow(new RuntimeException("db down"));

        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        UserTaskInstanceService userTaskInstanceService = mock(UserTaskInstanceService.class);

        CacheWarmupRunner runner = new CacheWarmupRunner(props, riskCacheLoader, taskConfigService, userTaskInstanceService);
        assertThrows(IllegalStateException.class, () -> runner.run(null));
    }
}

