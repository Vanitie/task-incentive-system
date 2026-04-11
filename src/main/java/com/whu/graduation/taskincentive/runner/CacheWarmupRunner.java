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

import java.util.List;
import java.util.Locale;

/**
 * Startup cache warmup orchestrator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmupRunner implements ApplicationRunner {

    private static final String ARG_MODE = "app.cache-warmup.mode";
    private static final String ARG_MODE_ALIAS = "cache-warmup-mode";
    private static final String ARG_MODE_SHORT = "warmup-mode";

    private final AppProperties appProperties;
    private final RiskCacheLoader riskCacheLoader;
    private final TaskConfigService taskConfigService;
    private final UserTaskInstanceService userTaskInstanceService;

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.CacheWarmup cfg = appProperties.getCacheWarmup();
        if (cfg == null) {
            log.info("cache warmup disabled: config missing");
            return;
        }

        String modeValue = resolveModeValue(args, cfg);
        if (modeValue != null) {
            AppProperties.CacheWarmup.Mode mode = parseMode(modeValue);
            runByMode(cfg, mode);
            return;
        }

        runLegacy(cfg);
    }

    private void runLegacy(AppProperties.CacheWarmup cfg) {
        if (!cfg.isEnabled()) {
            log.info("cache warmup disabled (legacy enabled=false)");
            return;
        }

        long startMs = System.currentTimeMillis();
        long maxDurationMs = cfg.getMaxDurationSeconds() > 0 ? cfg.getMaxDurationSeconds() * 1000L : Long.MAX_VALUE;
        long deadlineMs = maxDurationMs == Long.MAX_VALUE ? Long.MAX_VALUE : startMs + maxDurationMs;
        log.info("cache warmup start (legacy mode), failFast={}, maxDurationSeconds={}", cfg.isFailFast(), cfg.getMaxDurationSeconds());

        if (cfg.isLoadRisk() && hasRemainingBudget(deadlineMs)) {
            warmupRisk(cfg.isFailFast());
        }

        if (cfg.isLoadTaskConfig() && hasRemainingBudget(deadlineMs)) {
            warmupTaskConfig(cfg, cfg.isFailFast(), true);
        }

        if (cfg.isLoadHotUsers() && hasRemainingBudget(deadlineMs)) {
            warmupHotUsers(cfg, cfg.isFailFast(), cfg.getHotUserLimit(), cfg.getInstancesPerHotUser(),
                    cfg.getMaxTotalHotUserInstances(), cfg.getHotUserBatchSize(), deadlineMs, "legacy");
        }

        log.info("cache warmup finished (legacy mode), elapsedMs={}", System.currentTimeMillis() - startMs);
    }

    private void runByMode(AppProperties.CacheWarmup cfg, AppProperties.CacheWarmup.Mode mode) {
        if (mode == AppProperties.CacheWarmup.Mode.OFF) {
            log.info("cache warmup disabled by mode={}", mode);
            return;
        }

        long startMs = System.currentTimeMillis();
        long modeDurationSeconds = resolveModeDurationSeconds(cfg, mode);
        long maxDurationMs = modeDurationSeconds > 0 ? modeDurationSeconds * 1000L : Long.MAX_VALUE;
        long deadlineMs = maxDurationMs == Long.MAX_VALUE ? Long.MAX_VALUE : startMs + maxDurationMs;
        log.info("cache warmup start, mode={}, failFast={}, maxDurationSeconds={}", mode, cfg.isFailFast(), modeDurationSeconds);

        if (hasRemainingBudget(deadlineMs)) {
            warmupRisk(cfg.isFailFast());
        }

        if (hasRemainingBudget(deadlineMs)) {
            boolean writeRedis = mode != AppProperties.CacheWarmup.Mode.MEMORY_ONLY;
            warmupTaskConfig(cfg, cfg.isFailFast(), writeRedis);
        }

        if (mode == AppProperties.CacheWarmup.Mode.MEMORY_AND_REDIS_LIMITED && hasRemainingBudget(deadlineMs)) {
            warmupHotUsers(cfg, cfg.isFailFast(), cfg.getHotUserLimit(), cfg.getInstancesPerHotUser(),
                    cfg.getMaxTotalHotUserInstances(), cfg.getHotUserBatchSize(), deadlineMs, "limited");
        }

        if (mode == AppProperties.CacheWarmup.Mode.FULL && hasRemainingBudget(deadlineMs)) {
            warmupHotUsers(cfg, cfg.isFailFast(),
                    fallbackPositive(cfg.getFullHotUserLimit(), cfg.getHotUserLimit()),
                    fallbackPositive(cfg.getFullInstancesPerHotUser(), cfg.getInstancesPerHotUser()),
                    fallbackPositive(cfg.getFullMaxTotalHotUserInstances(), cfg.getMaxTotalHotUserInstances()),
                    fallbackPositive(cfg.getFullHotUserBatchSize(), cfg.getHotUserBatchSize()),
                    deadlineMs,
                    "full");
        }

        log.info("cache warmup finished, mode={}, elapsedMs={}", mode, System.currentTimeMillis() - startMs);
    }

    private long resolveModeDurationSeconds(AppProperties.CacheWarmup cfg, AppProperties.CacheWarmup.Mode mode) {
        if (mode == AppProperties.CacheWarmup.Mode.FULL) {
            return cfg.getFullMaxDurationSeconds() > 0 ? cfg.getFullMaxDurationSeconds() : cfg.getMaxDurationSeconds();
        }
        return cfg.getMaxDurationSeconds();
    }

    private void warmupRisk(boolean failFast) {
        executeOrHandle("risk", failFast, () -> {
            RiskCacheLoader.LoadStats stats = riskCacheLoader.load();
            log.info("risk cache warmup done, rules={}, blacklists={}, whitelists={}, quotas={}",
                    stats.getActiveRuleCount(),
                    stats.getBlacklistCount(),
                    stats.getWhitelistCount(),
                    stats.getQuotaCount());
        });
    }

    private void warmupTaskConfig(AppProperties.CacheWarmup cfg, boolean failFast, boolean writeRedis) {
        executeOrHandle(writeRedis ? "task-config-memory+redis" : "task-config-memory-only", failFast, () -> {
            int warmed = taskConfigService.warmupAllTaskConfigs(
                    cfg.getTaskConfigBatchSize(),
                    cfg.getTaskConfigRedisTtlSeconds(),
                    writeRedis);
            log.info("task config warmup done, warmedCount={}, batchSize={}, redisWriteEnabled={}, redisTtlSec={}",
                    warmed,
                    cfg.getTaskConfigBatchSize(),
                    writeRedis,
                    cfg.getTaskConfigRedisTtlSeconds());
        });
    }

    private void warmupHotUsers(AppProperties.CacheWarmup cfg,
                                boolean failFast,
                                int hotUserLimit,
                                int instancesPerHotUser,
                                int maxTotalInstances,
                                int batchSize,
                                long deadlineMs,
                                String scope) {
        executeOrHandle("hot-users-" + scope, failFast, () -> {
            long remainingMs = remainingBudgetMs(deadlineMs);
            UserTaskInstanceService.HotUserWarmupStats stats = userTaskInstanceService.warmupHotUserTaskInstances(
                    hotUserLimit,
                    instancesPerHotUser,
                    maxTotalInstances,
                    cfg.getUserTaskRedisTtlMinutes(),
                    batchSize,
                    remainingMs);
            log.info("hot user cache warmup done, scope={}, users={}, instances={}, truncated={}, userLimit={}, perUserLimit={}, totalLimit={}, batchUserSize={}, phaseBudgetMs={}",
                    scope,
                    stats.getUserCount(),
                    stats.getInstanceCount(),
                    stats.isTruncated(),
                    hotUserLimit,
                    instancesPerHotUser,
                    maxTotalInstances,
                    batchSize,
                    remainingMs);
        });
    }

    private String resolveModeValue(ApplicationArguments args, AppProperties.CacheWarmup cfg) {
        String argMode = firstOptionValue(args, ARG_MODE, ARG_MODE_ALIAS, ARG_MODE_SHORT);
        if (argMode != null && !argMode.trim().isEmpty()) {
            return argMode.trim();
        }
        if (!cfg.isEnabled()) {
            return null;
        }
        String cfgMode = cfg.getMode();
        if (cfgMode != null && !cfgMode.trim().isEmpty()) {
            return cfgMode.trim();
        }
        return null;
    }

    private String firstOptionValue(ApplicationArguments args, String... names) {
        if (args == null || names == null) {
            return null;
        }
        for (String name : names) {
            List<String> values = args.getOptionValues(name);
            if (values == null || values.isEmpty()) {
                continue;
            }
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private AppProperties.CacheWarmup.Mode parseMode(String modeValue) {
        String normalized = modeValue.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace('.', '_');
        try {
            return AppProperties.CacheWarmup.Mode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("unsupported cache warmup mode=" + modeValue
                    + ", expected one of OFF/MEMORY_ONLY/MEMORY_AND_REDIS_LIMITED/FULL", ex);
        }
    }

    private int fallbackPositive(int preferred, int fallback) {
        if (preferred > 0) {
            return preferred;
        }
        return Math.max(1, fallback);
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

    private boolean hasRemainingBudget(long deadlineMs) {
        return remainingBudgetMs(deadlineMs) > 0;
    }

    private long remainingBudgetMs(long deadlineMs) {
        if (deadlineMs == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, deadlineMs - System.currentTimeMillis());
    }
}

