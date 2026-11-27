package com.ca.tunaro.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Date;
import java.util.Locale;

import org.junit.Test;

public class SqlUtilsTest {

    @Test
    public void placeholdersHandlesZeroOneAndMany() {
        assertEquals("", SqlUtils.placeholders(0));
        assertEquals("?", SqlUtils.placeholders(1));
        assertEquals("?,?,?", SqlUtils.placeholders(3));
    }

    @Test
    public void allowedColumnIsReturned() {
        assertEquals("is_archived", SqlUtils.requireAllowedColumn("is_archived", "is_favourite", "is_archived"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownColumnIsRejected() {
        SqlUtils.requireAllowedColumn("is_archived = 1; DROP TABLE playlists", "is_favourite", "is_archived");
    }

    @Test
    public void utcFormatIsAsciiUnderNonLatinDefaultLocale() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("ar", "EG"));
            String formatted = SqlUtils.utcDateFormat().format(new Date(0L));
            assertEquals("1970-01-01T00:00:00.000Z", formatted);
            assertEquals(0L, SqlUtils.utcDateFormat().parse(formatted).getTime());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void consoleRejectsTransactionControl() {
        assertNotNull(SqlUtils.consoleRejectionReason("BEGIN"));
        assertNotNull(SqlUtils.consoleRejectionReason("  begin immediate;"));
        assertNotNull(SqlUtils.consoleRejectionReason("-- note\nCOMMIT"));
        assertNotNull(SqlUtils.consoleRejectionReason("/* x */ end transaction"));
        assertNotNull(SqlUtils.consoleRejectionReason("ROLLBACK"));
        assertNotNull(SqlUtils.consoleRejectionReason("SAVEPOINT sp1"));
        assertNotNull(SqlUtils.consoleRejectionReason("release sp1"));
    }

    @Test
    public void consoleRejectsConnectionModePragmaWrites() {
        assertNotNull(SqlUtils.consoleRejectionReason("PRAGMA journal_mode=DELETE"));
        assertNotNull(SqlUtils.consoleRejectionReason("pragma main.journal_mode = WAL"));
        assertNotNull(SqlUtils.consoleRejectionReason("PRAGMA locking_mode(EXCLUSIVE)"));
    }

    @Test
    public void consoleAllowsOrdinaryStatements() {
        assertNull(SqlUtils.consoleRejectionReason("SELECT * FROM songs"));
        assertNull(SqlUtils.consoleRejectionReason("PRAGMA journal_mode"));
        assertNull(SqlUtils.consoleRejectionReason("PRAGMA table_info(songs)"));
        assertNull(SqlUtils.consoleRejectionReason("UPDATE songs SET name = 'Begin Again'"));
        assertNull(SqlUtils.consoleRejectionReason("DELETE FROM endings"));
    }
}
