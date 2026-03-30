package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.security.JwtUtil;
import com.whu.graduation.taskincentive.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.whu.graduation.taskincentive.config.SecurityConfig;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"app.security.admin.username=admin","app.security.admin.password=admin"})
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserService userService;

    @Test
    public void loginAsAdmin_shouldReturnToken() throws Exception {
        when(jwtUtil.generateToken(anyString(), org.mockito.Mockito.anyMap())).thenReturn("admintoken");

        String body = "{\"username\":\"admin\",\"password\":\"admin\"}";

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("admintoken"));
    }

    @Test
    public void register_shouldReturnOk() throws Exception {
        when(userService.register(org.mockito.Mockito.any(User.class), anyString(), anyString())).thenReturn(true);
        String body = "{\"username\":\"u1\",\"password\":\"p\",\"roles\":\"ROLE_ADMIN\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).register(any(User.class), eq("p"), eq("ROLE_USER"));
    }

    @Test
    public void me_shouldReturnCurrentUserInfo() throws Exception {
        User user = new User();
        user.setId(1001L);
        user.setUsername("u1");
        user.setPointBalance(88);
        when(userService.selectByUsername("u1")).thenReturn(user);

        Claims claims = org.mockito.Mockito.mock(Claims.class);
        Date exp = new Date(System.currentTimeMillis() + 3600_000L);
        when(jwtUtil.parseToken("t1")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("u1");
        when(claims.get("roles")).thenReturn(List.of("ROLE_USER"));
        when(claims.getExpiration()).thenReturn(exp);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("u1"))
                .andExpect(jsonPath("$.data.userId").value(1001L))
                .andExpect(jsonPath("$.data.pointBalance").value(88))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_USER"));
    }
}
