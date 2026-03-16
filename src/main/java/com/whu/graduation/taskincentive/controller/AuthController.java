package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.security.JwtUtil;
import com.whu.graduation.taskincentive.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
            claims.put("roles", "ROLE_ADMIN");
            String token = jwtUtil.generateToken(req.getUsername(), claims);
            return ApiResponse.success(new LoginResponse(token));
        }

        User u = userService.authenticate(req.getUsername(), req.getPassword());
        if (u != null) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", u.getRoles());
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
        user.setPointBalance(0);
        String roles = req.getRoles() == null ? "ROLE_USER" : req.getRoles();
        boolean ok = userService.register(user, req.getPassword(), roles);
        if (ok) return ApiResponse.success("registered");
        return ApiResponse.error(409, "user exists");
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String,Object>> me(@RequestHeader("Authorization") String header) {
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = jwtUtil.parseToken(token);
            Map<String, Object> map = new HashMap<>();
            map.put("sub", claims.getSubject());
            map.put("exp", claims.getExpiration());
            map.put("roles", claims.get("roles"));
            return ApiResponse.success(map);
        }
        return ApiResponse.error(401, "no token");
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
        private String roles;
    }
}
