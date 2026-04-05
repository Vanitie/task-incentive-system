package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskConfigControllerUnitTest {

    @Test
    void listAll_shouldUseStatusQuery_whenStatusProvided() {
        TaskConfigService service = mock(TaskConfigService.class);
        TaskConfigController controller = new TaskConfigController();
        ReflectionTestUtils.setFieldRecursively(controller, "taskConfigService", service);

        Page<TaskConfig> p = new Page<>(1, 10);
        p.setRecords(List.of(new TaskConfig()));
        p.setTotal(1);
        when(service.selectByStatusPage(any(Page.class), org.mockito.ArgumentMatchers.eq(1))).thenReturn(p);

        ApiResponse<PageResult<TaskConfig>> out = controller.listAll(1, 10, 1);

        assertEquals(0, out.getCode());
        assertEquals(1, out.getData().getItems().size());
        verify(service).selectByStatusPage(any(Page.class), org.mockito.ArgumentMatchers.eq(1));
    }

    @Test
    void listAll_shouldUseDefaultPageQuery_whenStatusMissing() {
        TaskConfigService service = mock(TaskConfigService.class);
        TaskConfigController controller = new TaskConfigController();
        ReflectionTestUtils.setFieldRecursively(controller, "taskConfigService", service);

        Page<TaskConfig> p = new Page<>(2, 5);
        p.setTotal(0);
        when(service.selectPage(any(Page.class))).thenReturn(p);

        ApiResponse<PageResult<TaskConfig>> out = controller.listAll(2, 5, null);

        assertEquals(0, out.getCode());
        assertEquals(2, out.getData().getPage());
        verify(service).selectPage(any(Page.class));
    }

    @Test
    void search_shouldMapEndTimeToEndTimeColumn_andApplyAscOrder() {
        TaskConfigService service = mock(TaskConfigService.class);
        TaskConfigController controller = new TaskConfigController();
        ReflectionTestUtils.setFieldRecursively(controller, "taskConfigService", service);

        when(service.searchByConditions(any(), any(), any(), any(), any(Page.class))).thenReturn(new Page<>(1, 20));

        controller.search("n", "t", 1, "POINT", 1, 20, "endTime", true);

        ArgumentCaptor<Page<TaskConfig>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(service).searchByConditions(org.mockito.ArgumentMatchers.eq("n"), org.mockito.ArgumentMatchers.eq("t"),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq("POINT"), pageCaptor.capture());

        List<OrderItem> orders = pageCaptor.getValue().orders();
        assertEquals(1, orders.size());
        assertEquals("end_time", orders.get(0).getColumn());
        assertTrue(orders.get(0).isAsc());
    }

    @Test
    void search_shouldUseRawOrderColumn_andDescByDefault() {
        TaskConfigService service = mock(TaskConfigService.class);
        TaskConfigController controller = new TaskConfigController();
        ReflectionTestUtils.setFieldRecursively(controller, "taskConfigService", service);

        when(service.searchByConditions(any(), any(), any(), any(), any(Page.class))).thenReturn(new Page<>(1, 20));

        controller.search(null, null, null, null, 1, 20, "task_id", false);

        ArgumentCaptor<Page<TaskConfig>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(service).searchByConditions(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), pageCaptor.capture());

        List<OrderItem> orders = pageCaptor.getValue().orders();
        assertEquals("task_id", orders.get(0).getColumn());
        assertFalse(orders.get(0).isAsc());
    }
}

