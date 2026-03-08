package com.whu.graduation.taskincentive.security;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.Optional;

/**
 * 用户上下文，使用 TransmittableThreadLocal 存储当前线程的用户信息
 * 这样在异步任务中也能获取到用户信息
 */
public class UserContext {

    private static final TransmittableThreadLocal<String> USERNAME = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<String> ROLES = new TransmittableThreadLocal<>();

    public static void set(String username, String roles) {
        USERNAME.set(username);
        ROLES.set(roles);
    }

    public static Optional<String> getUsername() {
        return Optional.ofNullable(USERNAME.get());
    }

    public static Optional<String> getRoles() {
        return Optional.ofNullable(ROLES.get());
    }

    public static void clear() {
        USERNAME.remove();
        ROLES.remove();
    }
}
