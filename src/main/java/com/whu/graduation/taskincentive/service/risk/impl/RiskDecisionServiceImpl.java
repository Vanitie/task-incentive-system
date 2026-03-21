package com.whu.graduation.taskincentive.service.risk.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.graduation.taskincentive.common.enums.RiskDecisionAction;
import com.whu.graduation.taskincentive.constant.RiskConstants;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dao.entity.RewardFreezeRecord;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionRequest;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;
import com.whu.graduation.taskincentive.dto.risk.RiskHitRule;
import com.whu.graduation.taskincentive.service.risk.RiskCacheStore;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionEngine;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionService;
import com.whu.graduation.taskincentive.service.risk.RiskMetricStore;
import com.whu.graduation.taskincentive.mq.RiskDecisionPersistMessage;
import com.whu.graduation.taskincentive.mq.RiskDecisionPersistProducer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 风控决策服务实现
 */
@Service
public class RiskDecisionServiceImpl implements RiskDecisionService {

    private final RiskDecisionEngine decisionEngine;
    private final RiskMetricStore metricStore;
    private final RiskCacheStore cacheStore;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RiskDecisionPersistProducer persistProducer;

    public RiskDecisionServiceImpl(RiskDecisionEngine decisionEngine,
                                   RiskMetricStore metricStore,
                                   RiskCacheStore cacheStore,
                                   RedisTemplate<String, String> redisTemplate,
                                   RiskDecisionPersistProducer persistProducer) {
        this.decisionEngine = decisionEngine;
        this.metricStore = metricStore;
        this.cacheStore = cacheStore;
        this.redisTemplate = redisTemplate;
        this.persistProducer = persistProducer;
    }

    @Override
    public RiskDecisionResponse evaluate(RiskDecisionRequest request) {
        long start = System.currentTimeMillis();
        String traceId = request.getRequestId() == null ? String.valueOf(IdWorker.getId()) : request.getRequestId();

        try {
            // 1. 幂等校验（防重放）
            if (!checkDedup(request)) {
                return buildAndLog(request, traceId, RiskDecisionAction.REJECT, RiskConstants.REASON_REPLAY,
                        Collections.emptyList(), start, 0, null);
            }

            // 2. 记录指标（用于频控/聚合判断）
            metricStore.record(request);

            // 3. 名单优先判断
            if (hitWhitelist(request)) {
                return buildAndLog(request, traceId, RiskDecisionAction.PASS, RiskConstants.REASON_WHITELIST,
                        Collections.emptyList(), start, 0, null);
            }
            if (hitBlacklist(request)) {
                return buildAndLog(request, traceId, RiskDecisionAction.REJECT, RiskConstants.REASON_BLACKLIST,
                        Collections.emptyList(), start, 80, null);
            }

            // 4. 配额判断（用户/任务/全局）
            if (!checkQuota(request)) {
                return buildAndLog(request, traceId, RiskDecisionAction.REJECT, RiskConstants.REASON_QUOTA_EXCEEDED,
                        Collections.emptyList(), start, 70, null);
            }

            // 5. 规则引擎评估
            Map<String, Object> context = buildContext(request);
            RiskDecisionResponse response = decisionEngine.evaluateRules(cacheStore.getActiveRules(), context);
            if (response == null) {
                return buildAndLog(request, traceId, RiskDecisionAction.FREEZE, RiskConstants.REASON_DEFAULT,
                        Collections.emptyList(), start, 60, null);
            }
            response.setTraceId(traceId);

            // 6. 兜底：决策失败默认冻结
            String decision = response.getDecision();
            RiskDecisionAction action = parseAction(decision);
            if (action == null) {
                return buildAndLog(request, traceId, RiskDecisionAction.FREEZE, RiskConstants.REASON_DEFAULT,
                        response.getHitRules(), start, response.getRiskScore(), response.getDegradeRatio());
            }
            return buildAndLog(request, traceId, action, response.getReasonCode(),
                    response.getHitRules(), start, response.getRiskScore(), response.getDegradeRatio());
        } catch (Exception e) {
            return buildAndLog(request, traceId, RiskDecisionAction.FREEZE, RiskConstants.REASON_DEFAULT,
                    Collections.emptyList(), start, 90, null);
        }
    }

