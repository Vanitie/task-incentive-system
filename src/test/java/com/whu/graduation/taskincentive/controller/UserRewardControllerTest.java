package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
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

@WebMvcTest(UserRewardController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserRewardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRewardRecordService recordService;

    @Test
    public void listByUser_shouldReturnOk() throws Exception {
        when(recordService.selectByUserIdPage(org.mockito.Mockito.any(), org.mockito.Mockito.anyLong(), org.mockito.Mockito.any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserRewardRecord>());
        mockMvc.perform(get("/api/user-reward/list/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void unclaimed_shouldReturnOk() throws Exception {
        when(recordService.selectUnclaimedPhysicalReward(org.mockito.Mockito.anyLong())).thenReturn(java.util.Collections.emptyList());
        mockMvc.perform(get("/api/user-reward/unclaimed/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
