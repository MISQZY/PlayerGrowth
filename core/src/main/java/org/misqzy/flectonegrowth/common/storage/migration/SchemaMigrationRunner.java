package org.misqzy.flectonegrowth.common.storage.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** Executes already dialect-resolved SQL statements - no dialect knowledge of its own. */
public final class SchemaMigrationRunner {

    private SchemaMigrationRunner() {}

    public static void run(Connection connection, List<String> statements) throws SQLException {
        for (String sql : statements) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
        }
    }
}
