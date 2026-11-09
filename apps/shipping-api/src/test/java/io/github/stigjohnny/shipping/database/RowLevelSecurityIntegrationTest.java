package io.github.stigjohnny.shipping.database;

import static io.github.stigjohnny.shipping.database.Fixtures.ALPHA;
import static io.github.stigjohnny.shipping.database.Fixtures.ALPHA_ECONOMY;
import static io.github.stigjohnny.shipping.database.Fixtures.ALPHA_QUOTE;
import static io.github.stigjohnny.shipping.database.Fixtures.ALPHA_STANDARD;
import static io.github.stigjohnny.shipping.database.Fixtures.BETA;
import static io.github.stigjohnny.shipping.database.Fixtures.BETA_QUOTE;
import static io.github.stigjohnny.shipping.database.Fixtures.BETA_STANDARD;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T5 of docs/modules/tenants.md, the database's half: plain JDBC as the runtime role and as the owner role, with no
 * repository and no tenant predicate in the SQL, so that only forced row-level security stands between the tenants.
 * Tenant Alpha and Tenant Beta each have a quote, offers and a prototype flag. Each test binds a tenant as the planned
 * TenantBindingTransactionManager will, with {@code set_config('app.tenant_id', tenant, true)}.
 *
 * <p>Not covered here, because the code does not exist yet: the binding by TenantBindingTransactionManager over the
 * application's pool, and listeners that write under an event's tenant.
 */
@Tag("database")
class RowLevelSecurityIntegrationTest {

    private static final String QUOTES = "select quote_id::text from quotes.quote order by 1";
    private static final String SHIPMENTS = "select shipment_id::text from shipments.shipment order by 1";
    private static final String OFFERS = "select offer_id::text from quotes.quote_offer order by 1";
    private static final String FLAGS = "select tenant_id::text || ' ' || flag_key || ' ' || enabled::text"
            + " from tenants.prototype_flag order by 1";
    private static final String REFUSED = "42501 ERROR: new row violates row-level security policy for table ";
    private static final String NEW_QUOTE = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa9";
    private static final String NEW_OFFER = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaae9";

    private static DatabaseServer server;
    private static ShippingDatabase database;

    @BeforeAll
    static void migrateAndInsertTheRowsOfBothTenants() throws SQLException {
        server = DatabaseServer.start();
        database = server.newDatabase("row_level_security").migrate();
        try (Connection owner = database.owner()) {
            owner.setAutoCommit(false);
            Jdbc.update(owner, "insert into tenants.tenant values (?::uuid, 'Tenant Alpha (synthetic)'),"
                    + " (?::uuid, 'Tenant Beta (synthetic)')", ALPHA, BETA);
            Jdbc.bind(owner, ALPHA);
            Jdbc.update(owner, "insert into tenants.prototype_flag values (?::uuid, 'label-preview', true)", ALPHA);
            Jdbc.bind(owner, BETA);
            Jdbc.update(owner, "insert into tenants.prototype_flag values (?::uuid, 'label-preview', false)", BETA);
            owner.commit();
        }
        try (Connection app = database.app()) {
            app.setAutoCommit(false);
            Jdbc.bind(app, ALPHA);
            Jdbc.update(app, Fixtures.shipment(ALPHA, ALPHA_QUOTE, "SIM0000000001"));
            Jdbc.update(app, Fixtures.alphaQuote(ALPHA, ALPHA_QUOTE));
            Jdbc.update(app, Fixtures.offer(ALPHA, ALPHA_QUOTE, ALPHA_ECONOMY, "ECONOMY", "15.05", 5, 8));
            Jdbc.update(app, Fixtures.offer(ALPHA, ALPHA_QUOTE, ALPHA_STANDARD, "STANDARD", "18.81", 3, 5));
            app.commit();
            Jdbc.bind(app, BETA);
            Jdbc.update(app, Fixtures.shipment(BETA, BETA_QUOTE, "SIM0000000002"));
            Jdbc.update(app, Fixtures.betaQuote());
            Jdbc.update(app, Fixtures.offer(BETA, BETA_QUOTE, BETA_STANDARD, "STANDARD", "12.79", 1, 3));
            app.commit();
        }
    }

