package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserBadge;
import com.whu.graduation.taskincentive.service.UserBadgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserBadgeController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserBadgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserBadgeService userBadgeService;

    @Test
    public void grant_shouldReturnOk() throws Exception {
        when(userBadgeService.grantBadge(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyInt())).thenReturn(true);
        mockMvc.perform(post("/api/user-badge/grant?userId=1&badgeCode=101").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void listByUser_shouldReturnPagedResult() throws Exception {
        Page<UserBadge> page = new Page<>(2, 5);
        page.setTotal(1);
        page.setRecords(Collections.singletonList(new UserBadge()));
        when(userBadgeService.selectByUserIdPage(any(Page.class), anyLong())).thenReturn(page);

        mockMvc.perform(get("/api/user-badge/list/1?page=2&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    public void create_shouldReturnOk() throws Exception {
        when(userBadgeService.save(any(UserBadge.class))).thenReturn(true);

        mockMvc.perform(post("/api/user-badge/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"badgeCode\":101}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }
}

