package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import com.whu.graduation.taskincentive.service.UserActionLogService;
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

@WebMvcTest(UserActionLogController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserActionLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserActionLogService actionLogService;

    @Test
    public void create_shouldReturnOk() throws Exception {
        when(actionLogService.save(org.mockito.Mockito.any(UserActionLog.class))).thenReturn(true);
        String body = "{\"userId\":1,\"actionType\":\"USER_LEARN\"}";
        mockMvc.perform(post("/api/user-action-log/create").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    public void listByUser_shouldReturnOk() throws Exception {
        when(actionLogService.selectByUserIdPage(org.mockito.Mockito.any(), org.mockito.Mockito.anyLong())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserActionLog>());
        mockMvc.perform(get("/api/user-action-log/list/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