    @AfterAll
    static void stopTheServer() {
        server.close();
    }

    @Test
    void eachTenantSeesExactlyItsOwnRowsAsTheRuntimeRoleAndAsTheOwner() throws SQLException {
        for (boolean asOwner : List.of(false, true)) {
            try (Connection connection = asOwner ? database.owner() : database.app()) {
                connection.setAutoCommit(false);
                Jdbc.bind(connection, ALPHA);
                assertThat(Jdbc.strings(connection, QUOTES)).containsExactly(ALPHA_QUOTE);
                assertThat(Jdbc.strings(connection, SHIPMENTS)).containsExactly(ALPHA_QUOTE);
                assertThat(Jdbc.strings(connection, OFFERS)).containsExactly(ALPHA_ECONOMY, ALPHA_STANDARD);
                assertThat(Jdbc.strings(connection, FLAGS)).containsExactly(ALPHA + " label-preview true");
                // Naming Beta's rows does not reach them either.
                assertThat(Jdbc.strings(connection, "select quote_id::text from quotes.quote where quote_id = ?::uuid",
                        BETA_QUOTE)).isEmpty();
                assertThat(Jdbc.strings(connection, "select offer_id::text from quotes.quote_offer"
                        + " where tenant_id = ?::uuid", BETA)).isEmpty();
                connection.commit();
                Jdbc.bind(connection, BETA);
                assertThat(Jdbc.strings(connection, QUOTES)).containsExactly(BETA_QUOTE);
                assertThat(Jdbc.strings(connection, SHIPMENTS)).containsExactly(BETA_QUOTE);
                assertThat(Jdbc.strings(connection, OFFERS)).containsExactly(BETA_STANDARD);
                assertThat(Jdbc.strings(connection, FLAGS)).containsExactly(BETA + " label-preview false");
                connection.commit();
            }
        }
    }

    @Test
    void aTenantCannotWriteRowsOfAnotherTenantNorReferenceItsQuote() throws SQLException {
        try (Connection app = database.app()) {
            app.setAutoCommit(false);
            assertThat(asAlpha(app, Fixtures.alphaQuote(BETA, NEW_QUOTE))).startsWith(REFUSED + "\"quote\"");
            assertThat(asAlpha(app, Fixtures.offer(BETA, BETA_QUOTE, NEW_OFFER, "EXPRESS", "13.71", 1, 1)))
                    .startsWith(REFUSED + "\"quote_offer\"");
            // An offer of Alpha's own on Beta's quote: the foreign key includes tenant_id, and Alpha has no such quote.
            assertThat(asAlpha(app, Fixtures.offer(ALPHA, BETA_QUOTE, NEW_OFFER, "EXPRESS", "13.71", 1, 1)))
                    .startsWith("23503 ");
            assertThat(asAlpha(app, Fixtures.alphaQuote(ALPHA, NEW_QUOTE))).as("Alpha's own quote")
                    .isEqualTo(Jdbc.SUCCEEDED);
            Jdbc.bind(app, BETA);
            assertThat(Jdbc.strings(app, QUOTES)).containsExactly(BETA_QUOTE);
            assertThat(Jdbc.strings(app, OFFERS)).containsExactly(BETA_STANDARD);
            app.rollback();
        }
    }

    @Test
    void theRuntimeRoleCannotChangeImmutableRowsOrDeleteAnyRow() throws SQLException {
        Map<String, String> outcomes = new LinkedHashMap<>();
        try (Connection app = database.app()) {
            app.setAutoCommit(false);
            for (String statement : List.of("update quotes.quote set zone = zone",
                    "update quotes.quote_offer set amount = 0", "delete from quotes.quote_offer",
                    "delete from quotes.quote", "update tenants.prototype_flag set enabled = true",
                    "insert into tenants.prototype_flag values ('" + ALPHA + "', 'label-preview', true)",
                    "delete from tenants.prototype_flag", "delete from shipments.shipment")) {
                outcomes.put(statement, asAlpha(app, statement));
            }
        }

        assertThat(outcomes).allSatisfy((statement, outcome) -> assertThat(outcome).as(statement)
                .startsWith("42501 ERROR: permission denied for table"));
    }

