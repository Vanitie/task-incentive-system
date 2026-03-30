package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.security.JwtUtil;
import com.whu.graduation.taskincentive.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Value("${app.security.admin.username}")
    private String adminUser;

    @Value("${app.security.admin.password}")
    private String adminPass;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req) {
        if (adminUser.equals(req.getUsername()) && adminPass.equals(req.getPassword())) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("admin", true);
            claims.put("roles", List.of("ROLE_ADMIN"));
            String token = jwtUtil.generateToken(req.getUsername(), claims);
            return ApiResponse.success(new LoginResponse(token));
        }

        User u = userService.authenticate(req.getUsername(), req.getPassword());
        if (u != null) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", normalizeRoles(u.getRoles()));
            String token = jwtUtil.generateToken(u.getUsername(), claims);
            return ApiResponse.success(new LoginResponse(token));
        }
        return ApiResponse.error(401, "invalid credentials");
    }

    @PostMapping("/register")
    public ApiResponse<String> register(@RequestBody RegisterRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            return ApiResponse.error(400, "username and password required");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        // 注册用户默认初始化为普通用户，避免前端传参提权。
        user.setPointBalance(0);
        boolean ok = userService.register(user, req.getPassword(), "ROLE_USER");
        if (ok) return ApiResponse.success("registered");
        return ApiResponse.error(409, "user exists");
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String,Object>> me(Authentication authentication,
                                              @RequestHeader(value = "Authorization", required = false) String header) {
        if (authentication == null || authentication.getName() == null) {
            return ApiResponse.error(401, "no token");
        }

        String username = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Map<String, Object> map = new HashMap<>();
        map.put("username", username);
        map.put("roles", roles);
        map.put("isAdmin", roles.contains("ROLE_ADMIN"));

        User u = userService.selectByUsername(username);
        if (u != null) {
            map.put("userId", u.getId());
            map.put("pointBalance", u.getPointBalance());
        }

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);
                map.put("exp", claims.getExpiration());
            } catch (Exception ignored) {
                // Token is already authenticated by filter; expiration hint is best-effort only.
            }
        }

        return ApiResponse.success(map);
    }

    private List<String> normalizeRoles(String rawRoles) {
        if (rawRoles == null || rawRoles.trim().isEmpty()) {
            return List.of("ROLE_USER");
        }
        Set<String> set = new LinkedHashSet<>();
        Arrays.stream(rawRoles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(r -> set.add(r.startsWith("ROLE_") ? r : "ROLE_" + r));
        if (set.isEmpty()) {
            set.add("ROLE_USER");
        }
        return new ArrayList<>(set);
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class LoginResponse {
        private final String token;
    }

    @Data
    public static class RegisterRequest {
        private String username;
        private String password;
    }
}
