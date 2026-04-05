package com.whu.graduation.taskincentive.service.risk;

import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RiskCacheStoreTest {

    @Test
    public void refreshRulesAndQuotas_shouldHandleNullEmptyAndNonEmpty() {
        RiskCacheStore store = new RiskCacheStore();

        store.refreshRules(null);
        assertTrue(store.getActiveRules().isEmpty());

        store.refreshRules(List.of());
        assertTrue(store.getActiveRules().isEmpty());

        store.refreshRules(List.of(com.whu.graduation.taskincentive.dao.entity.RiskRule.builder().id(1L).build()));
        assertEquals(1, store.getActiveRules().size());
        assertThrows(UnsupportedOperationException.class, () -> store.getActiveRules().add(null));

        store.refreshQuotas(null);
        assertTrue(store.getQuotas().isEmpty());

        store.refreshQuotas(Map.of());
        assertTrue(store.getQuotas().isEmpty());
    }

    @Test
    public void keyHelpers_shouldHandleNullInputs() {
        assertEquals("", RiskCacheStore.listKey(null));
        assertEquals("USER", RiskCacheStore.listKey("user"));

        assertEquals("::::", RiskCacheStore.quotaKey(null, null, null, null, null));
    }

    @Test
    public void refreshMethods_shouldReplaceData() {
        RiskCacheStore store = new RiskCacheStore();

        store.refreshBlacklists(Map.of("USER", Set.of("1001")));
        store.refreshWhitelists(Map.of("IP", Set.of("10.0.0.1")));

        assertTrue(store.getBlacklists().containsKey("USER"));
        assertTrue(store.getWhitelists().containsKey("IP"));

        store.refreshBlacklists(Map.of());
        store.refreshWhitelists(Map.of());

        assertTrue(store.getBlacklists().isEmpty());
        assertTrue(store.getWhitelists().isEmpty());
    }

    @Test
    public void buildQuotaMap_shouldUseComposedKey() {
        RiskQuota quota = RiskQuota.builder()
                .scopeType("USER")
                .scopeId("1001")
                .resourceType("POINT")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(100)
                .build();

        Map<String, RiskQuota> map = RiskCacheStore.buildQuotaMap(List.of(quota));

        assertEquals(1, map.size());
        assertTrue(map.containsKey("USER:1001:POINT:ALL:DAY"));
    }

    @Test
    public void buildListMap_shouldIgnoreNullTypeAndValue() {
        List<Object> list = List.of(
                new String[]{"USER", "1001"},
                new String[]{null, "1002"},
                new String[]{"USER", null}
        );

        Map<String, Set<String>> map = RiskCacheStore.buildListMap(
                list,
                item -> ((String[]) item)[0],
                item -> ((String[]) item)[1]
        );

        assertTrue(map.containsKey("USER"));
        assertTrue(map.get("USER").contains("1001"));
        assertFalse(map.get("USER").contains("1002"));
    }

    @Test
    public void buildListAndQuotaMap_shouldReturnEmptyWhenInputNull() {
        Map<String, Set<String>> listMap = RiskCacheStore.buildListMap(
                null,
                item -> "USER",
                item -> "1001"
        );
        assertTrue(listMap.isEmpty());

        Map<String, RiskQuota> quotaMap = RiskCacheStore.buildQuotaMap(null);
        assertTrue(quotaMap.isEmpty());
    }
}