    @Test
    void theOwnersQueriesObeyTheForcedPolicies() throws SQLException {
        try (Connection owner = database.owner()) {
            owner.setAutoCommit(false);
            assertThat(counts(owner)).as("quotes, offers and flags without a bound tenant").containsExactly(0L, 0L, 0L, 0L);
            assertThat(Jdbc.outcome(owner, Fixtures.alphaQuote(ALPHA, NEW_QUOTE))).as("an insert without a tenant")
                    .startsWith(REFUSED + "\"quote\"");
            owner.rollback();
            Jdbc.bind(owner, ALPHA);
            assertThat(Jdbc.update(owner, "delete from quotes.quote_offer")).as("offers deleted with Alpha bound")
                    .isEqualTo(2);
            Jdbc.bind(owner, BETA);
            assertThat(Jdbc.strings(owner, OFFERS)).as("Beta's offers after that delete")
                    .containsExactly(BETA_STANDARD);
            owner.rollback();
            assertThat(asAlpha(owner, Fixtures.offer(BETA, BETA_QUOTE, NEW_OFFER, "EXPRESS", "13.71", 1, 1)))
                    .startsWith(REFUSED + "\"quote_offer\"");
        }
    }

    @Test
    void anUpdateThatChangesTheTenantIsRefused() throws SQLException {
        try (Connection owner = database.owner()) {
            owner.setAutoCommit(false);
            assertThat(asAlpha(owner, "update quotes.quote set tenant_id = '" + BETA + "' where quote_id = '"
                    + ALPHA_QUOTE + "'")).startsWith(REFUSED + "\"quote\"");
            assertThat(asAlpha(owner, "update tenants.prototype_flag set tenant_id = '" + BETA + "'"))
                    .startsWith(REFUSED + "\"prototype_flag\"");
            // An update that keeps the tenant passes the same policy.
            Jdbc.bind(owner, ALPHA);
            assertThat(Jdbc.update(owner, "update tenants.prototype_flag set enabled = false")).isEqualTo(1);
            owner.rollback();
        }
        try (Connection app = database.app()) {
            app.setAutoCommit(false);
            assertThat(asAlpha(app, "update quotes.quote set tenant_id = '" + BETA + "'"))
                    .startsWith("42501 ERROR: permission denied for table quote");
            Jdbc.bind(app, ALPHA);
            assertThat(Jdbc.strings(app, QUOTES)).containsExactly(ALPHA_QUOTE);
            assertThat(Jdbc.strings(app, FLAGS)).containsExactly(ALPHA + " label-preview true");
            app.rollback();
        }
    }

    @Test
    void withoutABoundTenantNothingIsVisibleAndNothingCanBeInserted() throws SQLException {
        try (Connection app = database.app()) {
            app.setAutoCommit(false);
            assertThat(Jdbc.value(app, "select coalesce(current_setting('app.tenant_id', true), 'never set')"))
                    .isEqualTo("never set");
            assertThat(counts(app)).as("quotes, offers and flags").containsExactly(0L, 0L, 0L, 0L);
            assertThat(Jdbc.outcome(app, Fixtures.alphaQuote(ALPHA, NEW_QUOTE))).startsWith(REFUSED + "\"quote\"");
            app.rollback();
            for (String tenant : List.of("", Fixtures.NOBODY)) {
                Jdbc.bind(app, tenant);
                assertThat(counts(app)).as("bound to '%s'", tenant).containsExactly(0L, 0L, 0L, 0L);
                app.rollback();
            }
            // A malformed value fails the query instead of matching anything.
            Jdbc.bind(app, "not-a-tenant");
            assertThat(Jdbc.outcome(app, QUOTES)).startsWith("22P02 ");
            app.rollback();
            Jdbc.bind(app, ALPHA);
            assertThat(counts(app)).as("the same queries with Alpha bound").containsExactly(1L, 2L, 1L, 1L);
            app.rollback();
        }
    }

