package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    @Value("${app.security.admin.username}")
    private String adminUser;

    @Value("${app.security.admin.password}")
    private String adminPass;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (adminUser.equals(req.getUsername()) && adminPass.equals(req.getPassword())) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("admin", true);
            String token = jwtUtil.generateToken(req.getUsername(), claims);
            return ResponseEntity.ok(new LoginResponse(token));
        }
        return ResponseEntity.status(401).body("invalid credentials");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String header) {
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = jwtUtil.parseToken(token);
            Map<String, Object> map = new HashMap<>();
            map.put("sub", claims.getSubject());
            map.put("exp", claims.getExpiration());
            return ResponseEntity.ok(map);
        }
        return ResponseEntity.status(401).body("no token");
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
}
