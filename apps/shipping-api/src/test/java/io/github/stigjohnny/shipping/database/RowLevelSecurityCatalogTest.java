package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T4 of docs/modules/tenants.md, read from PostgreSQL's catalog after every migration: each table is either
 * tenant-owned, with tenant keys, row-level security enabled and forced and the tenant_isolation policy, or one of the
 * four identity tables, which the runtime role cannot touch; the runtime role holds only the grants each table needs,
 * is no superuser, cannot bypass row-level security and owns nothing; and tenants.resolve_demo_token runs as its owner
 * with a fixed search path, for the runtime role only. The inventories below are explicit, so that a new table that
 * nobody classified fails here.
 */
class RowLevelSecurityCatalogTest {

    private static final List<String> TENANT_TABLES = List.of("quotes.quote", "quotes.quote_offer",
            "tenants.prototype_flag");
    private static final List<String> IDENTITY_TABLES = List.of("tenants.app_user", "tenants.demo_token",
            "tenants.membership", "tenants.tenant");
    /** What the runtime role may do on each table; on everything else, nothing. */
    private static final Map<String, List<String>> RUNTIME_GRANTS = Map.of(
            "quotes.quote", List.of("SELECT", "INSERT"),
            "quotes.quote_offer", List.of("SELECT", "INSERT"),
            "tenants.prototype_flag", List.of("SELECT"),
            "tenants.app_user", List.of(),
            "tenants.demo_token", List.of(),
            "tenants.membership", List.of(),
            "tenants.tenant", List.of());
    private static final List<String> TABLE_PRIVILEGES = List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE",
            "REFERENCES", "TRIGGER", "MAINTAIN");
    /** The policy's predicate as PostgreSQL prints it back: ADR 0005's tenant_id = nullif(...)::uuid. */
    private static final String BOUND_TENANT =
            "(tenant_id = (NULLIF(current_setting('app.tenant_id'::text, true), ''::text))::uuid)";
    /** Each identity table and a column for an UPDATE the runtime role tries. */
    private static final Map<String, String> IDENTITY_COLUMNS = Map.of("tenants.app_user", "display_name",
            "tenants.demo_token", "user_id", "tenants.membership", "role", "tenants.tenant", "display_name");
    private static final String TABLES_IN_APPLICATION_SCHEMAS = """
            select n.nspname || '.' || c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
            where c.relkind in ('r', 'p') and n.nspname not in ('pg_catalog', 'information_schema', 'flyway')
                and n.nspname !~ '^pg_(toast|temp_)'
            """;

    private static DatabaseServer server;
    private static ShippingDatabase database;

    @BeforeAll
    static void migrate() throws SQLException {
        server = DatabaseServer.start();
        database = server.newDatabase("catalog").migrate();
    }

    @AfterAll
    static void stopTheServer() {
        server.close();
    }

    @Test
    void everyTableIsTenantOwnedOrOneOfTheFourIdentityTables() throws SQLException {
        try (Connection catalog = database.superuser()) {
            assertThat(Jdbc.strings(catalog, TABLES_IN_APPLICATION_SCHEMAS + " order by 1"))
                    .containsExactlyElementsOf(Stream.concat(TENANT_TABLES.stream(), IDENTITY_TABLES.stream()).sorted()
                            .toList());
            // A tenant_id column marks a tenant-owned table; membership, an identity table, is the only other one.
            assertThat(Jdbc.strings(catalog, TABLES_IN_APPLICATION_SCHEMAS + """
                    and exists (select from pg_attribute a where a.attrelid = c.oid and a.attname = 'tenant_id'
                        and not a.attisdropped) order by 1""")).containsExactly("quotes.quote", "quotes.quote_offer",
                    "tenants.membership", "tenants.prototype_flag");
            for (String table : TENANT_TABLES) {
                assertThat(Jdbc.strings(catalog, """
                        select a.attname from pg_index i join pg_attribute a on a.attrelid = i.indrelid
                            and a.attnum = i.indkey[0]
                        where i.indrelid = ?::regclass and i.indisprimary""", table))
                        .as("the primary key of %s starts with", table).containsExactly("tenant_id");
                // A foreign key of a tenant table includes tenant_id on both sides, so a row cannot reference another
                // tenant's row.
                assertThat(Jdbc.rows(catalog, """
                        select k.conname,
                            exists (select from pg_attribute a where a.attrelid = k.conrelid
                                and a.attnum = any(k.conkey) and a.attname = 'tenant_id')::text,
                            exists (select from pg_attribute a where a.attrelid = k.confrelid
                                and a.attnum = any(k.confkey) and a.attname = 'tenant_id')::text
                        from pg_constraint k where k.conrelid = ?::regclass and k.contype = 'f'""", table))
                        .as("the foreign keys of %s", table)
                        .allSatisfy(key -> assertThat(key).endsWith("| true | true"));
            }
            assertThat(Jdbc.strings(catalog, "select conname from pg_constraint where contype = 'f' and conrelid in"
                    + " ('quotes.quote_offer'::regclass, 'tenants.prototype_flag'::regclass) order by 1"))
                    .containsExactly("prototype_flag_tenant_fkey", "quote_offer_quote_fkey");
        }
    }

    @Test
    void rowLevelSecurityIsEnabledAndForcedOnEveryTenantTableAndOnNoIdentityTable() throws SQLException {
        Map<String, String> flags = new LinkedHashMap<>();
        try (Connection catalog = database.superuser()) {
            for (String table : Stream.concat(TENANT_TABLES.stream(), IDENTITY_TABLES.stream()).toList()) {
                flags.put(table, Jdbc.value(catalog, "select 'enabled=' || relrowsecurity || ' forced='"
                        + " || relforcerowsecurity from pg_class where oid = ?::regclass", table));
            }
        }

        assertThat(flags).allSatisfy((table, state) -> assertThat(state).as(table).isEqualTo(
                TENANT_TABLES.contains(table) ? "enabled=true forced=true" : "enabled=false forced=false"));
    }

    @Test
    void eachTenantTableHasTheTenantIsolationPolicyAndNoOther() throws SQLException {
        try (Connection catalog = database.superuser()) {
            assertThat(Jdbc.rows(catalog, """
                    select schemaname || '.' || tablename, policyname, permissive, roles::text, cmd, qual, with_check
                    from pg_policies where schemaname not in ('pg_catalog', 'information_schema')
                    order by 1, 2""")).containsExactlyElementsOf(TENANT_TABLES.stream().map(table -> table
                    + " | tenant_isolation | PERMISSIVE | {public} | ALL | " + BOUND_TENANT + " | " + BOUND_TENANT)
                    .toList());
        }
    }

    @Test
    void theRuntimeRoleHoldsOnlyTheGrantsEachTableNeeds() throws SQLException {
        Map<String, List<String>> granted = new LinkedHashMap<>();
        try (Connection catalog = database.superuser()) {
            for (String table : RUNTIME_GRANTS.keySet().stream().sorted().toList()) {
                List<String> privileges = new ArrayList<>();
                for (String privilege : TABLE_PRIVILEGES) {
                    if (Boolean.parseBoolean(Jdbc.value(catalog,
                            "select has_table_privilege(?::name, ?::text, ?::text)::text", ShippingDatabase.APP, table,
                            privilege))) {
                        privileges.add(privilege);
                    }
                }
                granted.put(table, List.copyOf(privileges));
            }
            assertThat(granted).as("shipping_app's table privileges").containsExactlyInAnyOrderEntriesOf(
                    RUNTIME_GRANTS);
            assertThat(Jdbc.strings(catalog, TABLES_IN_APPLICATION_SCHEMAS
                    + " and exists (select from aclexplode(c.relacl) e where e.grantee = 0)"))
                    .as("tables with a grant to PUBLIC").isEmpty();
            assertThat(Jdbc.strings(catalog, TABLES_IN_APPLICATION_SCHEMAS
                    + " and exists (select from pg_attribute a where a.attrelid = c.oid and a.attacl is not null)"))
                    .as("tables with column privileges").isEmpty();
            assertThat(Jdbc.rows(catalog, """
                    select s, has_schema_privilege(?::name, s, 'USAGE')::text,
                        has_schema_privilege(?::name, s, 'CREATE')::text,
                        exists (select from pg_namespace n, aclexplode(n.nspacl) e where n.nspname = s
                            and e.grantee = 0)::text
                    from unnest(array['flyway', 'quotes', 'tenants']) s order by 1""", ShippingDatabase.APP,
                    ShippingDatabase.APP)).as("schema, usage, create, granted to PUBLIC").containsExactly(
                    "flyway | false | false | false", "quotes | true | false | false",
                    "tenants | true | false | false");
        }
    }

    @Test
    void theIdentityTablesRefuseTheRuntimeRoleDirectAccess() throws SQLException {
        Map<String, String> outcomes = new LinkedHashMap<>();
        try (Connection app = database.app()) {
            assertThat(Jdbc.count(app, "tenants.prototype_flag")).as("a table the runtime role may read").isZero();
            for (String table : IDENTITY_TABLES) {
                String column = Objects.requireNonNull(IDENTITY_COLUMNS.get(table), table);
                for (String statement : List.of("select * from " + table, "insert into " + table + " default values",
                        "update " + table + " set " + column + " = " + column, "delete from " + table)) {
                    outcomes.put(statement, Jdbc.outcome(app, statement));
                }
            }
        }

        assertThat(outcomes).hasSize(IDENTITY_TABLES.size() * 4).allSatisfy((statement, outcome) ->
                assertThat(outcome).as(statement).startsWith("42501 ERROR: permission denied for table"));
    }

    @Test
    void resolveDemoTokenRunsAsItsOwnerWithAFixedSearchPathAndOnlyForTheRuntimeRole() throws SQLException {
        try (Connection catalog = database.superuser()) {
            assertThat(Jdbc.rows(catalog, """
                    select pg_get_userbyid(p.proowner), p.prosecdef::text, p.provolatile::text, l.lanname,
                        array_to_string(p.proconfig, ';'), pg_get_function_identity_arguments(p.oid),
                        pg_get_function_result(p.oid),
                        (select string_agg(x, ',' order by x) from (select case when e.grantee = 0 then 'PUBLIC'
                            else pg_get_userbyid(e.grantee) end || '=' || e.privilege_type as x
                            from aclexplode(p.proacl) e) grants)
                    from pg_proc p join pg_language l on l.oid = p.prolang
                    where p.oid = 'tenants.resolve_demo_token(bytea)'::regprocedure""")).containsExactly(
                    "shipping_owner | true | s | sql | search_path=pg_catalog, tenants | p_token_sha256 bytea | "
                            + "TABLE(user_id uuid, user_display_name character varying, tenant_id uuid, "
                            + "tenant_display_name character varying, role text) | "
                            + "shipping_app=EXECUTE,shipping_owner=EXECUTE");
        }
        try (Connection owner = database.owner()) {
            owner.setAutoCommit(false);
            Jdbc.update(owner, "insert into tenants.tenant values (?::uuid, 'Tenant Alpha (synthetic)')",
                    Fixtures.ALPHA);
            Jdbc.update(owner, "insert into tenants.app_user values"
                    + " (?::uuid, 'clerk-alpha', 'Clerk Alpha (synthetic)'),"
                    + " (?::uuid, 'no-tenant-user', 'User without tenant (synthetic)')", Fixtures.CLERK_ALPHA,
                    Fixtures.NO_TENANT_USER);
            Jdbc.update(owner, "insert into tenants.membership values (?::uuid, ?::uuid, 'CLERK')",
                    Fixtures.CLERK_ALPHA, Fixtures.ALPHA);
            Jdbc.update(owner, "insert into tenants.demo_token values (sha256(convert_to('demo-clerk-alpha', 'UTF8')),"
                    + " ?::uuid), (sha256(convert_to('demo-no-tenant-user', 'UTF8')), ?::uuid)", Fixtures.CLERK_ALPHA,
                    Fixtures.NO_TENANT_USER);
            owner.commit();
        }
        String resolve = "select * from tenants.resolve_demo_token(sha256(convert_to(?, 'UTF8')))";
        try (Connection app = database.app()) {
            assertThat(Jdbc.rows(app, resolve, "demo-clerk-alpha")).containsExactly(Fixtures.CLERK_ALPHA
                    + " | Clerk Alpha (synthetic) | " + Fixtures.ALPHA + " | Tenant Alpha (synthetic) | CLERK");
            assertThat(Jdbc.rows(app, resolve, "demo-no-tenant-user")).containsExactly(Fixtures.NO_TENANT_USER
                    + " | User without tenant (synthetic) | null | null | null");
            assertThat(Jdbc.rows(app, resolve, "demo-unknown-user")).isEmpty();
            // The function reads what the runtime role itself cannot.
            assertThat(Jdbc.outcome(app, "select * from tenants.demo_token")).startsWith("42501 ");
        }
    }

    @Test
    void neitherRoleIsASuperuserOrBypassesRowLevelSecurityAndTheRuntimeRoleOwnsNothing() throws SQLException {
        try (Connection catalog = database.superuser()) {
            assertThat(Jdbc.rows(catalog, """
                    select rolname, rolsuper::text, rolbypassrls::text, rolinherit::text, rolcreaterole::text,
                        rolcreatedb::text, rolreplication::text, rolcanlogin::text
                    from pg_roles where rolname in (?, ?) order by 1""", ShippingDatabase.OWNER, ShippingDatabase.APP))
                    .as("role, superuser, bypassrls, inherit, createrole, createdb, replication, login")
                    .containsExactly("shipping_app | false | false | false | false | false | false | true",
                            "shipping_owner | false | false | false | false | false | false | true");
            String owned = "select count(*) from pg_shdepend where refobjid = ?::regrole and deptype = 'o'";
            assertThat(Jdbc.value(catalog, owned, ShippingDatabase.APP)).as("objects shipping_app owns").isEqualTo("0");
            assertThat(Long.parseLong(Jdbc.value(catalog, owned, ShippingDatabase.OWNER)))
                    .as("objects shipping_owner owns").isPositive();
            assertThat(Jdbc.strings(catalog, "select pg_get_userbyid(roleid) from pg_auth_members where member ="
                    + " ?::regrole or roleid = ?::regrole", ShippingDatabase.APP, ShippingDatabase.OWNER))
                    .as("memberships of shipping_app, and members of shipping_owner").isEmpty();
            assertThat(Jdbc.value(catalog, "select pg_has_role(?::name, ?::name, 'USAGE')::text",
                    ShippingDatabase.APP, ShippingDatabase.OWNER)).isEqualTo("false");
            assertThat(Jdbc.rows(catalog, """
                    select r, has_database_privilege(r, current_database(), 'CONNECT')::text,
                        has_database_privilege(r, current_database(), 'CREATE')::text,
                        has_database_privilege(r, current_database(), 'TEMPORARY')::text
                    from unnest(array[?::name, ?::name]) r order by 1""", ShippingDatabase.APP, ShippingDatabase.OWNER))
                    .as("role, connect, create, temporary").containsExactly("shipping_app | true | false | false",
                            "shipping_owner | true | true | false");
            // A database without an explicit list has the defaults, in which PUBLIC may connect and create temporary
            // tables.
            assertThat(Jdbc.value(catalog, "select count(*) from pg_database d,"
                    + " aclexplode(coalesce(d.datacl, acldefault('d', d.datdba))) e"
                    + " where d.datname = current_database() and e.grantee = 0")).as("database grants to PUBLIC")
                    .isEqualTo("0");
        }
    }
}