    @Test
    void aReusedConnectionCarriesNoTenantIntoItsNextTransaction() throws SQLException {
        // One connection for every transaction below: what a pool of size one hands out, without any reset between
        // borrowers.
        try (Connection pooled = database.app()) {
            String session = Jdbc.value(pooled, "select pg_backend_pid()::text");
            pooled.setAutoCommit(false);
            Jdbc.bind(pooled, ALPHA);
            assertThat(Jdbc.strings(pooled, QUOTES)).containsExactly(ALPHA_QUOTE);
            pooled.commit();
            assertThat(Jdbc.value(pooled, "select coalesce(current_setting('app.tenant_id', true), '')"))
                    .as("the setting after commit").isEmpty();
            assertThat(counts(pooled)).as("after commit").containsExactly(0L, 0L, 0L, 0L);
            pooled.commit();
            Jdbc.bind(pooled, ALPHA);
            assertThat(Jdbc.strings(pooled, QUOTES)).containsExactly(ALPHA_QUOTE);
            pooled.rollback();
            assertThat(counts(pooled)).as("after rollback").containsExactly(0L, 0L, 0L, 0L);
            pooled.commit();
            Jdbc.bind(pooled, BETA);
            assertThat(Jdbc.strings(pooled, QUOTES)).as("the next tenant's transaction").containsExactly(BETA_QUOTE);
            pooled.commit();
            // In auto-commit mode each statement is a transaction of its own: a binding is gone by the next one.
            pooled.setAutoCommit(true);
            Jdbc.bind(pooled, ALPHA);
            assertThat(counts(pooled)).as("auto-commit").containsExactly(0L, 0L, 0L, 0L);
            // A session-level setting, which the binding never uses, would carry over; the assertions above can fail.
            assertThat(Jdbc.value(pooled, "select set_config('app.tenant_id', ?, false)", ALPHA)).isEqualTo(ALPHA);
            assertThat(Jdbc.strings(pooled, QUOTES)).as("after a session-level setting").containsExactly(ALPHA_QUOTE);
            assertThat(Jdbc.value(pooled, "select pg_backend_pid()::text")).as("one session throughout")
                    .isEqualTo(session);
        }
    }

    @Test
    void shipmentWritesAndUpdatesObeyRlsEvenWithoutTenantPredicates() throws SQLException {
        for (boolean asOwner : List.of(false, true)) {
            try (Connection connection = asOwner ? database.owner() : database.app()) {
                connection.setAutoCommit(false);
                assertThat(Jdbc.outcome(connection, Fixtures.shipment(ALPHA, NEW_QUOTE, "SIM0000000009")))
                        .startsWith(REFUSED + "\"shipment\"");
                connection.rollback();
                assertThat(asAlpha(connection, Fixtures.shipment(BETA, NEW_QUOTE, "SIM0000000009")))
                        .startsWith(REFUSED + "\"shipment\"");
                assertThat(asAlpha(connection, "update shipments.shipment set tenant_id = '" + BETA + "'"))
                        .startsWith(REFUSED + "\"shipment\"");
                Jdbc.bind(connection, ALPHA);
                assertThat(Jdbc.update(connection, "update shipments.shipment set reference = 'ALPHA'"
                        + " where shipment_id = ?::uuid", BETA_QUOTE)).isZero();
                assertThat(Jdbc.update(connection, "update shipments.shipment set reference = 'ALPHA'"))
                        .isEqualTo(1);
                Jdbc.bind(connection, BETA);
                assertThat(Jdbc.value(connection, "select coalesce(reference, '') from shipments.shipment"))
                        .isEmpty();
                connection.rollback();
            }
        }
    }

    /** Runs a statement in a transaction with Tenant Alpha bound, rolls it back and says what happened. */
    private static String asAlpha(Connection connection, String sql) throws SQLException {
        Jdbc.bind(connection, ALPHA);
        String outcome = Jdbc.outcome(connection, sql);
        connection.rollback();
        return outcome;
    }

    private static List<Long> counts(Connection connection) throws SQLException {
        return List.of(Jdbc.count(connection, "quotes.quote"), Jdbc.count(connection, "quotes.quote_offer"),
                Jdbc.count(connection, "tenants.prototype_flag"), Jdbc.count(connection, "shipments.shipment"));
    }
}
