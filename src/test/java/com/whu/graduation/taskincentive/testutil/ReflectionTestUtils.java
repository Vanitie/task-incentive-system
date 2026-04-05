package com.whu.graduation.taskincentive.testutil;

import java.lang.reflect.Field;

/**
 * Test-only reflection helpers.
 */
public final class ReflectionTestUtils {

    private ReflectionTestUtils() {
    }

    public static void setFieldRecursively(Object target, String fieldName, Object value) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        Class<?> type = target.getClass();
        Field field = null;
        while (type != null && type != Object.class) {
            try {
                field = type.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException ignore) {
                type = type.getSuperclass();
            }
        }
        if (field == null) {
            throw new IllegalStateException(
                    "Field '" + fieldName + "' not found on class hierarchy of " + target.getClass().getName());
        }
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to set field '" + fieldName + "'", e);
        }
    }
}

