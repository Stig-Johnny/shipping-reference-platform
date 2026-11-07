package io.github.stigjohnny.shipping.database;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The PostgreSQL server of one database test class, started by {@link ReviewedPostgres}: the reviewed image, on a
 * reviewed engine only. Each test asks it for a database of its own ({@link #newDatabase}).
 *
 * <p>The two roles get new random passwords when the server starts. They exist only in this server, which is discarded
 * after the test class, so nothing secret is committed or reused.
 */
final class DatabaseServer implements AutoCloseable {

    private static final Pattern DATABASE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,40}");

    private final PostgreSQLContainer container;
    private final Login owner = new Login(ShippingDatabase.OWNER, password());
    private final Login app = new Login(ShippingDatabase.APP, password());

    private DatabaseServer(PostgreSQLContainer container) {
        this.container = container;
    }

    static DatabaseServer start() {
        return new DatabaseServer(ReviewedPostgres.start());
    }

    /**
     * Creates an empty database, runs postgres/init-roles.sql in it as the superuser and gives both roles this server's
     * passwords. No migration has run in it yet.
     */
    ShippingDatabase newDatabase(String name) throws SQLException {
        if (!DATABASE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("a test database name is lower case: " + name);
        }
        try (Connection connection = connect(container.getDatabaseName(), superuser());
                Statement statement = connection.createStatement()) {
            statement.execute("create database " + name);
        }
        ShippingDatabase database = new ShippingDatabase(this, name);
        try (Connection connection = database.superuser(); Statement statement = connection.createStatement()) {
            statement.execute(Scripts.initRoles());
            for (Login role : List.of(owner, app)) {
                // A hexadecimal literal, generated here: no quoting is needed and nothing outside the test supplies it.
                statement.execute("alter role " + role.user() + " password '" + role.password() + "'");
            }
        }
        return database;
    }

    Connection connect(String database, Login login) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), login.user(), login.password());
    }

    String jdbcUrl(String database) {
        return "jdbc:postgresql://" + container.getHost() + ":"
                + container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database;
    }

    Login superuser() {
        return new Login(container.getUsername(), container.getPassword());
    }

    Login owner() {
        return owner;
    }

    Login app() {
        return app;
    }

    @Override
    public void close() {
        container.stop();
    }

    private static String password() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** A database login. */
    record Login(String user, String password) {

        @Override
        public String toString() {
            return user;
        }
    }
}
