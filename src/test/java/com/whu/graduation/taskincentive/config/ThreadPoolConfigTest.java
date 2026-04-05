package com.whu.graduation.taskincentive.config;

import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadPoolConfigTest {

    @Test
    void taskEngineEventExecutor_shouldUseNormalizedPoolSizeAndThreadPrefix() throws Exception {
        ThreadPoolConfig config = new ThreadPoolConfig();
        ReflectionTestUtils.setFieldRecursively(config, "corePoolSize", 2);
        ReflectionTestUtils.setFieldRecursively(config, "maxPoolSize", 1);
        ReflectionTestUtils.setFieldRecursively(config, "queueCapacity", 10);
        ReflectionTestUtils.setFieldRecursively(config, "keepAliveSeconds", 30);
        ReflectionTestUtils.setFieldRecursively(config, "threadNamePrefix", "ut-pool-");

        ExecutorService executor = config.taskEngineEventExecutor();
        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;

        assertEquals(2, pool.getCorePoolSize());
        assertEquals(2, pool.getMaximumPoolSize());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        executor.submit(() -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(threadName.get().startsWith("ut-pool-"));
        executor.shutdownNow();
    }
}

