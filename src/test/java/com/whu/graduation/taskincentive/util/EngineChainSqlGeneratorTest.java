package com.whu.graduation.taskincentive.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EngineChainSqlGeneratorTest {

    @Test
    public void generate_shouldWriteSqlAndK6Template() throws Exception {
        Path sql = Files.createTempFile("engine-chain-", ".sql");
        Path k6 = Files.createTempFile("engine-chain-", ".js");

        new EngineChainSqlGenerator().generate(sql.toString(), k6.toString(), "token-123");

        String sqlText = Files.readString(sql, Charset.defaultCharset());
        String k6Text = Files.readString(k6, Charset.defaultCharset());

        assertTrue(sqlText.contains("INSERT INTO task_config"));
        assertTrue(sqlText.contains("INSERT INTO risk_decision_log"));
        assertTrue(sqlText.contains("DELETE FROM `user`"));
        assertTrue(k6Text.contains("const BASE_URL"));
        assertTrue(k6Text.contains("process-event-async"));
        assertTrue(k6Text.contains("token-123"));
    }

    @Test
    public void generate_shouldUseFallbackToken_whenBlankToken() throws Exception {
        Path sql = Files.createTempFile("engine-chain-blank-", ".sql");
        Path k6 = Files.createTempFile("engine-chain-blank-", ".js");

        new EngineChainSqlGenerator().generate(sql.toString(), k6.toString(), "   ");

        String k6Text = Files.readString(k6, Charset.defaultCharset());
        assertTrue(k6Text.contains("const BEARER_TOKEN = __ENV.BEARER_TOKEN || ''"));
    }

    @Test
    public void privateHelpers_shouldCoverExtractEscAndReasonBranches() throws Exception {
        Method extractInt = EngineChainSqlGenerator.class.getDeclaredMethod("extractInt", String.class, String.class, int.class);
        extractInt.setAccessible(true);
        assertEquals(7, extractInt.invoke(null, "{\"targetValue\":7}", "targetValue", 1));
        assertEquals(9, extractInt.invoke(null, "{}", "targetValue", 9));
        assertEquals(8, extractInt.invoke(null, "{\"targetValue\":x}", "targetValue", 8));

        Method esc = EngineChainSqlGenerator.class.getDeclaredMethod("esc", String.class);
        esc.setAccessible(true);
        assertEquals("", esc.invoke(null, new Object[]{null}));

        Method escJs = EngineChainSqlGenerator.class.getDeclaredMethod("escJs", String.class);
        escJs.setAccessible(true);
        assertEquals("", escJs.invoke(null, new Object[]{null}));
        assertEquals("a\\\\b\\'c", escJs.invoke(null, "a\\b'c"));

        EngineChainSqlGenerator generator = new EngineChainSqlGenerator();
        Method freezeReason = EngineChainSqlGenerator.class.getDeclaredMethod("freezeReasonByCode", String.class);
        freezeReason.setAccessible(true);
        assertTrue(((String) freezeReason.invoke(generator, "AMOUNT_1D_LIMIT")).contains("日额度"));
        assertTrue(((String) freezeReason.invoke(generator, "QUOTA_EXCEEDED")).contains("配额"));
        assertTrue(((String) freezeReason.invoke(generator, "OTHER")).contains("冻结"));

        Method chooseTriggerEvent = EngineChainSqlGenerator.class.getDeclaredMethod("chooseTriggerEvent", String.class);
        chooseTriggerEvent.setAccessible(true);
        assertEquals("USER_SIGN", chooseTriggerEvent.invoke(null, "CONTINUOUS"));
        assertEquals("USER_LEARN", chooseTriggerEvent.invoke(null, "WINDOW_ACCUMULATE"));
    }

    @Test
    public void privateHelpers_shouldCoverFindRuleByActionPrioritySelection() throws Exception {
        EngineChainSqlGenerator generator = new EngineChainSqlGenerator();

        Field rulesField = EngineChainSqlGenerator.class.getDeclaredField("rules");
        rulesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<EngineChainSqlGenerator.RiskRuleSeed> rules = (List<EngineChainSqlGenerator.RiskRuleSeed>) rulesField.get(generator);
        rules.clear();

        LocalDateTime t = LocalDateTime.now();
        rules.add(new EngineChainSqlGenerator.RiskRuleSeed(1L, "r1", "T", 10, 0, "c", "REJECT", null, t, t, 0, "a", "a", t, t));
        rules.add(new EngineChainSqlGenerator.RiskRuleSeed(2L, "r2", "T", 20, 1, "c", "REJECT", null, t, t, 0, "a", "a", t, t));
        rules.add(new EngineChainSqlGenerator.RiskRuleSeed(3L, "r3", "T", 15, 1, "c", "REJECT", null, t, t, 0, "a", "a", t, t));

        Method findRuleByAction = EngineChainSqlGenerator.class.getDeclaredMethod("findRuleByAction", String.class);
        findRuleByAction.setAccessible(true);

        EngineChainSqlGenerator.RiskRuleSeed hit = (EngineChainSqlGenerator.RiskRuleSeed) findRuleByAction.invoke(generator, "REJECT");
        assertEquals(2L, hit.id);

        Object none = findRuleByAction.invoke(generator, "PASS");
        assertNull(none);
    }

    @Test
    public void privateHelpers_shouldCoverRewardRuleTargetExtraAndHitRuleBranches() throws Exception {
        EngineChainSqlGenerator generator = new EngineChainSqlGenerator();

        Method buildBadges = EngineChainSqlGenerator.class.getDeclaredMethod("buildBadges");
        buildBadges.setAccessible(true);
        buildBadges.invoke(generator);

        Method chooseRewardValue = EngineChainSqlGenerator.class.getDeclaredMethod("chooseRewardValue", String.class);
        chooseRewardValue.setAccessible(true);
        int pointVal = (Integer) chooseRewardValue.invoke(generator, "POINT");
        int badgeCode = (Integer) chooseRewardValue.invoke(generator, "BADGE");
        int itemVal = (Integer) chooseRewardValue.invoke(generator, "ITEM");
        assertTrue(pointVal >= 5 && pointVal <= 100);
        assertTrue(itemVal >= 1 && itemVal <= 5);

        Field badgeMapField = EngineChainSqlGenerator.class.getDeclaredField("badgeIdByCode");
        badgeMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, Long> badgeMap = (Map<Integer, Long>) badgeMapField.get(generator);
        assertTrue(badgeMap.containsKey(badgeCode));

        Method buildRuleConfig = EngineChainSqlGenerator.class.getDeclaredMethod("buildRuleConfig", String.class);
        buildRuleConfig.setAccessible(true);
        assertTrue(((String) buildRuleConfig.invoke(generator, "ACCUMULATE")).contains("targetValue"));
        assertTrue(((String) buildRuleConfig.invoke(generator, "CONTINUOUS")).contains("targetDays"));
        assertTrue(((String) buildRuleConfig.invoke(generator, "STAIR")).contains("stages"));
        assertTrue(((String) buildRuleConfig.invoke(generator, "WINDOW_ACCUMULATE")).contains("windowMinutes"));

        Method parseTaskTarget = EngineChainSqlGenerator.class.getDeclaredMethod("parseTaskTarget", EngineChainSqlGenerator.TaskSeed.class);
        parseTaskTarget.setAccessible(true);
        LocalDateTime t = LocalDateTime.now();
        EngineChainSqlGenerator.TaskSeed acc = new EngineChainSqlGenerator.TaskSeed(1L, "a", "ACCUMULATE", "UNLIMITED", "USER_LEARN", "{\"targetValue\":21}", "POINT", 1, null, 1, t, t, t, t);
        EngineChainSqlGenerator.TaskSeed cont = new EngineChainSqlGenerator.TaskSeed(2L, "b", "CONTINUOUS", "UNLIMITED", "USER_SIGN", "{\"targetDays\":4}", "POINT", 1, null, 1, t, t, t, t);
        EngineChainSqlGenerator.TaskSeed stair = new EngineChainSqlGenerator.TaskSeed(3L, "c", "STAIR", "LIMITED", "OTHER", "{}", "POINT", 1, 1, 1, t, t, t, t);
        EngineChainSqlGenerator.TaskSeed win = new EngineChainSqlGenerator.TaskSeed(4L, "d", "WINDOW_ACCUMULATE", "UNLIMITED", "USER_LEARN", "{\"targetValue\":44}", "POINT", 1, null, 1, t, t, t, t);
        assertEquals(21, (Integer) parseTaskTarget.invoke(generator, acc));
        assertEquals(4, (Integer) parseTaskTarget.invoke(generator, cont));
        assertEquals(60, (Integer) parseTaskTarget.invoke(generator, stair));
        assertEquals(44, (Integer) parseTaskTarget.invoke(generator, win));

        Method buildExtraData = EngineChainSqlGenerator.class.getDeclaredMethod("buildExtraData", EngineChainSqlGenerator.TaskSeed.class, int.class, int.class);
        buildExtraData.setAccessible(true);
        assertTrue(((String) buildExtraData.invoke(generator, cont, 3, 2)).contains("continuousDays"));
        assertTrue(((String) buildExtraData.invoke(generator, stair, 70, 3)).contains("[1,2,3]"));
        assertTrue(((String) buildExtraData.invoke(generator, stair, 35, 2)).contains("[1,2]"));
        assertTrue(((String) buildExtraData.invoke(generator, stair, 12, 2)).contains("[1]"));
        assertTrue(((String) buildExtraData.invoke(generator, stair, 1, 1)).contains("[]"));
        assertTrue(((String) buildExtraData.invoke(generator, win, 7, 2)).contains("entries"));
        assertNull(buildExtraData.invoke(generator, acc, 5, 1));

        Method buildHitRulesJson = EngineChainSqlGenerator.class.getDeclaredMethod("buildHitRulesJson", EngineChainSqlGenerator.RiskRuleSeed.class);
        buildHitRulesJson.setAccessible(true);
        assertEquals("[]", buildHitRulesJson.invoke(generator, new Object[]{null}));
        EngineChainSqlGenerator.RiskRuleSeed hitRule = new EngineChainSqlGenerator.RiskRuleSeed(5L, "r\"x", "T", 99, 1, "c", "REJECT", null, t, t, 1, "a", "a", t, t);
        String hitJson = (String) buildHitRulesJson.invoke(generator, hitRule);
        assertTrue(hitJson.contains("\\\""));
        assertTrue(hitJson.contains("REJECT"));
    }

    @Test
    public void writeK6Template_shouldThrow_whenNoRoleUserGenerated() throws Exception {
        EngineChainSqlGenerator generator = new EngineChainSqlGenerator();

        Field usersField = EngineChainSqlGenerator.class.getDeclaredField("users");
        usersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<EngineChainSqlGenerator.UserSeed> users = (List<EngineChainSqlGenerator.UserSeed>) usersField.get(generator);
        users.clear();

        Method writeK6Template = EngineChainSqlGenerator.class.getDeclaredMethod("writeK6Template", String.class, String.class);
        writeK6Template.setAccessible(true);

        Path k6 = Files.createTempFile("engine-chain-empty-users-", ".js");
        Throwable ex = assertThrows(Throwable.class, () -> writeK6Template.invoke(generator, k6.toString(), "token"));
        assertTrue(ex.getCause() instanceof IllegalStateException);
    }

    @Test
    public void main_andGenerateOverload_shouldCoverArgumentBranches() throws Exception {
        Path sql1 = Files.createTempFile("engine-chain-main-1-", ".sql");
        Path k61 = Files.createTempFile("engine-chain-main-1-", ".js");
        EngineChainSqlGenerator.main(new String[]{sql1.toString(), k61.toString(), "t-main"});
        assertTrue(Files.exists(sql1));
        assertTrue(Files.exists(k61));

        Path sql2 = Files.createTempFile("engine-chain-main-2-", ".sql");
        new EngineChainSqlGenerator().generate(sql2.toString());
        assertTrue(Files.exists(sql2));
    }

    @Test
    public void helperMethods_shouldCoverJoinEscProgressAndVisibilityBranches() throws Exception {
        Method joinLongList = EngineChainSqlGenerator.class.getDeclaredMethod("joinLongList", List.class);
        joinLongList.setAccessible(true);
        assertEquals("", joinLongList.invoke(null, List.of()));
        assertEquals("1,2,3", joinLongList.invoke(null, List.of(1L, 2L, 3L)));

        Method escJson = EngineChainSqlGenerator.class.getDeclaredMethod("escJson", String.class);
        escJson.setAccessible(true);
        assertEquals("", escJson.invoke(null, new Object[]{null}));
        assertEquals("a\\\\b\\\"c", escJson.invoke(null, "a\\b\"c"));

        EngineChainSqlGenerator generator = new EngineChainSqlGenerator();
        LocalDateTime now = LocalDateTime.now();
        EngineChainSqlGenerator.TaskSeed task = new EngineChainSqlGenerator.TaskSeed(1L, "t", "ACCUMULATE", "UNLIMITED", "USER_LEARN", "{\"targetValue\":30}", "POINT", 1, null, 1, now, now, now, now);

        Method progressByStatus = EngineChainSqlGenerator.class.getDeclaredMethod("progressByStatus", EngineChainSqlGenerator.TaskSeed.class, int.class);
        progressByStatus.setAccessible(true);
        int cancelled = (Integer) progressByStatus.invoke(generator, task, 4);
        int completed = (Integer) progressByStatus.invoke(generator, task, 3);
        int inProgress = (Integer) progressByStatus.invoke(generator, task, 2);
        int accepted = (Integer) progressByStatus.invoke(generator, task, 1);
        assertTrue(cancelled >= 0);
        assertTrue(completed >= 30);
        assertTrue(inProgress >= 1 && inProgress < 30);
        assertTrue(accepted >= 0);

        Method visibleTasksForUser = EngineChainSqlGenerator.class.getDeclaredMethod("visibleTasksForUser", EngineChainSqlGenerator.UserSeed.class);
        visibleTasksForUser.setAccessible(true);
        Field tasksField = EngineChainSqlGenerator.class.getDeclaredField("tasks");
        tasksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<EngineChainSqlGenerator.TaskSeed> tasks = (List<EngineChainSqlGenerator.TaskSeed>) tasksField.get(generator);
        tasks.clear();

        LocalDateTime oldStart = now.minusDays(2);
        LocalDateTime newStart = now.plusDays(3);
        tasks.add(new EngineChainSqlGenerator.TaskSeed(2L, "old", "ACCUMULATE", "UNLIMITED", "USER_LEARN", "{}", "POINT", 1, null, 1, oldStart, now, now, now));
        tasks.add(new EngineChainSqlGenerator.TaskSeed(3L, "new", "ACCUMULATE", "UNLIMITED", "USER_LEARN", "{}", "POINT", 1, null, 1, newStart, now.plusDays(5), now, now));

        EngineChainSqlGenerator.UserSeed user = new EngineChainSqlGenerator.UserSeed(1L, "u", "p", "ROLE_USER", 0, now.minusDays(1), now.minusDays(1));
        @SuppressWarnings("unchecked")
        List<EngineChainSqlGenerator.TaskSeed> visible = (List<EngineChainSqlGenerator.TaskSeed>) visibleTasksForUser.invoke(generator, user);
        assertEquals(1, visible.size());
        assertEquals(2L, visible.get(0).id);
    }
}


