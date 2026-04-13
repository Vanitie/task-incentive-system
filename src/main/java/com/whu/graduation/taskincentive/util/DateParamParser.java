package com.whu.graduation.taskincentive.util;

import com.whu.graduation.taskincentive.common.error.BusinessException;
import com.whu.graduation.taskincentive.common.error.ErrorCode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Parses optional request date params and supports both legacy and ISO-8601 formats.
 */
public final class DateParamParser {

    private static final DateTimeFormatter LEGACY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateParamParser() {
    }

    public static Date parseNullable(String rawValue, String paramName) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        String value = rawValue.trim();

        try {
            Instant instant = Instant.parse(value);
            return Date.from(instant);
        } catch (Exception ignored) {
        }

        try {
            OffsetDateTime odt = OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return Date.from(odt.toInstant());
        } catch (Exception ignored) {
        }

        try {
            LocalDateTime ldt = LocalDateTime.parse(value, LEGACY_DATE_TIME);
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception ignored) {
        }

        throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                String.format("%s format invalid, supported: ISO-8601 or yyyy-MM-dd HH:mm:ss", paramName)
        );
    }
}

