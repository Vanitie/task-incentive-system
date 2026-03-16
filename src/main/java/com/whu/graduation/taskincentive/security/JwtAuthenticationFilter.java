package com.whu.graduation.taskincentive.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);
                String subject = claims.getSubject();
                Object rolesObj = claims.get("roles");
                Collection<GrantedAuthority> authorities = new ArrayList<>();
                if (rolesObj != null) {
                    if (rolesObj instanceof String) {
                        String rolesStr = (String) rolesObj;
                        String[] arr = rolesStr.split(",");
                        for (String r : arr) {
                            String role = r.trim();
                            if (!role.isEmpty()) {
                                // ensure prefix ROLE_
                                if (!role.startsWith("ROLE_")) role = "ROLE_" + role;
                                authorities.add(new SimpleGrantedAuthority(role));
                            }
                        }
                    } else if (rolesObj instanceof java.util.List) {
                        List<?> list = (List<?>) rolesObj;
                        for (Object o : list) {
                            if (o != null) {
                                String role = String.valueOf(o).trim();
                                if (!role.startsWith("ROLE_")) role = "ROLE_" + role;
                                authorities.add(new SimpleGrantedAuthority(role));
                            }
                        }
                    }
                }
                Authentication auth = new UsernamePasswordAuthenticationToken(subject, token, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                if (logger.isDebugEnabled()) {
                    logger.debug("JWT parsed successfully for subject={}, authorities={}", subject, authorities);
                }
            } catch (Exception ex) {
                // token invalid -> clear context and continue; endpoints will reject if required
                logger.warn("Invalid JWT token: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
