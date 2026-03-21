package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.dao.entity.RiskBlacklist;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dao.entity.RiskRule;
import com.whu.graduation.taskincentive.dao.entity.RiskWhitelist;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 风控缓存仓库（内存版，MVP）
 */
@Component
public class RiskCacheStore {

    private final List<RiskRule> activeRules = new CopyOnWriteArrayList<>();
    private final Map<String, Set<String>> blacklists = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> whitelists = new ConcurrentHashMap<>();
    private final Map<String, RiskQuota> quotas = new ConcurrentHashMap<>();

    public void refreshRules(List<RiskRule> rules) {
        activeRules.clear();
        if (rules != null && !rules.isEmpty()) {
            activeRules.addAll(rules);
        }
    }

    public List<RiskRule> getActiveRules() {
        return Collections.unmodifiableList(activeRules);
    }

    public void refreshBlacklists(Map<String, Set<String>> data) {
        blacklists.clear();
        if (data != null && !data.isEmpty()) {
            blacklists.putAll(data);
        }
    }

    public void refreshWhitelists(Map<String, Set<String>> data) {
        whitelists.clear();
        if (data != null && !data.isEmpty()) {
            whitelists.putAll(data);
        }
    }

    public Map<String, Set<String>> getBlacklists() {
        return blacklists;
    }

    public Map<String, Set<String>> getWhitelists() {
        return whitelists;
    }

    public void refreshQuotas(Map<String, RiskQuota> data) {
        quotas.clear();
        if (data != null && !data.isEmpty()) {
            quotas.putAll(data);
        }
    }

    public Map<String, RiskQuota> getQuotas() {
        return quotas;
    }

    public static String listKey(String targetType) {
        return targetType == null ? "" : targetType.toUpperCase();
    }

    public static String quotaKey(String scopeType, String scopeId, String resourceType, String resourceId, String periodType) {
        return (scopeType == null ? "" : scopeType.toUpperCase()) + ":" +
                (scopeId == null ? "" : scopeId) + ":" +
                (resourceType == null ? "" : resourceType.toUpperCase()) + ":" +
                (resourceId == null ? "" : resourceId) + ":" +
                (periodType == null ? "" : periodType.toUpperCase());
    }

    public static Map<String, Set<String>> buildListMap(List<? extends Object> list, java.util.function.Function<Object, String> typeFn, java.util.function.Function<Object, String> valueFn) {
        Map<String, Set<String>> map = new ConcurrentHashMap<>();
        if (list == null) return map;
        for (Object item : list) {
            String type = typeFn.apply(item);
            String value = valueFn.apply(item);
            if (type == null || value == null) continue;
            map.computeIfAbsent(listKey(type), k -> ConcurrentHashMap.newKeySet()).add(value);
        }
        return map;
    }

    public static Map<String, RiskQuota> buildQuotaMap(List<RiskQuota> list) {
        Map<String, RiskQuota> map = new ConcurrentHashMap<>();
        if (list == null) return map;
        for (RiskQuota q : list) {
            String key = quotaKey(q.getScopeType(), q.getScopeId(), q.getResourceType(), q.getResourceId(), q.getPeriodType());
            map.put(key, q);
        }
        return map;
    }
}
