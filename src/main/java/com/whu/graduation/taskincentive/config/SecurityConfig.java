package com.whu.graduation.taskincentive.config;

import com.whu.graduation.taskincentive.security.JwtAuthenticationFilter;
import com.whu.graduation.taskincentive.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;

/**
 * JWT 安全配置：使用 JwtAuthenticationFilter 验证 Authorization: Bearer <token>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private Environment env;

    @Autowired
    private JwtUtil jwtUtil;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");

        http.csrf().disable();
        http.formLogin().disable();
        http.httpBasic().disable();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(HttpServletResponse.SC_FORBIDDEN))
        );

        // 注册 JWT 认证过滤器
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtUtil);
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        if (isDev) {
            // 开发环境：开放 swagger、auth 和 actuator
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                    .requestMatchers("/api/auth/me").authenticated()
                    .requestMatchers("/api/risk/**", "/api/task-config/**", "/api/task-stock/**", "/api/monitor/**").hasRole("ADMIN")
                    .requestMatchers("/api/engine/**", "/api/user-task/**", "/api/user-reward/**", "/api/user-badge/**", "/api/user-action-log/**", "/api/stats/**", "/api/benchmark/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().authenticated()
            );
        } else {
            // 非开发环境：禁止访问 swagger（denyAll），仅允许 auth 接口公开
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                    .requestMatchers("/api/auth/me").authenticated()
                    .requestMatchers("/api/risk/**", "/api/task-config/**", "/api/task-stock/**", "/api/monitor/**").hasRole("ADMIN")
                    .requestMatchers("/api/engine/**", "/api/user-task/**", "/api/user-reward/**", "/api/user-badge/**", "/api/user-action-log/**", "/api/stats/**", "/api/benchmark/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").denyAll()
                    .requestMatchers("/actuator/**").denyAll()
                    .anyRequest().authenticated()
            );
        }

        return http.build();
    }
}
