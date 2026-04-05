package com.whu.graduation.taskincentive.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_shouldSkipAuth_whenHeaderMissing() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(req, resp);
    }

    @Test
    void doFilterInternal_shouldSkipAuth_whenHeaderNotBearer() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(req, resp);
    }

    @Test
    void doFilterInternal_shouldParseStringRoles_andAddRolePrefix() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("t-1")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("u1");
        when(claims.get("roles")).thenReturn("ADMIN, ROLE_USER,  ");

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer t-1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("u1", auth.getPrincipal());
        List<String> roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList());
        assertTrue(roles.contains("ROLE_ADMIN"));
        assertTrue(roles.contains("ROLE_USER"));
        verify(chain, times(1)).doFilter(req, resp);
    }

    @Test
    void doFilterInternal_shouldParseListRoles_andIgnoreNullElement() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("t-2")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("u2");
        when(claims.get("roles")).thenReturn(Arrays.asList("ADMIN", "ROLE_AUDITOR", null));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer t-2");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        List<String> roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList());
        assertEquals(2, roles.size());
        assertTrue(roles.contains("ROLE_ADMIN"));
        assertTrue(roles.contains("ROLE_AUDITOR"));
    }

    @Test
    void doFilterInternal_shouldSetEmptyAuthorities_whenRolesAbsent() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("t-3")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("u3");
        when(claims.get("roles")).thenReturn(null);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer t-3");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(0, auth.getAuthorities().size());
    }

    @Test
    void doFilterInternal_shouldClearContext_whenJwtInvalid() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.parseToken(anyString())).thenThrow(new RuntimeException("bad token"));

        SecurityContextHolder.getContext().setAuthentication(mock(Authentication.class));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(req, resp);
    }
}
