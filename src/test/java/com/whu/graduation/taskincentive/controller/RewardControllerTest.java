package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.service.RewardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RewardController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RewardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RewardService rewardService;

    @Test
    public void grant_shouldReturnOk() throws Exception {
        when(rewardService.grantReward(org.mockito.Mockito.anyLong(), org.mockito.Mockito.any(Reward.class))).thenReturn(true);
        String body = "{\"type\":\"POINT\",\"amount\":100}";
        mockMvc.perform(post("/api/reward/grant?userId=123").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
