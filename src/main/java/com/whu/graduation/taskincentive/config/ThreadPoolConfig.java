package com.whu.graduation.taskincentive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolConfig {

    @Value("${app.executors.task-engine-event.core-pool-size:32}")
    private int corePoolSize;

    @Value("${app.executors.task-engine-event.max-pool-size:128}")
    private int maxPoolSize;

    @Value("${app.executors.task-engine-event.queue-capacity:20000}")
    private int queueCapacity;

    @Value("${app.executors.task-engine-event.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${app.executors.task-engine-event.thread-name-prefix:task-engine-event-}")
    private String threadNamePrefix;

    @Bean({"taskEngineEventExecutor", "dbWriteExecutor"})
    public ExecutorService taskEngineEventExecutor() {
        int normalizedMaxPoolSize = Math.max(maxPoolSize, corePoolSize);
        return new ThreadPoolExecutor(
                corePoolSize,
                normalizedMaxPoolSize,
                keepAliveSeconds, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new ThreadFactory() {
                    private final AtomicInteger count = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, threadNamePrefix + count.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}