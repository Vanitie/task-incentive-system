package com.whu.graduation.taskincentive.security;

import com.whu.graduation.taskincentive.config.AppProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilTest {

    @Test
    void generateAndParseToken_shouldWorkWithConfiguredSecret() {
        JwtUtil jwtUtil = new JwtUtil(buildProperties("0123456789abcdef0123456789abcdef", 60_000L));
        jwtUtil.init();

        String token = jwtUtil.generateToken("alice", Map.of("role", "ADMIN"));
        Claims claims = jwtUtil.parseToken(token);

        assertNotNull(token);
        assertEquals("alice", claims.getSubject());
        assertEquals("ADMIN", claims.get("role", String.class));
        assertFalse(jwtUtil.isTokenExpired(claims));
    }

    @Test
    void init_shouldFallbackToGeneratedKeyWhenSecretMissing() {
        JwtUtil jwtUtil = new JwtUtil(buildProperties(null, 30_000L));
        jwtUtil.init();

        String token = jwtUtil.generateToken("bob", Map.of());
        Claims claims = jwtUtil.parseToken(token);

        assertNotNull(token);
        assertEquals("bob", claims.getSubject());
    }

    @Test
    void isTokenExpired_shouldReturnTrueWhenExpirationBeforeNow() {
        JwtUtil jwtUtil = new JwtUtil(buildProperties("0123456789abcdef0123456789abcdef", 60_000L));
        jwtUtil.init();

        Claims expiredClaims = mock(Claims.class);
        when(expiredClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() - 1000));

        assertTrue(jwtUtil.isTokenExpired(expiredClaims));
    }

    private AppProperties buildProperties(String secret, long expirationMs) {
        AppProperties appProperties = new AppProperties();
        AppProperties.Security security = new AppProperties.Security();
        AppProperties.Security.Jwt jwt = new AppProperties.Security.Jwt();
        jwt.setSecret(secret);
        jwt.setExpirationMs(expirationMs);
        security.setJwt(jwt);
        appProperties.setSecurity(security);
        return appProperties;
    }
}

