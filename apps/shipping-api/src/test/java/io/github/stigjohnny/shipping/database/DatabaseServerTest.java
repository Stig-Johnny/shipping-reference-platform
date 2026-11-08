package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** The JDBC adapter can select a test database without reaching the harness's container. */
class DatabaseServerTest {

    @Test
    void selectsTheDatabaseAndPreservesTheReviewedEndpointAndOptions() {
        assertThat(DatabaseServer.jdbcUrl("jdbc:postgresql://127.0.0.1:49152/test", "migration_round_trip"))
                .isEqualTo("jdbc:postgresql://127.0.0.1:49152/migration_round_trip");
        assertThat(DatabaseServer.jdbcUrl("jdbc:postgresql://127.0.0.1:49152/test?loggerLevel=OFF", "rls"))
                .isEqualTo("jdbc:postgresql://127.0.0.1:49152/rls?loggerLevel=OFF");
    }

    @Test
    void refusesDatabaseNamesThatCouldChangeTheEndpointOrOptions() {
        for (String name : new String[] {"../other", "rls?user=other", "rls/other", "", "UPPER"}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    DatabaseServer.jdbcUrl("jdbc:postgresql://127.0.0.1:49152/test", name));
        }
    }
}