    private RiskDecisionResponse buildAndLog(RiskDecisionRequest request, String traceId,
                                             RiskDecisionAction action, String reason,
                                             List<RiskHitRule> hitRules, long start,
                                             Integer riskScore, Double degradeRatio) {
        long latency = System.currentTimeMillis() - start;
        RiskDecisionResponse response = RiskDecisionResponse.builder()
                .decision(action.name())
                .reasonCode(reason)
                .hitRules(hitRules)
                .riskScore(riskScore == null ? 0 : riskScore)
                .traceId(traceId)
                .degradeRatio(degradeRatio)
                .build();

        RiskDecisionLog log = RiskDecisionLog.builder()
                .id(IdWorker.getId())
                .requestId(request.getRequestId())
                .eventId(request.getEventId())
                .userId(request.getUserId())
                .taskId(request.getTaskId())
                .decision(action.name())
                .reasonCode(reason)
                .hitRules(toJson(hitRules))
                .riskScore(riskScore == null ? 0 : riskScore)
                .latencyMs(latency)
                .createdAt(new Date())
                .build();
        RewardFreezeRecord freeze = null;
        if (action == RiskDecisionAction.FREEZE) {
            freeze = RewardFreezeRecord.builder()
                    .id(IdWorker.getId())
                    .rewardId(null)
                    .userId(request.getUserId())
                    .taskId(request.getTaskId())
                    .freezeReason(reason)
                    .status(0)
                    .unfreezeAt(null)
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .build();
        }
        try {
            RiskDecisionPersistMessage msg = RiskDecisionPersistMessage.builder()
                    .decisionLog(log)
                    .freezeRecord(freeze)
                    .build();
            String userKey = request.getUserId() == null ? "0" : String.valueOf(request.getUserId());
            persistProducer.send(userKey, msg);
        } catch (Exception ignored) {
        }
        return response;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private boolean checkDedup(RiskDecisionRequest request) {
        if (request.getRequestId() == null || request.getRequestId().isEmpty()) {
            return true;
        }
        String key = RiskConstants.CACHE_DECISION_DEDUP_PREFIX + request.getRequestId();
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", 7, TimeUnit.DAYS);
        return ok == null || ok;
    }

    private boolean hitBlacklist(RiskDecisionRequest request) {
        return hitList(cacheStore.getBlacklists(), request);
    }

    private boolean hitWhitelist(RiskDecisionRequest request) {
        return hitList(cacheStore.getWhitelists(), request);
    }

    private boolean hitList(Map<String, java.util.Set<String>> lists, RiskDecisionRequest request) {
        if (lists == null || lists.isEmpty()) return false;
        String userKey = RiskCacheStore.listKey("USER");
        String deviceKey = RiskCacheStore.listKey("DEVICE");
        String ipKey = RiskCacheStore.listKey("IP");

        if (request.getUserId() != null) {
            java.util.Set<String> users = lists.get(userKey);
            if (users != null && users.contains(String.valueOf(request.getUserId()))) return true;
        }
        if (request.getDeviceId() != null) {
            java.util.Set<String> devices = lists.get(deviceKey);
            if (devices != null && devices.contains(request.getDeviceId())) return true;
        }
        if (request.getIp() != null) {
            java.util.Set<String> ips = lists.get(ipKey);
            if (ips != null && ips.contains(request.getIp())) return true;
        }
        return false;
    }

    private boolean checkQuota(RiskDecisionRequest request) {
        if (cacheStore.getQuotas() == null || cacheStore.getQuotas().isEmpty()) return true;
        LocalDateTime time = request.getEventTime() == null ? LocalDateTime.now() : request.getEventTime();
        String resourceType = normalizeResourceType(request.getResourceType());
        String resourceId = normalizeResourceId(request.getResourceId());

        // 用户级
        if (request.getUserId() != null) {
            if (!consumeQuota("USER", String.valueOf(request.getUserId()), resourceType, resourceId, "DAY", time)) return false;
        }
        // 任务级
        if (request.getTaskId() != null) {
            if (!consumeQuota("TASK", String.valueOf(request.getTaskId()), resourceType, resourceId, "DAY", time)) return false;
        }
        // 全局级
        if (!consumeQuota("GLOBAL", "ALL", resourceType, resourceId, "DAY", time)) return false;
        return true;
    }

    private boolean consumeQuota(String scopeType, String scopeId, String resourceType, String resourceId, String periodType, LocalDateTime time) {
        String key = RiskCacheStore.quotaKey(scopeType, scopeId, resourceType, resourceId, periodType);
        RiskQuota quota = cacheStore.getQuotas().get(key);
        if (quota == null || quota.getLimitValue() == null) return true;

        String bucket = bucketKey(periodType, time);
        String redisKey = "risk:quota:" + key + ":" + bucket;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        long ttl = ttlSeconds(periodType, time);
        if (ttl > 0) redisTemplate.expire(redisKey, ttl, TimeUnit.SECONDS);
        return count == null || count <= quota.getLimitValue();
    }

    private String bucketKey(String periodType, LocalDateTime time) {
        if ("HOUR".equalsIgnoreCase(periodType)) {
            return time.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH"));
        }
        if ("MINUTE".equalsIgnoreCase(periodType)) {
            return time.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        }
        return time.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private long ttlSeconds(String periodType, LocalDateTime time) {
        if ("HOUR".equalsIgnoreCase(periodType)) {
            LocalDateTime next = time.plusHours(1).withMinute(0).withSecond(0).withNano(0);
            return toEpochSecond(next) - toEpochSecond(time);
        }
        if ("MINUTE".equalsIgnoreCase(periodType)) {
            LocalDateTime next = time.plusMinutes(1).withSecond(0).withNano(0);
            return toEpochSecond(next) - toEpochSecond(time);
        }
        LocalDateTime next = time.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        return toEpochSecond(next) - toEpochSecond(time);
    }

    private long toEpochSecond(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private Map<String, Object> buildContext(RiskDecisionRequest request) {
        Map<String, Object> ctx = new HashMap<>();
        LocalDateTime time = request.getEventTime() == null ? LocalDateTime.now() : request.getEventTime();

        ctx.put("eventTime", time);
        ctx.put("userId", request.getUserId());
        ctx.put("taskId", request.getTaskId());
        ctx.put("eventType", request.getEventType());
        ctx.put("amount", request.getAmount() == null ? 1 : request.getAmount());
        ctx.put("resourceType", normalizeResourceType(request.getResourceType()));
        ctx.put("resourceId", normalizeResourceId(request.getResourceId()));
        ctx.put("count_1m", metricStore.getCount1m(request.getUserId(), request.getTaskId(), time));
        ctx.put("count_1h", metricStore.getCount1h(request.getUserId(), request.getTaskId(), time));
        ctx.put("amount_1d", metricStore.getAmount1d(request.getUserId(), request.getTaskId(), time));
        ctx.put("distinct_device_1d", metricStore.getDistinctDevice1d(request.getUserId(), time));
        ctx.put("ip_count_1m", metricStore.getIpCount1m(request.getIp(), time));
        ctx.put("device_count_1m", metricStore.getDeviceCount1m(request.getDeviceId(), time));

        if (request.getExt() != null) {
            ctx.putAll(request.getExt());
        }
        return ctx;
    }

    private String normalizeResourceType(String resourceType) {
        return (resourceType == null || resourceType.isEmpty()) ? "ALL" : resourceType;
    }

    private String normalizeResourceId(String resourceId) {
        return (resourceId == null || resourceId.isEmpty()) ? "ALL" : resourceId;
    }

    private RiskDecisionAction parseAction(String decision) {
        if (decision == null) return null;
        try {
            return RiskDecisionAction.valueOf(decision);
        } catch (Exception e) {
            return null;
        }
    }
}
