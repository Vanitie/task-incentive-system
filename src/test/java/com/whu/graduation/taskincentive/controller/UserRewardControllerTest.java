package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserRewardController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserRewardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRewardRecordService recordService;

    @Test
    public void listByUser_shouldReturnOk() throws Exception {
        when(recordService.selectByUserIdPage(any(), anyLong(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserRewardRecord>());
        mockMvc.perform(get("/api/user-reward/list/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void unclaimed_shouldReturnOk() throws Exception {
        when(recordService.selectUnclaimedPhysicalReward(anyLong())).thenReturn(java.util.Collections.emptyList());
        mockMvc.perform(get("/api/user-reward/unclaimed/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void listByConditions_shouldReturnPagedResult() throws Exception {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserRewardRecord> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(2, 5);
        UserRewardRecord item = new UserRewardRecord();
        item.setUserId(1001L);
        item.setTaskId(10L);
        item.setRewardType("POINT");
        page.setRecords(List.of(item));
        page.setTotal(1);

        when(recordService.listByConditions(any(), eq(1001L), eq(10L), eq("POINT"), eq(0))).thenReturn(page);

        mockMvc.perform(get("/api/user-reward/list")
                        .param("userId", "1001")
                        .param("taskId", "10")
                        .param("rewardType", "POINT")
                        .param("status", "0")
                        .param("page", "2")
                        .param("size", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5));
    }

    @Test
    public void listByConditions_shouldUseDefaultPaging_whenNoPageSizeProvided() throws Exception {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserRewardRecord> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(recordService.listByConditions(any(), eq(null), eq(null), eq(null), eq(null))).thenReturn(page);

        mockMvc.perform(get("/api/user-reward/list").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    public void countTodayReceivers_shouldReturnChartWithPercent() throws Exception {
        when(recordService.getReceivedUsersLast7Days()).thenReturn(List.of(0L, 5L));

        mockMvc.perform(get("/api/user-reward/count/today-receivers").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.value").value(5))
                .andExpect(jsonPath("$.data.percent").value("+100%"));
    }

    @Test
    public void reconcileSummary_shouldReturnOk() throws Exception {
        when(recordService.reconcileSummary(anyInt())).thenReturn(Map.of("mismatch", 0, "sample", List.of()));

        mockMvc.perform(get("/api/user-reward/reconcile/summary").param("sampleLimit", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(recordService).reconcileSummary(15);
    }

    @Test
    public void previewPointReplay_shouldReturnOk() throws Exception {
        when(recordService.previewPointReplayDiff(anyInt())).thenReturn(Map.of("diffCount", 2));

        mockMvc.perform(get("/api/user-reward/replay/points/preview").param("sampleLimit", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(recordService).previewPointReplayDiff(8);
    }

    @Test
    public void executePointReplay_shouldReturnOk() throws Exception {
        when(recordService.executePointReplayCompensation()).thenReturn(Map.of("updatedUsers", 3));

        mockMvc.perform(post("/api/user-reward/replay/points/execute").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.updatedUsers").value(3));
    }

    @Test
    public void create_shouldReturnOk() throws Exception {
        when(recordService.save(any(UserRewardRecord.class))).thenReturn(true);

        String body = "{\"userId\":1001,\"taskId\":10,\"rewardType\":\"POINT\",\"rewardValue\":10}";
        mockMvc.perform(post("/api/user-reward/create").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void computePercent_shouldCoverAllBranches() throws Exception {
        UserRewardController plain = new UserRewardController();
        ReflectionTestUtils.setFieldRecursively(plain, "recordService", recordService);
        Method method = UserRewardController.class.getDeclaredMethod("computePercent", List.class);
        method.setAccessible(true);

        org.junit.jupiter.api.Assertions.assertEquals("+0%", method.invoke(plain, new Object[]{null}));
        org.junit.jupiter.api.Assertions.assertEquals("+0%", method.invoke(plain, List.of(9L)));
        org.junit.jupiter.api.Assertions.assertEquals("+0%", method.invoke(plain, List.of(0L, 0L)));
        org.junit.jupiter.api.Assertions.assertEquals("+100%", method.invoke(plain, List.of(0L, 5L)));
        org.junit.jupiter.api.Assertions.assertEquals("+25%", method.invoke(plain, List.of(8L, 10L)));
        org.junit.jupiter.api.Assertions.assertEquals("-50%", method.invoke(plain, List.of(10L, 5L)));
    }
}
