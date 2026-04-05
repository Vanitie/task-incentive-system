package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.TaskView;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.TaskStockService;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserViewServiceImplTest {

    @Mock
    private TaskConfigService taskConfigService;
    @Mock
    private TaskStockService taskStockService;
    @Mock
    private UserTaskInstanceService userTaskInstanceService;

    private UserViewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserViewServiceImpl();
        ReflectionTestUtils.setField(service, "taskConfigService", taskConfigService);
        ReflectionTestUtils.setField(service, "taskStockService", taskStockService);
        ReflectionTestUtils.setField(service, "userTaskInstanceService", userTaskInstanceService);
    }

    @Test
    void listAvailableTasks_shouldEvaluateMainBranchesAndFinalReason() {
        Date now = new Date();
        TaskConfig enabled = config(1L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig disabled = config(2L, 0, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig notStarted = config(3L, 1, "UNLIMITED", shiftMinutes(now, 10), shiftMinutes(now, 20));
        TaskConfig ended = config(4L, 1, "UNLIMITED", shiftMinutes(now, -20), shiftMinutes(now, -10));
        TaskConfig limitedNoStockObj = config(5L, 1, "LIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig limitedZero = config(6L, 1, "LIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig limitedEnough = config(7L, 1, "LIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig acceptedOverride = config(8L, 1, "LIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));

        when(taskConfigService.listAll()).thenReturn(List.of(
                enabled, disabled, notStarted, ended, limitedNoStockObj, limitedZero, limitedEnough, acceptedOverride
        ));
        when(userTaskInstanceService.selectByUserId(100L)).thenReturn(List.of(
                instance(8L, 1),
                instance(999L, 0)
        ));
        when(taskStockService.getByIdAndStageIndex(anyLong(), eq(1))).thenAnswer(invocation -> {
            Long taskId = invocation.getArgument(0);
            if (taskId.equals(5L)) {
                return null;
            }
            if (taskId.equals(6L)) {
                return stock(6L, 0);
            }
            return stock(taskId, 5);
        });

        List<TaskView> result = service.listAvailableTasks(100L, null);
        Map<Long, TaskView> byId = result.stream().collect(Collectors.toMap(v -> v.getTaskConfig().getId(), Function.identity()));

        assertTrue(byId.get(1L).getCanAccept());
        assertNull(byId.get(1L).getReason());

        assertFalse(byId.get(2L).getCanAccept());
        assertEquals("任务未启用", byId.get(2L).getReason());

        assertFalse(byId.get(3L).getCanAccept());
        assertEquals("任务尚未开始", byId.get(3L).getReason());

        assertFalse(byId.get(4L).getCanAccept());
        assertEquals("任务已结束", byId.get(4L).getReason());

        assertFalse(byId.get(5L).getCanAccept());
        assertEquals("库存不足", byId.get(5L).getReason());
        assertNull(byId.get(5L).getRemainingStock());

        assertFalse(byId.get(6L).getCanAccept());
        assertEquals("库存不足", byId.get(6L).getReason());
        assertEquals(0, byId.get(6L).getRemainingStock());

        assertTrue(byId.get(7L).getCanAccept());
        assertEquals(5, byId.get(7L).getRemainingStock());

        assertTrue(byId.get(8L).getUserAccepted());
        assertFalse(byId.get(8L).getCanAccept());
        assertEquals("已领取", byId.get(8L).getReason());
    }

    @Test
    void listAvailableTasks_shouldApplyStateFilters() {
        Date now = new Date();
        TaskConfig ongoing = config(11L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig accepted = config(12L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig expired = config(13L, 1, "UNLIMITED", shiftMinutes(now, -20), shiftMinutes(now, -1));

        when(taskConfigService.listAll()).thenReturn(List.of(ongoing, accepted, expired));
        when(userTaskInstanceService.selectByUserId(101L)).thenReturn(List.of(instance(12L, 2)));

        assertEquals(1, service.listAvailableTasks(101L, "ongoing").size());
        assertEquals(11L, service.listAvailableTasks(101L, "ongoing").get(0).getTaskConfig().getId());

        assertEquals(1, service.listAvailableTasks(101L, "completed").size());
        assertEquals(12L, service.listAvailableTasks(101L, "completed").get(0).getTaskConfig().getId());

        assertEquals(1, service.listAvailableTasks(101L, "expired").size());
        assertEquals(13L, service.listAvailableTasks(101L, "expired").get(0).getTaskConfig().getId());
    }

    @Test
    void listAvailableTasksPage_shouldKeepPageMetaAndFilterCompleted() {
        Date now = new Date();
        TaskConfig t1 = config(21L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig t2 = config(22L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig t3 = config(23L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));

        Page<TaskConfig> cfgPage = new Page<>(2, 3);
        cfgPage.setTotal(20);
        cfgPage.setRecords(List.of(t1, t2, t3));

        when(taskConfigService.selectPage(any(Page.class))).thenReturn(cfgPage);
        when(userTaskInstanceService.selectByUserId(102L)).thenReturn(List.of(instance(22L, 1), instance(23L, 0)));

        Page<TaskView> out = service.listAvailableTasksPage(new Page<>(2, 3), 102L, "completed");

        assertEquals(2, out.getCurrent());
        assertEquals(3, out.getSize());
        assertEquals(20, out.getTotal());
        assertEquals(1, out.getRecords().size());
        assertEquals(22L, out.getRecords().get(0).getTaskConfig().getId());
        assertTrue(out.getRecords().get(0).getUserAccepted());
    }

    @Test
    void listAvailableTasks_shouldHandleNullUserInstancesAndUnknownState() {
        Date now = new Date();
        TaskConfig ongoing = config(31L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig disabled = config(32L, 0, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));

        when(taskConfigService.listAll()).thenReturn(List.of(ongoing, disabled));
        when(userTaskInstanceService.selectByUserId(103L)).thenReturn(null);

        List<TaskView> out = service.listAvailableTasks(103L, "unknown-state");

        assertEquals(2, out.size());
        Map<Long, TaskView> byId = out.stream().collect(Collectors.toMap(v -> v.getTaskConfig().getId(), Function.identity()));
        assertFalse(byId.get(31L).getUserAccepted());
        assertTrue(byId.get(31L).getCanAccept());
        assertFalse(byId.get(32L).getCanAccept());
        assertEquals("任务未启用", byId.get(32L).getReason());
    }

    @Test
    void listAvailableTasksPage_shouldFilterExpiredAndSetLimitedStockReason() {
        Date now = new Date();
        TaskConfig expiredLimited = config(41L, 1, "LIMITED", shiftMinutes(now, -30), shiftMinutes(now, -1));
        TaskConfig ongoingLimited = config(42L, 1, "LIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));

        Page<TaskConfig> cfgPage = new Page<>(1, 10);
        cfgPage.setTotal(2);
        cfgPage.setRecords(List.of(expiredLimited, ongoingLimited));

        when(taskConfigService.selectPage(any(Page.class))).thenReturn(cfgPage);
        when(userTaskInstanceService.selectByUserId(104L)).thenReturn(List.of());
        when(taskStockService.getByIdAndStageIndex(41L, 1)).thenReturn(stock(41L, 0));
        when(taskStockService.getByIdAndStageIndex(42L, 1)).thenReturn(stock(42L, 2));

        Page<TaskView> out = service.listAvailableTasksPage(new Page<>(1, 10), 104L, "expired");

        assertEquals(2, out.getTotal());
        assertEquals(1, out.getRecords().size());
        TaskView only = out.getRecords().get(0);
        assertEquals(41L, only.getTaskConfig().getId());
        assertFalse(only.getCanAccept());
        assertEquals("库存不足", only.getReason());
        assertEquals(0, only.getRemainingStock());
    }

    @Test
    void listAvailableTasksPage_shouldCoverOngoingAndUnknownStateBranches() {
        Date now = new Date();
        TaskConfig disabled = config(51L, null, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig notStarted = config(52L, 1, "UNLIMITED", shiftMinutes(now, 10), shiftMinutes(now, 20));
        TaskConfig ended = config(53L, 1, "UNLIMITED", shiftMinutes(now, -20), shiftMinutes(now, -1));
        TaskConfig limitedNoStock = config(54L, 1, "LIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig accepted = config(55L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig ongoing = config(56L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));

        Page<TaskConfig> cfgPage = new Page<>(1, 10);
        cfgPage.setTotal(6);
        cfgPage.setRecords(List.of(disabled, notStarted, ended, limitedNoStock, accepted, ongoing));

        when(taskConfigService.selectPage(any(Page.class))).thenReturn(cfgPage);
        when(userTaskInstanceService.selectByUserId(105L)).thenReturn(List.of(instance(55L, 1), instance(999L, null)));
        when(taskStockService.getByIdAndStageIndex(54L, 1)).thenReturn(null);

        Page<TaskView> unknown = service.listAvailableTasksPage(new Page<>(1, 10), 105L, "unknown");
        assertEquals(6, unknown.getRecords().size());

        Page<TaskView> ongoingOut = service.listAvailableTasksPage(new Page<>(1, 10), 105L, "ongoing");
        assertEquals(1, ongoingOut.getRecords().size());
        assertEquals(56L, ongoingOut.getRecords().get(0).getTaskConfig().getId());
        assertTrue(ongoingOut.getRecords().get(0).getCanAccept());
    }

    @Test
    void listAvailableTasks_shouldTrimStateAndHandleLowercaseLimitedType() {
        Date now = new Date();
        TaskConfig limitedLowercase = config(61L, 1, "limited", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig accepted = config(62L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));

        when(taskConfigService.listAll()).thenReturn(List.of(limitedLowercase, accepted));
        when(userTaskInstanceService.selectByUserId(106L)).thenReturn(List.of(instance(62L, 1)));
        when(taskStockService.getByIdAndStageIndex(61L, 1)).thenReturn(stock(61L, 9));

        List<TaskView> out = service.listAvailableTasks(106L, "  ongoing  ");

        assertEquals(1, out.size());
        assertEquals(61L, out.get(0).getTaskConfig().getId());
        assertEquals(9, out.get(0).getRemainingStock());
        assertTrue(out.get(0).getCanAccept());
    }

    @Test
    void listAvailableTasksPage_shouldHandleNullUserInstances() {
        Date now = new Date();
        TaskConfig t1 = config(71L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));

        Page<TaskConfig> cfgPage = new Page<>(1, 5);
        cfgPage.setTotal(1);
        cfgPage.setRecords(List.of(t1));
        when(taskConfigService.selectPage(any(Page.class))).thenReturn(cfgPage);
        when(userTaskInstanceService.selectByUserId(107L)).thenReturn(null);

        Page<TaskView> out = service.listAvailableTasksPage(new Page<>(1, 5), 107L, "completed");

        assertEquals(1, out.getTotal());
        assertTrue(out.getRecords().isEmpty());
    }

    @Test
    void listAvailableTasks_shouldNotFilterWhenStateIsEmptyString() {
        Date now = new Date();
        TaskConfig t1 = config(81L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig t2 = config(82L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));

        when(taskConfigService.listAll()).thenReturn(List.of(t1, t2));
        when(userTaskInstanceService.selectByUserId(108L)).thenReturn(List.of(instance(82L, 1)));

        List<TaskView> out = service.listAvailableTasks(108L, "");

        assertEquals(2, out.size());
    }

    @Test
    void listAvailableTasksPage_shouldNotFilterWhenStateIsNull() {
        Date now = new Date();
        TaskConfig t1 = config(91L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));
        TaskConfig t2 = config(92L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 10));

        Page<TaskConfig> cfgPage = new Page<>(1, 10);
        cfgPage.setTotal(2);
        cfgPage.setRecords(List.of(t1, t2));

        when(taskConfigService.selectPage(any(Page.class))).thenReturn(cfgPage);
        when(userTaskInstanceService.selectByUserId(109L)).thenReturn(List.of(instance(92L, 1)));

        Page<TaskView> out = service.listAvailableTasksPage(new Page<>(1, 10), 109L, null);

        assertEquals(2, out.getRecords().size());
    }

    @Test
    void listAvailableTasks_shouldCoverExpiredPredicateFalseBranches_andBlankTrimState() {
        Date now = new Date();
        TaskConfig nullEnd = config(101L, 1, "UNLIMITED", shiftMinutes(now, -10), null);
        TaskConfig futureEnd = config(102L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 30));
        TaskConfig expired = config(103L, 1, "UNLIMITED", shiftMinutes(now, -30), shiftMinutes(now, -1));

        when(taskConfigService.listAll()).thenReturn(List.of(nullEnd, futureEnd, expired));
        when(userTaskInstanceService.selectByUserId(110L)).thenReturn(null);

        List<TaskView> blankTrim = service.listAvailableTasks(110L, "   ");
        assertEquals(3, blankTrim.size());

        List<TaskView> expiredOnly = service.listAvailableTasks(110L, "expired");
        assertEquals(1, expiredOnly.size());
        assertEquals(103L, expiredOnly.get(0).getTaskConfig().getId());
    }

    @Test
    void listAvailableTasksPage_shouldCoverExpiredPredicateFalseBranches() {
        Date now = new Date();
        TaskConfig nullEnd = config(111L, 1, "UNLIMITED", shiftMinutes(now, -10), null);
        TaskConfig futureEnd = config(112L, 1, "UNLIMITED", shiftMinutes(now, -10), shiftMinutes(now, 20));
        TaskConfig expired = config(113L, 1, "UNLIMITED", shiftMinutes(now, -20), shiftMinutes(now, -1));

        Page<TaskConfig> cfgPage = new Page<>(1, 10);
        cfgPage.setTotal(3);
        cfgPage.setRecords(List.of(nullEnd, futureEnd, expired));
        when(taskConfigService.selectPage(any(Page.class))).thenReturn(cfgPage);
        when(userTaskInstanceService.selectByUserId(111L)).thenReturn(null);

        Page<TaskView> expiredOnly = service.listAvailableTasksPage(new Page<>(1, 10), 111L, "expired");
        assertEquals(1, expiredOnly.getRecords().size());
        assertEquals(113L, expiredOnly.getRecords().get(0).getTaskConfig().getId());
    }

    private static TaskConfig config(Long id, Integer status, String taskType, Date start, Date end) {
        TaskConfig cfg = new TaskConfig();
        cfg.setId(id);
        cfg.setStatus(status);
        cfg.setTaskType(taskType);
        cfg.setStartTime(start);
        cfg.setEndTime(end);
        return cfg;
    }

    private static UserTaskInstance instance(Long taskId, Integer status) {
        UserTaskInstance i = new UserTaskInstance();
        i.setTaskId(taskId);
        i.setStatus(status);
        return i;
    }

    private static TaskStock stock(Long taskId, Integer available) {
        TaskStock s = new TaskStock();
        s.setTaskId(taskId);
        s.setStageIndex(1);
        s.setAvailableStock(available);
        return s;
    }

    private static Date shiftMinutes(Date base, int deltaMinutes) {
        return new Date(base.getTime() + deltaMinutes * 60_000L);
    }
}

