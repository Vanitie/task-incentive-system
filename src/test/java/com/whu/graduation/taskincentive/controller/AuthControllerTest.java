package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.security.JwtUtil;
import com.whu.graduation.taskincentive.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.whu.graduation.taskincentive.config.SecurityConfig;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    public void unit_me_shouldReturn401_whenAuthenticationNull() {
        AuthController plain = new AuthController(jwtUtil, userService);
        ApiResponse<Map<String, Object>> out = plain.me(null, null);
        assertEquals(401, out.getCode());
        assertEquals("no token", out.getMsg());
    }

    @Test
    public void unit_me_shouldReturn401_whenAuthenticationNameNull() {
        AuthController plain = new AuthController(jwtUtil, userService);
        org.springframework.security.core.Authentication auth = Mockito.mock(org.springframework.security.core.Authentication.class);
        when(auth.getName()).thenReturn(null);
        ApiResponse<Map<String, Object>> out = plain.me(auth, null);
        assertEquals(401, out.getCode());
    }

    @Test
    public void unit_me_shouldSucceed_whenHeaderNull_andUserMissing() {
        AuthController plain = new AuthController(jwtUtil, userService);
        org.springframework.security.core.Authentication auth =
                new UsernamePasswordAuthenticationToken("u1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(userService.selectByUsername("u1")).thenReturn(null);

        ApiResponse<Map<String, Object>> out = plain.me(auth, null);
        assertEquals(0, out.getCode());
        assertEquals("u1", out.getData().get("username"));
        assertEquals(false, out.getData().get("isAdmin"));
        assertFalse(out.getData().containsKey("userId"));
        assertFalse(out.getData().containsKey("exp"));
    }

    @Test
    public void unit_me_shouldIgnoreExpiration_whenHeaderNotBearer() {
        AuthController plain = new AuthController(jwtUtil, userService);
        org.springframework.security.core.Authentication auth =
                new UsernamePasswordAuthenticationToken("u1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(userService.selectByUsername("u1")).thenReturn(null);

        ApiResponse<Map<String, Object>> out = plain.me(auth, "Token abc");
        assertEquals(0, out.getCode());
        assertFalse(out.getData().containsKey("exp"));
    }

    @Test
    public void unit_me_shouldIgnoreExpiration_whenParseTokenThrows() {
        AuthController plain = new AuthController(jwtUtil, userService);
        org.springframework.security.core.Authentication auth =
                new UsernamePasswordAuthenticationToken("u1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(userService.selectByUsername("u1")).thenReturn(null);
        when(jwtUtil.parseToken("bad")).thenThrow(new RuntimeException("bad token"));

        ApiResponse<Map<String, Object>> out = plain.me(auth, "Bearer bad");
        assertEquals(0, out.getCode());
        assertFalse(out.getData().containsKey("exp"));
    }

    @Test
    public void unit_me_shouldExposeIsAdminTrue_whenRoleAdminPresent() {
        AuthController plain = new AuthController(jwtUtil, userService);
        org.springframework.security.core.Authentication auth =
                new UsernamePasswordAuthenticationToken("admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(userService.selectByUsername("admin")).thenReturn(null);

        ApiResponse<Map<String, Object>> out = plain.me(auth, null);
        assertEquals(0, out.getCode());
        assertEquals(true, out.getData().get("isAdmin"));
    }

    @Test
    public void unit_normalizeRoles_shouldCoverNullBlankAndCleanup() throws Exception {
        AuthController plain = new AuthController(jwtUtil, userService);
        Method method = AuthController.class.getDeclaredMethod("normalizeRoles", String.class);
        method.setAccessible(true);

        assertEquals(List.of("ROLE_USER"), method.invoke(plain, new Object[]{null}));
        assertEquals(List.of("ROLE_USER"), method.invoke(plain, "   "));

        // Empty tokens should fallback to ROLE_USER
        assertEquals(List.of("ROLE_USER"), method.invoke(plain, ", ,  ,"));

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) method.invoke(plain, "USER, ROLE_ADMIN,USER,");
        assertTrue(roles.contains("ROLE_USER"));
        assertTrue(roles.contains("ROLE_ADMIN"));
        // De-dup should ensure only 2 entries
        assertEquals(2, roles.size());
    }

    @Test
    public void unit_login_shouldReturnToken_whenUserAuthenticated() {
        AuthController plain = new AuthController(jwtUtil, userService);
        // inject @Value fields
        com.whu.graduation.taskincentive.testutil.ReflectionTestUtils.setFieldRecursively(plain, "adminUser", "admin");
        com.whu.graduation.taskincentive.testutil.ReflectionTestUtils.setFieldRecursively(plain, "adminPass", "admin");

        AuthController.LoginRequest req = new AuthController.LoginRequest();
        req.setUsername("u1");
        req.setPassword("p");

        User u = new User();
        u.setUsername("u1");
        u.setRoles("USER,ADMIN"); // triggers normalizeRoles prefix + de-dup
        when(userService.authenticate("u1", "p")).thenReturn(u);
        when(jwtUtil.generateToken(eq("u1"), any())).thenReturn("t-user");

        ApiResponse<AuthController.LoginResponse> out = plain.login(req);
        assertEquals(0, out.getCode());
        assertNotNull(out.getData());
        assertEquals("t-user", out.getData().getToken());
    }

    @Test
    public void unit_login_shouldReturn401_whenInvalidCredentials() {
        AuthController plain = new AuthController(jwtUtil, userService);
        com.whu.graduation.taskincentive.testutil.ReflectionTestUtils.setFieldRecursively(plain, "adminUser", "admin");
        com.whu.graduation.taskincentive.testutil.ReflectionTestUtils.setFieldRecursively(plain, "adminPass", "admin");

        AuthController.LoginRequest req = new AuthController.LoginRequest();
        req.setUsername("u1");
        req.setPassword("bad");
        when(userService.authenticate("u1", "bad")).thenReturn(null);

        ApiResponse<AuthController.LoginResponse> out = plain.login(req);
        assertEquals(401, out.getCode());
        assertEquals("invalid credentials", out.getMsg());
    }

    @Test
    public void unit_register_shouldReturn400_whenUsernameOrPasswordMissing() {
        AuthController plain = new AuthController(jwtUtil, userService);
        AuthController.RegisterRequest req = new AuthController.RegisterRequest();
        req.setUsername(null);
        req.setPassword("p");
        ApiResponse<String> out1 = plain.register(req);
        assertEquals(400, out1.getCode());

        AuthController.RegisterRequest req2 = new AuthController.RegisterRequest();
        req2.setUsername("u1");
        req2.setPassword(null);
        ApiResponse<String> out2 = plain.register(req2);
        assertEquals(400, out2.getCode());
    }

    @Test
    public void unit_register_shouldReturn409_whenUserExists() {
        AuthController plain = new AuthController(jwtUtil, userService);
        AuthController.RegisterRequest req = new AuthController.RegisterRequest();
        req.setUsername("u1");
        req.setPassword("p");
        when(userService.register(any(User.class), eq("p"), eq("ROLE_USER"))).thenReturn(false);

        ApiResponse<String> out = plain.register(req);
        assertEquals(409, out.getCode());
        assertEquals("user exists", out.getMsg());
    }
}
