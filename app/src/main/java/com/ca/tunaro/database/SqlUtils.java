package com.ca.tunaro.database;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/** Pure SQL/string helpers shared by the data layer; free of Android types so they unit-test on the JVM. */
public final class SqlUtils {

    /** ISO-8601 UTC pattern used for every stored listen/sync timestamp. */
    public static final String UTC_MILLIS_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private SqlUtils() {}

    /** Returns {@code count} comma-separated bind placeholders, e.g. "?,?,?". */
    public static String placeholders(int count) {
        StringBuilder sb = new StringBuilder(Math.max(0, count * 2 - 1));
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }

    /**
     * Returns {@code column} if it is one of {@code allowed}; otherwise throws. Guards
     * column names that get concatenated into SQL, since they cannot be bound as parameters.
     */
    public static String requireAllowedColumn(String column, String... allowed) {
        for (String a : allowed) {
            if (a.equals(column)) return column;
        }
        throw new IllegalArgumentException("Column not allowed: " + column);
    }

    /**
     * New UTC formatter for stored timestamps. Uses {@link Locale#US} so digits stay ASCII
     * regardless of the device locale (stored values must sort and parse consistently).
     */
    public static SimpleDateFormat utcDateFormat() {
        SimpleDateFormat fmt = new SimpleDateFormat(UTC_MILLIS_PATTERN, Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt;
    }
}
