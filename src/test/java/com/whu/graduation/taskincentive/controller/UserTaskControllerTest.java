package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import com.whu.graduation.taskincentive.service.UserViewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserTaskController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserTaskInstanceService instanceService;

    @MockBean
    private UserViewService userViewService;

    @Test
    public void accept_shouldReturnOk() throws Exception {
        when(instanceService.acceptTask(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong())).thenReturn(new UserTaskInstance());
        mockMvc.perform(post("/api/user-task/accept?userId=1&taskId=2").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void available_shouldReturnOk() throws Exception {
        when(userViewService.listAvailableTasksPage(org.mockito.Mockito.any(), org.mockito.Mockito.anyLong(), org.mockito.Mockito.any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        mockMvc.perform(get("/api/user-task/available/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
