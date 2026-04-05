package com.whu.graduation.taskincentive.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisPlusConfigTest {

    @Test
    void mybatisPlusInterceptor_shouldContainPaginationInnerInterceptor() {
        MybatisPlusConfig config = new MybatisPlusConfig();

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor();

        assertNotNull(interceptor);
        assertEquals(1, interceptor.getInterceptors().size());
        assertTrue(interceptor.getInterceptors().get(0) instanceof PaginationInnerInterceptor);
    }
}

