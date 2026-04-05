package com.whu.graduation.taskincentive.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void setGetAndClear_shouldWork() {
        UserContext.set("alice", "ROLE_USER");

        assertEquals("alice", UserContext.getUsername().orElseThrow());
        assertEquals("ROLE_USER", UserContext.getRoles().orElseThrow());

        UserContext.clear();

        assertTrue(UserContext.getUsername().isEmpty());
        assertTrue(UserContext.getRoles().isEmpty());
    }
}

