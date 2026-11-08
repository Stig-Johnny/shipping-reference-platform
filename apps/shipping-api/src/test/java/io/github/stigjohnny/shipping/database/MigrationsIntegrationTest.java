package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.stigjohnny.shipping.database.Scripts.Migration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * control.migrations (ADR 0005): the migrations apply cleanly to an empty database as the owner role, Flyway refuses an
 * applied migration that was edited afterwards, and the runtime role cannot run DDL.
 */
@Tag("database")
class MigrationsIntegrationTest {

    /** The migrations this commit ships, by version and description: a missing or an extra one fails. */
    private static final List<String> MIGRATIONS = List.of("1 tenants", "2 quotes");

    /**
     * DDL and other schema changes the runtime role tries. TRUNCATE is not DDL, but row-level security does not apply
     * to it. {@code set role} comes last: were it to succeed, everything after it would run as the owner.
     */
    private static final List<String> RUNTIME_DDL = List.of(
            "create schema scratch",
            "create table public.scratch (id integer)",
            "create table tenants.scratch (id integer)",
            "create table quotes.scratch (id integer)",
            "create temporary table scratch (id integer)",
            "create function quotes.scratch() returns integer language sql as 'select 1'",
            "create index on quotes.quote (issued_at)",
            "alter table quotes.quote add column note text",
            "alter table quotes.quote no force row level security",
            "alter table quotes.quote disable row level security",
            "alter table tenants.prototype_flag owner to shipping_app",
            "alter policy tenant_isolation on quotes.quote using (true)",
            "create policy everything on quotes.quote_offer using (true)",
            "drop policy tenant_isolation on tenants.prototype_flag",
            "alter function tenants.resolve_demo_token(bytea) security invoker",
            "comment on table quotes.quote is 'changed at runtime'",
            "grant select on tenants.tenant to shipping_app",
            "truncate quotes.quote_offer",
            "drop table quotes.quote_offer",
            "drop schema quotes cascade",
            "create role scratch",
            "create database scratch",
            "set role shipping_owner");

    private static DatabaseServer server;

    @BeforeAll
    static void startTheServer() {
        server = DatabaseServer.start();
    }

    @AfterAll
    static void stopTheServer() {
        server.close();
    }

    @Test
    void theOwnerRoleMigratesAnEmptyDatabase() throws SQLException {
        ShippingDatabase database = server.newDatabase("migrations_from_empty");
        try (Connection superuser = database.superuser()) {
            assertThat(Jdbc.strings(superuser, "select nspname from pg_namespace"
                    + " where nspname !~ '^(pg_|information_schema$)' order by 1"))
                    .as("schemas before the first migration").containsExactly("public");
        }

        MigrateResult result = database.flyway().migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(MIGRATIONS.size());
        assertThat(Scripts.migrations()).extracting(m -> m.version() + " " + m.description())
                .containsExactlyElementsOf(MIGRATIONS);
        try (Connection superuser = database.superuser()) {
            assertThat(Jdbc.rows(superuser, "select version || ' ' || description, installed_by, success::text from "
                    + ShippingDatabase.HISTORY + " where type = 'SQL' order by installed_rank"))
                    .containsExactlyElementsOf(MIGRATIONS.stream().map(m -> m + " | shipping_owner | true").toList());
            assertThat(Jdbc.rows(superuser, "select rolsuper::text, rolbypassrls::text from pg_roles where rolname = ?",
                    ShippingDatabase.OWNER)).as("Flyway's role").containsExactly("false | false");
            // Everything the migrations created belongs to the owner role, and nothing to the runtime role.
            assertThat(Jdbc.strings(superuser, """
                    select distinct pg_get_userbyid(owner) from (
                        select nspowner as owner from pg_namespace where nspname in ('tenants', 'quotes')
                        union all select relowner from pg_class where relnamespace in ('tenants'::regnamespace,
                            'quotes'::regnamespace)
                        union all select proowner from pg_proc where pronamespace in ('tenants'::regnamespace,
                            'quotes'::regnamespace)
                        union all select typowner from pg_type where typnamespace in ('tenants'::regnamespace,
                            'quotes'::regnamespace)) owners""")).containsExactly(ShippingDatabase.OWNER);
        }
        assertThat(database.flyway().migrate().migrationsExecuted).as("a second run").isZero();
    }

    @Test
    void flywayRefusesAnAppliedMigrationThatWasEditedAfterwards(@TempDir Path copy) throws SQLException, IOException {
        ShippingDatabase database = server.newDatabase("migrations_checksums").migrate();
        assertThat(database.flyway().validateWithResult().validationSuccessful).isTrue();
        for (Migration migration : Scripts.migrations()) {
            Files.writeString(copy.resolve(migration.fileName()),
                    Scripts.resource(Scripts.MIGRATIONS + "/" + migration.fileName()), StandardCharsets.UTF_8);
        }
        // Unchanged copies validate: what fails below is the edit, not the copy.
        assertThat(database.flyway("latest", "filesystem:" + copy).validateWithResult().validationSuccessful).isTrue();

        Files.writeString(copy.resolve("V1__tenants.sql"), "\n-- edited after it was applied\n", StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        Flyway edited = database.flyway("latest", "filesystem:" + copy);
        ValidateResult result = edited.validateWithResult();

        assertThat(result.validationSuccessful).isFalse();
        assertThat(result.invalidMigrations)
                .extracting(output -> output.version, output -> output.errorDetails.errorCode)
                .containsExactly(tuple("1", CoreErrorCode.CHECKSUM_MISMATCH));
        assertThatThrownBy(edited::migrate).isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("checksum mismatch");
    }

    @Test
    void theRuntimeRoleCannotRunDdl() throws SQLException {
        ShippingDatabase database = server.newDatabase("migrations_runtime_ddl").migrate();
        Map<String, String> outcomes = new LinkedHashMap<>();
        try (Connection app = database.app()) {
            // The runtime role connects and reads what it is granted: the refusals below are about DDL only.
            assertThat(Jdbc.count(app, "quotes.quote")).isZero();
            for (String ddl : RUNTIME_DDL) {
                outcomes.put(ddl, Jdbc.outcome(app, ddl));
            }
        }

        assertThat(outcomes).allSatisfy((ddl, outcome) -> assertThat(outcome).as(ddl).startsWith("42501 "));
        // The owner role may, which is why only Flyway uses it: the same kind of change succeeds and is rolled back.
        try (Connection owner = database.owner()) {
            owner.setAutoCommit(false);
            assertThat(Jdbc.outcome(owner, "create table quotes.scratch (id integer)")).isEqualTo(Jdbc.SUCCEEDED);
            assertThat(Jdbc.outcome(owner, "alter table quotes.quote no force row level security"))
                    .isEqualTo(Jdbc.SUCCEEDED);
            owner.rollback();
        }
    }
}
