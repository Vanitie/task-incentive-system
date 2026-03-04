package com.whu.graduation.taskincentive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class TaskIncentiveSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskIncentiveSystemApplication.class, args);
    }

}
