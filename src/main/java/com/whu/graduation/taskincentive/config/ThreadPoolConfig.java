package com.whu.graduation.taskincentive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolConfig {

    @Bean("dbWriteExecutor")
    public ExecutorService dbWriteExecutor() {
        return new ThreadPoolExecutor(
                10,                     // 核心线程数
                50,                     // 最大线程数
                60, TimeUnit.SECONDS,   // 空闲线程存活时间
                new LinkedBlockingQueue<>(1000), // 队列容量
                new ThreadFactory() {   // 自定义线程名
                    private final AtomicInteger count = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "TaskEngine-DBWriter-" + count.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}