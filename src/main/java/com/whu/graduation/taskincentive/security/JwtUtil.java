package com.whu.graduation.taskincentive.security;

import com.whu.graduation.taskincentive.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private final AppProperties appProperties;
    private Key key;
    private long expirationMs;

    public JwtUtil(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        String secret = appProperties.getSecurity().getJwt().getSecret();
        this.expirationMs = appProperties.getSecurity().getJwt().getExpirationMs();
        if (secret != null && !secret.isEmpty()) {
            // use HS256 with provided secret
            byte[] bytes = secret.getBytes();
            this.key = new SecretKeySpec(bytes, SignatureAlgorithm.HS256.getJcaName());
        } else {
            // fallback to generated key (not ideal for multi-instance)
            this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        }
    }

    public String generateToken(String subject, Map<String, Object> claims) {
        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
