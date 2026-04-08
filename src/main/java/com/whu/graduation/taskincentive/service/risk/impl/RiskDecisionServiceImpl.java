package com.whu.graduation.taskincentive.service.risk.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.graduation.taskincentive.common.enums.RiskDecisionAction;
import com.whu.graduation.taskincentive.constant.RiskConstants;
import com.whu.graduation.taskincentive.dao.entity.RiskBlacklist;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dao.entity.RewardFreezeRecord;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dao.entity.RiskWhitelist;
import com.whu.graduation.taskincentive.dao.mapper.RewardFreezeRecordMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskBlacklistMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskDecisionLogMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskQuotaMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskRuleMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskWhitelistMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RiskDecisionServiceImpl.class);

    private final RiskDecisionEngine decisionEngine;
    private final RiskMetricStore metricStore;
    private final RiskCacheStore cacheStore;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RiskDecisionPersistProducer persistProducer;

    @Autowired
    private RiskRuleMapper riskRuleMapper;
    @Autowired
    private RiskQuotaMapper riskQuotaMapper;
    @Autowired
    private RiskWhitelistMapper riskWhitelistMapper;
    @Autowired
    private RiskBlacklistMapper riskBlacklistMapper;
    @Autowired
    private RiskDecisionLogMapper riskDecisionLogMapper;
    @Autowired
    private RewardFreezeRecordMapper rewardFreezeRecordMapper;
    @Autowired
    private UserRewardRecordMapper userRewardRecordMapper;

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
                return buildResponseOnly(traceId, RiskDecisionAction.REJECT, RiskConstants.REASON_REPLAY,
                        Collections.emptyList(), 0, null);
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
            Map<String, RiskQuota> quotaSnapshot = snapshotQuotas();
            if (!checkQuota(request, quotaSnapshot)) {
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

    @Override
    public RiskDecisionResponse evaluateDirect(RiskDecisionRequest request) {
        long start = System.currentTimeMillis();
        String traceId = request.getRequestId() == null ? String.valueOf(IdWorker.getId()) : request.getRequestId();

        try {
            if (!checkDedupDirect(request)) {
                return buildResponseOnly(traceId, RiskDecisionAction.REJECT, RiskConstants.REASON_REPLAY,
                        Collections.emptyList(), 0, null);
            }

            if (hitWhitelistDirect(request)) {
                return buildAndLogDirect(request, traceId, RiskDecisionAction.PASS, RiskConstants.REASON_WHITELIST,
                        Collections.emptyList(), start, 0, null);
            }
            if (hitBlacklistDirect(request)) {
                return buildAndLogDirect(request, traceId, RiskDecisionAction.REJECT, RiskConstants.REASON_BLACKLIST,
                        Collections.emptyList(), start, 80, null);
            }

            List<RiskQuota> quotas = loadQuotasDirect();
            if (!checkQuotaDirect(request, quotas)) {
                return buildAndLogDirect(request, traceId, RiskDecisionAction.REJECT, RiskConstants.REASON_QUOTA_EXCEEDED,
                        Collections.emptyList(), start, 70, null);
            }

            List<RiskRule> rules = loadActiveRulesDirect();
            Map<String, Object> context = buildContextDirect(request);
            RiskDecisionResponse response = decisionEngine.evaluateRules(rules, context);
            if (response == null) {
                return buildAndLogDirect(request, traceId, RiskDecisionAction.FREEZE, RiskConstants.REASON_DEFAULT,
                        Collections.emptyList(), start, 60, null);
            }
            response.setTraceId(traceId);

            RiskDecisionAction action = parseAction(response.getDecision());
            if (action == null) {
                return buildAndLogDirect(request, traceId, RiskDecisionAction.FREEZE, RiskConstants.REASON_DEFAULT,
                        response.getHitRules(), start, response.getRiskScore(), response.getDegradeRatio());
            }
            return buildAndLogDirect(request, traceId, action, response.getReasonCode(),
                    response.getHitRules(), start, response.getRiskScore(), response.getDegradeRatio());
        } catch (Exception e) {
            return buildAndLogDirect(request, traceId, RiskDecisionAction.FREEZE, RiskConstants.REASON_DEFAULT,
                    Collections.emptyList(), start, 90, null);
        }
    }

    private RiskDecisionResponse buildAndLogDirect(RiskDecisionRequest request, String traceId,
                                                   RiskDecisionAction action, String reason,
                                                   List<RiskHitRule> hitRules, long start,
                                                   Integer riskScore, Double degradeRatio) {
        long latency = System.currentTimeMillis() - start;
        RiskDecisionResponse response = buildResponseOnly(traceId, action, reason, hitRules, riskScore, degradeRatio);

        RiskDecisionLog decisionLog = RiskDecisionLog.builder()
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
        riskDecisionLogMapper.insert(decisionLog);

        if (action == RiskDecisionAction.FREEZE) {
            RewardFreezeRecord freeze = RewardFreezeRecord.builder()
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
            rewardFreezeRecordMapper.insert(freeze);
        }
        return response;
    }

    private RiskDecisionResponse buildAndLog(RiskDecisionRequest request, String traceId,
                                             RiskDecisionAction action, String reason,
                                             List<RiskHitRule> hitRules, long start,
                                             Integer riskScore, Double degradeRatio) {
        long latency = System.currentTimeMillis() - start;
        RiskDecisionResponse response = buildResponseOnly(traceId, action, reason, hitRules, riskScore, degradeRatio);

        RiskDecisionLog decisionLog = RiskDecisionLog.builder()
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
                    .decisionLog(decisionLog)
                    .freezeRecord(freeze)
                    .build();
            String userKey = request.getUserId() == null ? "0" : String.valueOf(request.getUserId());
            persistProducer.send(userKey, msg);
        } catch (Exception e) {
            // 风控主流程仍返回决策结果，但记录落库消息发送失败需要留痕，供对账补偿。
            log.error("risk decision persist message send failed, requestId={}, userId={}, taskId={}",
                    request.getRequestId(), request.getUserId(), request.getTaskId(), e);
        }
        return response;
    }

    private boolean checkDedupDirect(RiskDecisionRequest request) {
        if (request.getRequestId() == null || request.getRequestId().isEmpty()) {
            return true;
        }
        QueryWrapper<RiskDecisionLog> wrapper = new QueryWrapper<RiskDecisionLog>()
                .eq("request_id", request.getRequestId())
                .last("LIMIT 1");
        return riskDecisionLogMapper.selectOne(wrapper) == null;
    }

    private boolean hitWhitelistDirect(RiskDecisionRequest request) {
        return hitListDirect(true, request);
    }

    private boolean hitBlacklistDirect(RiskDecisionRequest request) {
        return hitListDirect(false, request);
    }

    private boolean hitListDirect(boolean whitelist, RiskDecisionRequest request) {
        Date now = new Date();
        if (request.getUserId() != null) {
            if (existsInList(whitelist, "USER", String.valueOf(request.getUserId()), now)) {
                return true;
            }
        }
        if (request.getDeviceId() != null && !request.getDeviceId().isEmpty()) {
            if (existsInList(whitelist, "DEVICE", request.getDeviceId(), now)) {
                return true;
            }
        }
        if (request.getIp() != null && !request.getIp().isEmpty()) {
            if (existsInList(whitelist, "IP", request.getIp(), now)) {
                return true;
            }
        }
        return false;
    }

    private boolean existsInList(boolean whitelist, String type, String value, Date now) {
        if (whitelist) {
            QueryWrapper<RiskWhitelist> wrapper = new QueryWrapper<RiskWhitelist>()
                    .eq("status", 1)
                    .eq("target_type", type)
                    .eq("target_value", value)
                    .and(q -> q.isNull("expire_at").or().gt("expire_at", now))
                    .last("LIMIT 1");
            return riskWhitelistMapper.selectOne(wrapper) != null;
        }
        QueryWrapper<RiskBlacklist> wrapper = new QueryWrapper<RiskBlacklist>()
                .eq("status", 1)
                .eq("target_type", type)
                .eq("target_value", value)
                .and(q -> q.isNull("expire_at").or().gt("expire_at", now))
                .last("LIMIT 1");
        return riskBlacklistMapper.selectOne(wrapper) != null;
    }

    private List<RiskQuota> loadQuotasDirect() {
        List<RiskQuota> list = riskQuotaMapper.selectList(new QueryWrapper<RiskQuota>());
        return list == null ? Collections.emptyList() : list;
    }

    private List<RiskRule> loadActiveRulesDirect() {
        List<RiskRule> list = riskRuleMapper.selectList(new QueryWrapper<RiskRule>().eq("status", 1).orderByDesc("priority"));
        return list == null ? Collections.emptyList() : list;
    }

    private boolean checkQuotaDirect(RiskDecisionRequest request, List<RiskQuota> quotas) {
        if (quotas == null || quotas.isEmpty()) return true;
        LocalDateTime time = request.getEventTime() == null ? LocalDateTime.now() : request.getEventTime();
        String resourceType = normalizeResourceType(request.getResourceType());
        String resourceId = normalizeResourceId(request.getResourceId());
        for (RiskQuota quota : quotas) {
            if (!isQuotaApplicable(quota, request, resourceType, resourceId)) {
                continue;
            }
            long used = countQuotaUsageDirect(quota, request, time);
            Integer limit = quota.getLimitValue();
            if (limit != null && used >= limit) {
                return false;
            }
        }
        return true;
    }

    private long countQuotaUsageDirect(RiskQuota quota, RiskDecisionRequest request, LocalDateTime time) {
        LocalDateTime start;
        String periodType = quota.getPeriodType() == null || quota.getPeriodType().isEmpty()
                ? "DAY" : quota.getPeriodType().toUpperCase();
        if ("HOUR".equalsIgnoreCase(periodType)) {
            start = time.withMinute(0).withSecond(0).withNano(0);
        } else if ("MINUTE".equalsIgnoreCase(periodType)) {
            start = time.withSecond(0).withNano(0);
        } else {
            start = time.withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        Date startDate = Date.from(start.atZone(ZoneId.systemDefault()).toInstant());
        Date endDate = Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        QueryWrapper<RiskDecisionLog> wrapper = new QueryWrapper<RiskDecisionLog>()
                .ge("created_at", startDate)
                .lt("created_at", endDate);

        String scopeType = quota.getScopeType() == null ? "" : quota.getScopeType().toUpperCase();
        String scopeId = quota.getScopeId() == null ? "ALL" : quota.getScopeId();
        if ("USER".equals(scopeType) && request.getUserId() != null && !"ALL".equalsIgnoreCase(scopeId)) {
            wrapper.eq("user_id", request.getUserId());
        }
        if ("TASK".equals(scopeType) && request.getTaskId() != null && !"ALL".equalsIgnoreCase(scopeId)) {
            wrapper.eq("task_id", request.getTaskId());
        }
        Long count = riskDecisionLogMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private Map<String, Object> buildContextDirect(RiskDecisionRequest request) {
        Map<String, Object> ctx = new HashMap<>();
        LocalDateTime time = request.getEventTime() == null ? LocalDateTime.now() : request.getEventTime();
        Date nowDate = Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        Date minuteStart = Date.from(time.withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant());
        Date hourStart = Date.from(time.withMinute(0).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant());
        Date dayStart = Date.from(time.withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant());

        long count1m = countDecisionLogsByWindow(request.getUserId(), request.getTaskId(), minuteStart, nowDate);
        long count1h = countDecisionLogsByWindow(request.getUserId(), request.getTaskId(), hourStart, nowDate);
        long amount1d = sumRewardAmountByWindow(request.getUserId(), request.getTaskId(), dayStart, nowDate);

        ctx.put("eventTime", time);
        ctx.put("userId", request.getUserId());
        ctx.put("taskId", request.getTaskId());
        ctx.put("eventType", request.getEventType());
        ctx.put("amount", request.getAmount() == null ? 1 : request.getAmount());
        ctx.put("resourceType", normalizeResourceType(request.getResourceType()));
        ctx.put("resourceId", normalizeResourceId(request.getResourceId()));
        ctx.put("count_1m", count1m);
        ctx.put("count_1h", count1h);
        ctx.put("amount_1d", amount1d);
        // 当前库表未存储 device/ip 维度的行为明细，直连链路按 0 处理。
        ctx.put("distinct_device_1d", 0L);
        ctx.put("ip_count_1m", 0L);
        ctx.put("device_count_1m", 0L);
        if (request.getExt() != null) {
            ctx.putAll(request.getExt());
        }
        return ctx;
    }

    private long countDecisionLogsByWindow(Long userId, Long taskId, Date start, Date end) {
        QueryWrapper<RiskDecisionLog> wrapper = new QueryWrapper<RiskDecisionLog>()
                .ge("created_at", start)
                .lt("created_at", end);
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (taskId != null) {
            wrapper.eq("task_id", taskId);
        }
        Long cnt = riskDecisionLogMapper.selectCount(wrapper);
        return cnt == null ? 0L : cnt;
    }

    private long sumRewardAmountByWindow(Long userId, Long taskId, Date start, Date end) {
        QueryWrapper<com.whu.graduation.taskincentive.dao.entity.UserRewardRecord> wrapper =
                new QueryWrapper<com.whu.graduation.taskincentive.dao.entity.UserRewardRecord>()
                        .select("reward_value")
                        .ge("create_time", start)
                        .lt("create_time", end);
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (taskId != null) {
            wrapper.eq("task_id", taskId);
        }
        List<com.whu.graduation.taskincentive.dao.entity.UserRewardRecord> list = userRewardRecordMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (com.whu.graduation.taskincentive.dao.entity.UserRewardRecord row : list) {
            if (row != null && row.getRewardValue() != null) {
                sum += row.getRewardValue();
            }
        }
        return sum;
    }

    private RiskDecisionResponse buildResponseOnly(String traceId,
                                                   RiskDecisionAction action,
                                                   String reason,
                                                   List<RiskHitRule> hitRules,
                                                   Integer riskScore,
                                                   Double degradeRatio) {
        return RiskDecisionResponse.builder()
                .decision(action.name())
                .reasonCode(reason)
                .hitRules(hitRules)
                .riskScore(riskScore == null ? 0 : riskScore)
                .traceId(traceId)
                .degradeRatio(degradeRatio)
                .build();
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

    private Map<String, RiskQuota> snapshotQuotas() {
        Map<String, RiskQuota> current = cacheStore.getQuotas();
        if (current == null || current.isEmpty()) {
            return Collections.emptyMap();
        }
        return new HashMap<>(current);
    }

    private boolean checkQuota(RiskDecisionRequest request, Map<String, RiskQuota> quotas) {
        if (quotas == null || quotas.isEmpty()) return true;
        LocalDateTime time = request.getEventTime() == null ? LocalDateTime.now() : request.getEventTime();
        String resourceType = normalizeResourceType(request.getResourceType());
        String resourceId = normalizeResourceId(request.getResourceId());

        for (RiskQuota quota : quotas.values()) {
            if (!isQuotaApplicable(quota, request, resourceType, resourceId)) {
                continue;
            }
            if (!consumeQuota(quota, time)) {
                return false;
            }
        }
        return true;
    }

    private boolean isQuotaApplicable(RiskQuota quota, RiskDecisionRequest request,
                                      String resourceType, String resourceId) {
        if (quota == null || quota.getLimitValue() == null) return false;

        String quotaResourceType = normalizeResourceType(quota.getResourceType());
        String quotaResourceId = normalizeResourceId(quota.getResourceId());
        if (!"ALL".equalsIgnoreCase(quotaResourceType)
                && !quotaResourceType.equalsIgnoreCase(resourceType)) {
            return false;
        }
        if (!"ALL".equalsIgnoreCase(quotaResourceId)
                && !quotaResourceId.equalsIgnoreCase(resourceId)) {
            return false;
        }

        String scopeType = quota.getScopeType() == null ? "" : quota.getScopeType().toUpperCase();
        String scopeId = quota.getScopeId() == null ? "ALL" : quota.getScopeId();
        if ("USER".equals(scopeType)) {
            if (request.getUserId() == null) return false;
            return "ALL".equalsIgnoreCase(scopeId)
                    || scopeId.equals(String.valueOf(request.getUserId()));
        }
        if ("TASK".equals(scopeType)) {
            if (request.getTaskId() == null) return false;
            return "ALL".equalsIgnoreCase(scopeId)
                    || scopeId.equals(String.valueOf(request.getTaskId()));
        }
        if ("GLOBAL".equals(scopeType)) {
            return "ALL".equalsIgnoreCase(scopeId);
        }
        return false;
    }

    private boolean consumeQuota(RiskQuota quota, LocalDateTime time) {
        String scopeType = quota.getScopeType() == null ? "" : quota.getScopeType().toUpperCase();
        String scopeId = quota.getScopeId() == null ? "ALL" : quota.getScopeId();
        String resourceType = normalizeResourceType(quota.getResourceType());
        String resourceId = normalizeResourceId(quota.getResourceId());
        String periodType = quota.getPeriodType() == null || quota.getPeriodType().isEmpty()
                ? "DAY" : quota.getPeriodType().toUpperCase();

        String key = RiskCacheStore.quotaKey(scopeType, scopeId, resourceType, resourceId, periodType);

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
