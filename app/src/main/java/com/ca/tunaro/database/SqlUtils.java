package com.ca.tunaro.database;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/** Pure SQL/string helpers shared by the data layer; free of Android types so they unit-test on the JVM. */
public final class SqlUtils {

    /** ISO-8601 UTC pattern used for every stored listen/sync timestamp. */
    public static final String UTC_MILLIS_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private SqlUtils() {}

    // Leading whitespace and SQL comments, skipped before classifying a console statement.
    private static final Pattern LEADING_NOISE = Pattern.compile("^(\\s+|--[^\\n]*(\\n|$)|/\\*.*?\\*/)+", Pattern.DOTALL);
    private static final Pattern TRANSACTION_CONTROL =
            Pattern.compile("^(BEGIN|COMMIT|END|ROLLBACK|SAVEPOINT|RELEASE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONNECTION_PRAGMA_WRITE = Pattern.compile(
            "^PRAGMA\\s+(\\w+\\s*\\.\\s*)?(JOURNAL_MODE|LOCKING_MODE)\\s*[=(]", Pattern.CASE_INSENSITIVE);

    /**
     * Why the developer SQL console must not run {@code sql}, or null when it may. The console
     * shares the app's single database connection, so a transaction it opens is never closed and
     * blocks every other writer, and a journal or locking mode change applies to the whole app.
     */
    public static String consoleRejectionReason(String sql) {
        if (sql == null) return null;
        String statement = LEADING_NOISE.matcher(sql).replaceFirst("");
        if (TRANSACTION_CONTROL.matcher(statement).find()) {
            return "Transaction statements are not allowed: the console shares the app's connection";
        }
        if (CONNECTION_PRAGMA_WRITE.matcher(statement).find()) {
            return "Changing journal_mode or locking_mode is not allowed: it affects the whole app";
        }
        return null;
    }

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
