package io.github.stigjohnny.shipping.database;

import io.github.stigjohnny.shipping.database.Scripts.Migration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;

/**
 * One database of a {@link DatabaseServer}, bootstrapped with postgres/init-roles.sql, with a connection for each of
 * its three logins and Flyway configured as ADR 0012 and ADR 0016 say: the owner role applies classpath:db/migration
 * and Flyway keeps its history in a schema of its own, flyway, which no migration touches.
 */
final class ShippingDatabase {

    static final String OWNER = "shipping_owner";
    static final String APP = "shipping_app";
    static final String HISTORY_SCHEMA = "flyway";
    static final String HISTORY = HISTORY_SCHEMA + ".flyway_schema_history";

    private final DatabaseServer server;
    private final String name;

    ShippingDatabase(DatabaseServer server, String name) {
        this.server = server;
        this.name = name;
    }

    String name() {
        return name;
    }

    /**
     * The container's superuser: for the bootstrap, for reading the catalog, and for the changes with which a test
     * shows that a check can fail. What a role can or cannot do is always tried as that role.
     */
    Connection superuser() throws SQLException {
        return server.connect(name, server.superuser());
    }

    /** shipping_owner, the role Flyway uses. */
    Connection owner() throws SQLException {
        return server.connect(name, server.owner());
    }

    /** shipping_app, the runtime role. */
    Connection app() throws SQLException {
        return server.connect(name, server.app());
    }

    /** Flyway as the owner role, up to the latest migration. */
    Flyway flyway() {
        return flyway("latest", "classpath:" + Scripts.MIGRATIONS);
    }

    /** Flyway as the owner role, up to {@code target} ("latest" or a version), reading the given locations. */
    Flyway flyway(String target, String... locations) {
        return Flyway.configure()
                .dataSource(server.jdbcUrl(name), OWNER, server.owner().password())
                .locations(locations)
                .defaultSchema(HISTORY_SCHEMA)
                .schemas(HISTORY_SCHEMA)
                .createSchemas(true)
                .validateMigrationNaming(true)
                .failOnMissingLocations(true)
                .cleanDisabled(true)
                .target(target)
                .load();
    }

    /** Applies every migration up to and including {@code version}; 0 applies none. */
    ShippingDatabase migrateTo(int version) {
        if (version > 0) {
            flyway(String.valueOf(version), "classpath:" + Scripts.MIGRATIONS).migrate();
        }
        return this;
    }

    ShippingDatabase migrate() {
        flyway().migrate();
        return this;
    }

    /** The versions of the applied migrations in Flyway's history, in order, without Flyway's own schema marker. */
    List<String> appliedVersions() {
        return Arrays.stream(flyway().info().applied()).filter(info -> !info.getType().isSynthetic())
                .map(info -> info.getVersion().getVersion()).toList();
    }

    /**
     * Reverts the latest applied migration, as ADR 0016 prescribes: its down script and the deletion of its row in
     * Flyway's history, in one transaction of the owner role.
     */
    void revert(Migration migration) throws SQLException {
        List<String> applied = appliedVersions();
        if (applied.isEmpty() || !applied.getLast().equals(String.valueOf(migration.version()))) {
            throw new IllegalStateException("V" + migration.version() + " is not the latest applied migration: "
                    + applied);
        }
        try (Connection connection = owner()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement();
                    PreparedStatement history = connection.prepareStatement("delete from " + HISTORY
                            + " where version = ?")) {
                statement.execute(migration.downSql());
                history.setString(1, String.valueOf(migration.version()));
                if (history.executeUpdate() != 1) {
                    throw new IllegalStateException("Flyway's history holds no row for version " + migration.version());
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }
}
