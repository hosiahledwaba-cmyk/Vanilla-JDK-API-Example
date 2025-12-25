package com.robinhood.util;

import java.time.*;
import java.time.format.DateTimeFormatter;

public class TimeUtils {

    private static final ZoneId GMT = ZoneOffset.UTC;
    private static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_PATTERN).withZone(GMT);

    /** Get current UTC/GMT Instant */
    public static Instant nowUTC() {
        return Instant.now(Clock.systemUTC());
    }

    /** Get current UTC/GMT time formatted as string */
    public static String nowAsString() {
        return FORMATTER.format(nowUTC());
    }

    /** Format any Instant to UTC/GMT string */
    public static String formatUTC(Instant instant) {
        return FORMATTER.format(instant);
    }

    /** Format LocalDateTime as if it's UTC */
    public static String formatUTC(LocalDateTime ldt) {
        return ldt.atZone(GMT).format(FORMATTER);
    }

    /** Parse string (assumed GMT) to Instant */
    public static Instant parseFromGMT(String timeStr) {
        LocalDateTime ldt = LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern(DEFAULT_PATTERN));
        return ldt.atZone(GMT).toInstant();
    }

    /** Format any ZonedDateTime into a GMT string */
    public static String formatToGMT(ZonedDateTime zdt) {
        return zdt.withZoneSameInstant(GMT).format(FORMATTER);
    }

    /** Get the default system pattern */
    public static String getPattern() {
        return DEFAULT_PATTERN;
    }
}
