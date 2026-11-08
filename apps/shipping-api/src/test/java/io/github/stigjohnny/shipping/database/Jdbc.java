package io.github.stigjohnny.shipping.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Plain JDBC for the database tests: no repository, no predicate added, nothing between the test and PostgreSQL. */
final class Jdbc {

    /** Successful statements report this; failed ones their SQLSTATE and message. */
    static final String SUCCEEDED = "succeeded";

    private Jdbc() {
    }

    /** Binds a tenant to the current transaction, exactly as the planned TenantBindingTransactionManager will. */
    static void bind(Connection connection, String tenant) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config('app.tenant_id', ?, true)")) {
            statement.setString(1, tenant);
            statement.execute();
        }
    }

    /** The first column of every row, as text ("null" for SQL NULL), in the query's order. */
    static List<String> strings(Connection connection, String sql, Object... parameters) throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, sql, parameters);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String value = rows.getString(1);
                values.add(value == null ? "null" : value);
            }
        }
        return List.copyOf(values);
    }

    /** Every row with its columns as text joined by " | " ("null" for SQL NULL), in the query's order. */
    static List<String> rows(Connection connection, String sql, Object... parameters) throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, sql, parameters);
                ResultSet rows = statement.executeQuery()) {
            int columns = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= columns; column++) {
                    String value = rows.getString(column);
                    row.add(value == null ? "null" : value);
                }
                values.add(String.join(" | ", row));
            }
        }
        return List.copyOf(values);
    }

    /** The single value of a query such as {@code select count(*) ...}. */
    static String value(Connection connection, String sql, Object... parameters) throws SQLException {
        List<String> values = strings(connection, sql, parameters);
        if (values.size() != 1) {
            throw new IllegalStateException("expected one row from " + sql + ", got " + values);
        }
        return values.getFirst();
    }

    static long count(Connection connection, String table) throws SQLException {
        return Long.parseLong(value(connection, "select count(*) from " + table));
    }

    /** Runs a statement and returns the number of rows it changed. */
    static int update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters)) {
            return statement.executeUpdate();
        }
    }

    /**
     * What happens when the statement runs: {@link #SUCCEEDED}, or the SQLSTATE and message of its failure. On a
     * connection in a transaction, a failure aborts the transaction; the caller rolls it back.
     */
    static String outcome(Connection connection, String sql, Object... parameters) {
        try (PreparedStatement statement = prepare(connection, sql, parameters)) {
            statement.execute();
            return SUCCEEDED;
        } catch (SQLException e) {
            return e.getSQLState() + " " + e.getMessage();
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... parameters)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            return statement;
        } catch (SQLException | RuntimeException e) {
            statement.close();
            throw e;
        }
    }
}
