package com.whu.graduation.taskincentive.service;

import com.whu.graduation.taskincentive.dao.mapper.TaskConfigHistoryMapper;
import com.whu.graduation.taskincentive.dao.mapper.TaskConfigMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.dto.TaskAnalyticsDTO;
import com.whu.graduation.taskincentive.service.impl.TaskAnalyticsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class TaskAnalyticsServiceImplTest {

    @Test
    void taskConfigOverview_shouldCountRewardedCompletedInstancesByCompletionWindow() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        List<String> sqls = new ArrayList<>();
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            sqls.add(sql);
            if (sql.startsWith("SELECT AVG(TIMESTAMPDIFF(MINUTE, create_time, update_time))")) {
                return 30.0D;
            }
            if (sql.contains("FROM user_task_instance WHERE task_id=? AND status>0")) {
                return 100L;
            }
            if (sql.contains("FROM user_task_instance WHERE task_id=? AND status=3")) {
                return 80L;
            }
            if (sql.contains("FROM user_task_instance uti WHERE uti.task_id=? AND uti.status=3")) {
                return 75L;
            }
            return 0L;
        }).when(jdbcTemplate).queryForObject(anyString(), eq(Number.class), org.mockito.ArgumentMatchers.<Object[]>any());

        TaskAnalyticsServiceImpl service = new TaskAnalyticsServiceImpl(
                jdbcTemplate,
                mock(TaskConfigHistoryMapper.class),
                mock(UserTaskInstanceMapper.class),
                mock(UserRewardRecordMapper.class),
                mock(TaskConfigMapper.class),
                mock(UserMapper.class)
        );

        TaskAnalyticsDTO.MetricOverview overview = service.taskConfigOverview(9L, 7);

        assertEquals(7, overview.getDays());
        assertEquals(100L, overview.getAcceptedCount());
        assertEquals(80L, overview.getCompletedCount());
        assertEquals(75L, overview.getRewardedCount());
        assertEquals(93.75D, overview.getRewardRate());
        assertEquals(30.0D, overview.getAvgCompletionMinutes());
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("status=3") && sql.contains("update_time>=?") && sql.contains("update_time<?")));
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("EXISTS (SELECT 1 FROM user_reward_record rr") && sql.contains("rr.create_time<?")));
    }

    @Test
    void userTaskOverview_shouldPopulateDaysAndUseCompletedWindowForRewardRate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        List<String> sqls = new ArrayList<>();
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            sqls.add(sql);
            if (sql.startsWith("SELECT COALESCE(COUNT(1),0) FROM user_task_instance uti")) {
                return 120L;
            }
            if (sql.startsWith("SELECT COUNT(1) FROM user_task_instance uti") && !sql.contains("EXISTS (SELECT 1 FROM user_reward_record rr")) {
                return 60L;
            }
            if (sql.startsWith("SELECT COUNT(1) FROM user_task_instance uti") && sql.contains("EXISTS (SELECT 1 FROM user_reward_record rr")) {
                return 59L;
            }
            if (sql.startsWith("SELECT AVG(TIMESTAMPDIFF(MINUTE, uti.create_time, uti.update_time)) FROM user_task_instance uti")) {
                return 45.2D;
            }
            return 0L;
        }).when(jdbcTemplate).queryForObject(anyString(), eq(Number.class), org.mockito.ArgumentMatchers.<Object[]>any());

        TaskAnalyticsServiceImpl service = new TaskAnalyticsServiceImpl(
                jdbcTemplate,
                mock(TaskConfigHistoryMapper.class),
                mock(UserTaskInstanceMapper.class),
                mock(UserRewardRecordMapper.class),
                mock(TaskConfigMapper.class),
                mock(UserMapper.class)
        );

        TaskAnalyticsDTO.MetricOverview overview = service.userTaskOverview(null, null, null, null, null);

        assertEquals(7, overview.getDays());
        assertEquals(120L, overview.getAcceptedCount());
        assertEquals(60L, overview.getCompletedCount());
        assertEquals(59L, overview.getRewardedCount());
        assertEquals(50.0D, overview.getCompletionRate());
        assertEquals(98.33D, overview.getRewardRate());
        assertEquals(45.2D, overview.getAvgCompletionMinutes());
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("uti.status=3") && sql.contains("uti.update_time>=?") && sql.contains("uti.update_time<?")));
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("EXISTS (SELECT 1 FROM user_reward_record rr") && sql.contains("rr.create_time<?")));
    }
}


