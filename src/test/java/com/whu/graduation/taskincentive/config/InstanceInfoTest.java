package com.whu.graduation.taskincentive.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InstanceInfoTest {

    @Test
    void init_shouldGenerateInstanceId() {
        InstanceInfo info = new InstanceInfo();

        info.init();

        assertNotNull(info.getInstanceId());
        assertFalse(info.getInstanceId().isEmpty());
    }
}

