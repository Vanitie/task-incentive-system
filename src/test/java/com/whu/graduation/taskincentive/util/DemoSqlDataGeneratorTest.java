package com.whu.graduation.taskincentive.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DemoSqlDataGeneratorTest {

    @Test
    public void generate_shouldWriteDemoSql() throws Exception {
        Path sql = Files.createTempFile("demo-data-", ".sql");

        new DemoSqlDataGenerator().generate(sql.toString());

        String text = Files.readString(sql, Charset.defaultCharset());
        assertTrue(text.contains("INSERT INTO `user`"));
        assertTrue(text.contains("INSERT INTO task_config"));
        assertTrue(text.contains("INSERT INTO user_task_instance"));
        assertTrue(text.contains("INSERT INTO user_reward_record"));
        assertTrue(text.contains("SET FOREIGN_KEY_CHECKS = 1;"));
    }

    @Test
    public void main_shouldCoverArgsBranch_andWriteDefaultOrCustomFile() throws Exception {
        Path custom = Files.createTempFile("demo-data-main-", ".sql");
        DemoSqlDataGenerator.main(new String[]{custom.toString()});
        assertTrue(Files.exists(custom));

        Path defaultPath = Path.of("demo_data.sql");
        Files.deleteIfExists(defaultPath);
        DemoSqlDataGenerator.main(new String[]{});
        assertTrue(Files.exists(defaultPath));
        Files.deleteIfExists(defaultPath);
    }

    @Test
    public void privateHelpers_shouldCoverEscAndParseTargetBranches() throws Exception {
        java.lang.reflect.Method esc = DemoSqlDataGenerator.class.getDeclaredMethod("esc", String.class);
        esc.setAccessible(true);
        assertEquals("", esc.invoke(null, new Object[]{null}));
        assertEquals("a\\\\b''c", esc.invoke(null, "a\\b'c"));

        java.lang.reflect.Method parse = DemoSqlDataGenerator.class.getDeclaredMethod("parseTargetFromRule", String.class);
        parse.setAccessible(true);
        assertEquals(12, parse.invoke(null, "{\"target\":12}"));
        assertEquals(1, parse.invoke(null, "{\"target\":x}"));
        assertEquals(1, parse.invoke(null, "{bad}"));
    }

    @Test
    public void buildUserTaskInstances_shouldSkipWhenNoVisibleTasks() throws Exception {
        DemoSqlDataGenerator g = new DemoSqlDataGenerator();

        java.lang.reflect.Field allUsersF = DemoSqlDataGenerator.class.getDeclaredField("allUsers");
        allUsersF.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<DemoSqlDataGenerator.UserSeed> allUsers = (List<DemoSqlDataGenerator.UserSeed>) allUsersF.get(g);

        java.lang.reflect.Field allTasksF = DemoSqlDataGenerator.class.getDeclaredField("allTasks");
        allTasksF.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<DemoSqlDataGenerator.TaskSeed> allTasks = (List<DemoSqlDataGenerator.TaskSeed>) allTasksF.get(g);

        LocalDate today = LocalDate.now();
        allUsers.add(new DemoSqlDataGenerator.UserSeed(1L, "u1", "p", "ROLE_USER", 0,
                LocalDateTime.of(today, LocalTime.NOON), LocalDateTime.of(today, LocalTime.NOON)));

        // 任务时间窗口不与 today 重合，触发 visibleTasks 为空分支
        LocalDate oldDay = today.minusDays(30);
        allTasks.add(new DemoSqlDataGenerator.TaskSeed(2L, "t", "TASK_TYPE_BEHAVIOR", "USER_SIGN", "{\"target\":1}",
                "REWARD_POINT", 1, null, null, 1,
                LocalDateTime.of(oldDay, LocalTime.MIN), LocalDateTime.of(oldDay, LocalTime.MAX),
                LocalDateTime.of(oldDay, LocalTime.NOON), LocalDateTime.of(oldDay, LocalTime.NOON)));

        java.lang.reflect.Method m = DemoSqlDataGenerator.class.getDeclaredMethod("buildUserTaskInstancesAndRewardsAndLogs", LocalDate.class, LocalDate.class);
        m.setAccessible(true);
        m.invoke(g, today, today);

        java.lang.reflect.Field idxF = DemoSqlDataGenerator.class.getDeclaredField("userTaskIndex");
        idxF.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, DemoSqlDataGenerator.UserTaskInstanceSeed> idx = (Map<String, DemoSqlDataGenerator.UserTaskInstanceSeed>) idxF.get(g);
        assertTrue(idx.isEmpty());
    }
}
