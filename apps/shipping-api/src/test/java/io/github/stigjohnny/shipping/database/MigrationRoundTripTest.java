package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.shipping.database.Scripts.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * ADR 0012: every migration can be reverted by its down script, proven on PostgreSQL. For each migration n, a new
 * database is migrated to n - 1 and its catalog recorded, migrated to n, reverted with n's down script (which must
 * restore the catalog of n - 1 exactly) and migrated to n again (which must restore the catalog of n exactly). A
 * database that went down and up through every version equals one migrated straight to the latest. The comparison is
 * {@link CatalogSnapshot}; the last two tests show that it fails on an incomplete down script and on each kind of
 * change it compares.
 */
class MigrationRoundTripTest {

    private static DatabaseServer server;

    @BeforeAll
    static void startTheServer() {
        server = DatabaseServer.start();
    }

    @AfterAll
    static void stopTheServer() {
        server.close();
    }

    static Stream<Migration> migrations() {
        return Scripts.migrations().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("migrations")
    void theDownScriptRestoresThePreviousCatalogAndTheMigrationTheNextOne(Migration migration) throws SQLException {
        ShippingDatabase database = server.newDatabase("round_trip_v" + migration.version());
        database.migrateTo(migration.version() - 1);
        List<String> previous = CatalogSnapshot.of(database);

        database.migrateTo(migration.version());
        List<String> migrated = CatalogSnapshot.of(database);
        assertThat(migrated).as("V%d changes the catalog", migration.version()).isNotEqualTo(previous);

        database.revert(migration);
        assertThat(CatalogSnapshot.of(database)).as("after %s", migration.downScriptName())
                .containsExactlyElementsOf(previous);
        assertThat(database.appliedVersions()).as("applied after the revert")
                .containsExactlyElementsOf(versionsBelow(migration.version()));

        database.migrateTo(migration.version());
        assertThat(CatalogSnapshot.of(database)).as("after V%d again", migration.version())
                .containsExactlyElementsOf(migrated);
    }

    @Test
    void aDatabaseThatWentDownAndUpThroughEveryVersionEqualsOneMigratedStraight() throws SQLException {
        List<Migration> migrations = Scripts.migrations();
        assertThat(migrations).isNotEmpty();
        ShippingDatabase stepwise = server.newDatabase("round_trip_stepwise");
        for (Migration migration : migrations) {
            stepwise.migrateTo(migration.version());
            stepwise.revert(migration);
            stepwise.migrateTo(migration.version());
        }
        ShippingDatabase straight = server.newDatabase("round_trip_straight").migrate();

        assertThat(CatalogSnapshot.of(stepwise)).containsExactlyElementsOf(CatalogSnapshot.of(straight));
        assertThat(stepwise.appliedVersions()).containsExactlyElementsOf(straight.appliedVersions())
                .hasSameSizeAs(migrations);
    }

    @Test
    void aDownScriptThatLeavesSomethingBehindIsCaught() throws SQLException {
        Migration latest = Scripts.migrations().getLast();
        ShippingDatabase database = server.newDatabase("round_trip_incomplete");
        database.migrateTo(latest.version() - 1);
        List<String> previous = CatalogSnapshot.of(database);
        database.migrateTo(latest.version());
        String downScript = latest.downSql().strip();
        String withoutLastStatement = downScript.substring(0, downScript.lastIndexOf(';', downScript.length() - 2) + 1);

        try (Connection owner = database.owner(); Statement statement = owner.createStatement()) {
            statement.execute(withoutLastStatement);
        }

        assertThat(difference(previous, CatalogSnapshot.of(database)))
                .as("what %s without its last statement leaves behind", latest.downScriptName())
                .isNotEmpty().allSatisfy(line -> assertThat(line).startsWith("schema "));
    }

    @Test
    void theSnapshotSeesEveryKindOfChangeItCompares() throws SQLException {
        ShippingDatabase database = server.newDatabase("round_trip_snapshot").migrate();
        // Each change, made by the superuser in a transaction that is rolled back, and the kind of line it changes.
        Map<String, String> changes = Map.ofEntries(
                Map.entry("revoke insert on quotes.quote from shipping_app", "relation quotes.quote "),
                Map.entry("alter table quotes.quote_offer no force row level security", "relation quotes.quote_offer "),
                Map.entry("alter table quotes.quote alter column zone type varchar(13)", "column quotes.quote.zone "),
                Map.entry("alter table quotes.quote drop constraint quote_zone_check", "constraint quotes.quote."),
                Map.entry("create index quote_issued_at on quotes.quote (issued_at)", "index quotes.quote_issued_at "),
                Map.entry("alter policy tenant_isolation on quotes.quote using (true)", "policy quotes.quote."),
                Map.entry("alter function tenants.resolve_demo_token(bytea) set search_path = public",
                        "function tenants.resolve_demo_token("),
                Map.entry("grant usage on schema quotes to public", "schema quotes "),
                Map.entry("comment on table tenants.tenant is 'changed'", "comment table tenants.tenant "),
                Map.entry("create table public.leftover (id integer)", "relation public.leftover "),
                Map.entry("alter role shipping_app bypassrls", "role shipping_app "),
                Map.entry("grant shipping_owner to shipping_app", "role shipping_app "),
                Map.entry("alter default privileges for role shipping_owner grant select on tables to shipping_app",
                        "default privileges "),
                Map.entry("grant temporary on database " + database.name() + " to shipping_app", "database "));
        try (Connection superuser = database.superuser()) {
            superuser.setAutoCommit(false);
            List<String> baseline = CatalogSnapshot.of(superuser);
            for (Map.Entry<String, String> change : changes.entrySet()) {
                try (Statement statement = superuser.createStatement()) {
                    statement.execute(change.getKey());
                }
                List<String> changed = CatalogSnapshot.of(superuser);
                superuser.rollback();
                assertThat(difference(baseline, changed)).as(change.getKey()).isNotEmpty()
                        .anySatisfy(line -> assertThat(line).startsWith(change.getValue()));
            }
            assertThat(CatalogSnapshot.of(superuser)).as("after every rollback").containsExactlyElementsOf(baseline);
        }
    }

    /** The lines only one of the two snapshots has: what was added, then what was removed. */
    private static List<String> difference(List<String> before, List<String> after) {
        List<String> added = new ArrayList<>(after);
        added.removeAll(before);
        List<String> removed = new ArrayList<>(before);
        removed.removeAll(after);
        return Stream.concat(added.stream(), removed.stream()).toList();
    }

    private static List<String> versionsBelow(int version) {
        return Scripts.migrations().stream().filter(migration -> migration.version() < version)
                .map(migration -> String.valueOf(migration.version())).toList();
    }
}
