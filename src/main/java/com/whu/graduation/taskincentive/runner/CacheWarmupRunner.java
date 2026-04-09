package com.whu.graduation.taskincentive.runner;

import com.whu.graduation.taskincentive.config.AppProperties;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import com.whu.graduation.taskincentive.service.risk.RiskCacheLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup cache warmup orchestrator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmupRunner implements ApplicationRunner {

    private final AppProperties appProperties;
    private final RiskCacheLoader riskCacheLoader;
    private final TaskConfigService taskConfigService;
    private final UserTaskInstanceService userTaskInstanceService;

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.CacheWarmup cfg = appProperties.getCacheWarmup();
        if (cfg == null || !cfg.isEnabled()) {
            log.info("cache warmup disabled");
            return;
        }

        long startMs = System.currentTimeMillis();
        log.info("cache warmup start, failFast={}", cfg.isFailFast());

        if (cfg.isLoadRisk()) {
            executeOrHandle("risk", cfg.isFailFast(), () -> {
                RiskCacheLoader.LoadStats stats = riskCacheLoader.load();
                log.info("risk cache warmup done, rules={}, blacklists={}, whitelists={}, quotas={}",
                        stats.getActiveRuleCount(),
                        stats.getBlacklistCount(),
                        stats.getWhitelistCount(),
                        stats.getQuotaCount());
            });
        }

        if (cfg.isLoadTaskConfig()) {
            executeOrHandle("task-config", cfg.isFailFast(), () -> {
                int warmed = taskConfigService.warmupAllTaskConfigs(
                        cfg.getTaskConfigBatchSize(),
                        cfg.getTaskConfigRedisTtlSeconds());
                log.info("task config warmup done, warmedCount={}, batchSize={}, redisTtlSec={}",
                        warmed,
                        cfg.getTaskConfigBatchSize(),
                        cfg.getTaskConfigRedisTtlSeconds());
            });
        }

        if (cfg.isLoadHotUsers()) {
            executeOrHandle("hot-users", cfg.isFailFast(), () -> {
                UserTaskInstanceService.HotUserWarmupStats stats = userTaskInstanceService.warmupHotUserTaskInstances(
                        cfg.getHotUserLimit(),
                        cfg.getInstancesPerHotUser(),
                        cfg.getMaxTotalHotUserInstances(),
                        cfg.getUserTaskRedisTtlMinutes());
                log.info("hot user cache warmup done, users={}, instances={}, truncated={}, userLimit={}, perUserLimit={}, totalLimit={}",
                        stats.getUserCount(),
                        stats.getInstanceCount(),
                        stats.isTruncated(),
                        cfg.getHotUserLimit(),
                        cfg.getInstancesPerHotUser(),
                        cfg.getMaxTotalHotUserInstances());
            });
        }

        log.info("cache warmup finished, elapsedMs={}", System.currentTimeMillis() - startMs);
    }

    private void executeOrHandle(String phase, boolean failFast, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            if (failFast) {
                throw new IllegalStateException("cache warmup failed at phase=" + phase, e);
            }
            log.warn("cache warmup phase failed, phase={}, err={}", phase, e.getMessage(), e);
        }
    }
}

