package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The database harness works: the reviewed PostgreSQL 18.6 image starts on the reviewed engine, under every condition
 * that {@link ReviewedPostgres} checks, and answers a query over JDBC. It proves the harness and nothing else; the
 * schema, the roles and row-level security come with the migrations.
 */
class PostgresHarnessIntegrationTest {

    @Test
    void theReviewedImageStartsOnTheReviewedEngineAndAnswersSelectVersion() throws SQLException {
        try (PostgreSQLContainer postgres = ReviewedPostgres.start()) {
            try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword())) {
                try (Statement statement = connection.createStatement()) {
                    try (ResultSet version = statement.executeQuery("select version()")) {
                        assertThat(version.next()).isTrue();
                        assertThat(version.getString(1)).startsWith("PostgreSQL 18.6 ");
                    }
                }
            }
        }
    }
}
