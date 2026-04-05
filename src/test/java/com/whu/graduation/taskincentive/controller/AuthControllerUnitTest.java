package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.security.JwtUtil;
import com.whu.graduation.taskincentive.service.UserService;
import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerUnitTest {

    @Test
    void login_shouldUseNormalizedRoles_whenUserAuthenticated() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserService userService = mock(UserService.class);
        AuthController controller = new AuthController(jwtUtil, userService);
        ReflectionTestUtils.setFieldRecursively(controller, "adminUser", "admin");
        ReflectionTestUtils.setFieldRecursively(controller, "adminPass", "admin");

        User user = new User();
        user.setUsername("u1");
        user.setRoles("ADMIN,ROLE_USER, ,ADMIN");
        when(userService.authenticate("u1", "p")).thenReturn(user);
        when(jwtUtil.generateToken(eq("u1"), anyMap())).thenReturn("token-u1");

        AuthController.LoginRequest req = new AuthController.LoginRequest();
        req.setUsername("u1");
        req.setPassword("p");

        ApiResponse<AuthController.LoginResponse> out = controller.login(req);

        assertEquals(0, out.getCode());
        assertEquals("token-u1", out.getData().getToken());

        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jwtUtil).generateToken(eq("u1"), claimsCaptor.capture());
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claimsCaptor.getValue().get("roles");
        assertEquals(2, roles.size());
        assertTrue(roles.contains("ROLE_ADMIN"));
        assertTrue(roles.contains("ROLE_USER"));
    }

    @Test
    void register_shouldReturnConflict_whenUserExists() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserService userService = mock(UserService.class);
        AuthController controller = new AuthController(jwtUtil, userService);

        AuthController.RegisterRequest req = new AuthController.RegisterRequest();
        req.setUsername("u2");
        req.setPassword("p2");
        when(userService.register(org.mockito.ArgumentMatchers.any(User.class), eq("p2"), eq("ROLE_USER"))).thenReturn(false);

        ApiResponse<String> out = controller.register(req);

        assertEquals(409, out.getCode());
        assertEquals("user exists", out.getMsg());
    }

    @Test
    void register_shouldReturnBadRequest_whenUsernameOrPasswordMissing() {
        AuthController controller = new AuthController(mock(JwtUtil.class), mock(UserService.class));
        AuthController.RegisterRequest req = new AuthController.RegisterRequest();
        req.setUsername("u4");

        ApiResponse<String> out = controller.register(req);

        assertEquals(400, out.getCode());
        assertEquals("username and password required", out.getMsg());
    }

    @Test
    void login_shouldReturnUnauthorized_whenCredentialInvalid() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserService userService = mock(UserService.class);
        AuthController controller = new AuthController(jwtUtil, userService);
        ReflectionTestUtils.setFieldRecursively(controller, "adminUser", "admin");
        ReflectionTestUtils.setFieldRecursively(controller, "adminPass", "admin");
        when(userService.authenticate("x", "y")).thenReturn(null);

        AuthController.LoginRequest req = new AuthController.LoginRequest();
        req.setUsername("x");
        req.setPassword("y");

        ApiResponse<AuthController.LoginResponse> out = controller.login(req);

        assertEquals(401, out.getCode());
        assertEquals("invalid credentials", out.getMsg());
    }

    @Test
    void me_shouldReturnNoToken_whenAuthenticationMissing() {
        AuthController controller = new AuthController(mock(JwtUtil.class), mock(UserService.class));

        ApiResponse<Map<String, Object>> out = controller.me(null, null);

        assertEquals(401, out.getCode());
        assertEquals("no token", out.getMsg());
    }

    @Test
    void me_shouldIgnoreTokenParseError_andStillReturnProfile() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserService userService = mock(UserService.class);
        AuthController controller = new AuthController(jwtUtil, userService);

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("u3");
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_USER"))).when(auth).getAuthorities();

        User user = new User();
        user.setId(3L);
        user.setUsername("u3");
        user.setPointBalance(30);
        when(userService.selectByUsername("u3")).thenReturn(user);
        when(jwtUtil.parseToken("bad-token")).thenThrow(new RuntimeException("bad"));

        ApiResponse<Map<String, Object>> out = controller.me(auth, "Bearer bad-token");

        assertEquals(0, out.getCode());
        assertEquals("u3", out.getData().get("username"));
        assertEquals(3L, out.getData().get("userId"));
        assertFalse(out.getData().containsKey("exp"));
        assertNotNull(out.getData().get("roles"));
    }

    @Test
    void me_shouldAttachExp_whenBearerTokenIsParsable() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserService userService = mock(UserService.class);
        AuthController controller = new AuthController(jwtUtil, userService);

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin");
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(auth).getAuthorities();

        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPointBalance(99);
        when(userService.selectByUsername("admin")).thenReturn(user);

        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        Date exp = new Date(System.currentTimeMillis() + 60000);
        when(claims.getExpiration()).thenReturn(exp);
        when(jwtUtil.parseToken("good-token")).thenReturn(claims);

        ApiResponse<Map<String, Object>> out = controller.me(auth, "Bearer good-token");

        assertEquals(0, out.getCode());
        assertEquals(exp, out.getData().get("exp"));
        assertEquals(true, out.getData().get("isAdmin"));
    }

    @Test
    void normalizeRoles_shouldFallbackAndNormalize_whenInputEdgeCases() throws Exception {
        AuthController controller = new AuthController(mock(JwtUtil.class), mock(UserService.class));
        Method normalize = AuthController.class.getDeclaredMethod("normalizeRoles", String.class);
        normalize.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> nullRoles = (List<String>) normalize.invoke(controller, new Object[]{null});
        assertEquals(List.of("ROLE_USER"), nullRoles);

        @SuppressWarnings("unchecked")
        List<String> blankRoles = (List<String>) normalize.invoke(controller, " ,  , ");
        assertEquals(List.of("ROLE_USER"), blankRoles);

        @SuppressWarnings("unchecked")
        List<String> mixedRoles = (List<String>) normalize.invoke(controller, "ADMIN, ROLE_USER,ADMIN");
        assertEquals(2, mixedRoles.size());
        assertTrue(mixedRoles.contains("ROLE_ADMIN"));
        assertTrue(mixedRoles.contains("ROLE_USER"));
    }
}




