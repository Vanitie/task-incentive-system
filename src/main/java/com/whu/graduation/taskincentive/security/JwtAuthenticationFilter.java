package com.whu.graduation.taskincentive.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        try {
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                Claims claims = jwtUtil.parseToken(token);
                if (!jwtUtil.isTokenExpired(claims)) {
                    String username = claims.getSubject();
                    Object rolesObj = claims.get("roles");
                    Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    String rolesStr = null;
                    if (rolesObj instanceof String) {
                        rolesStr = (String) rolesObj;
                        if (StringUtils.hasText(rolesStr)) {
                            authorities = Arrays.stream(rolesStr.split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                                    .map(SimpleGrantedAuthority::new)
                                    .collect(Collectors.toList());
                        }
                    }
                    boolean isAdmin = Boolean.TRUE.equals(claims.get("admin", Boolean.class));
                    if (isAdmin && authorities.isEmpty()) {
                        authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"));
                        rolesStr = "ROLE_ADMIN";
                    }

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            authorities
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    // set user context for propagation
                    UserContext.set(username, rolesStr);
                }
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // token 无效或过期，清空上下文
            SecurityContextHolder.clearContext();
            UserContext.clear();
            filterChain.doFilter(request, response);
        } finally {
            // 确保每次请求结束后都清理上下文，防止内存泄漏
            UserContext.clear();
        }
    }
}
