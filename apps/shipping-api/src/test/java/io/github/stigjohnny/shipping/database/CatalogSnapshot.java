package io.github.stigjohnny.shipping.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What a database's catalog holds, as sorted lines of text that two states can be compared by (ADR 0016): for every
 * schema except PostgreSQL's own and Flyway's history schema, its schemas, relations, columns, constraints, indexes,
 * policies, functions, types, sequences, views, triggers, extended statistics and comments, each with its owner,
 * row-level security flags and effective privileges; the database's own privileges, default privileges and
 * extensions; and the attributes, settings and memberships of the two application roles.
 *
 * <p>Columns of PostgreSQL's one-byte type "char" are cast to text, which concatenation cannot choose for them. The
 * lines name objects, never their OIDs, and a column's position counts only columns that are not dropped, so
 * that a database rebuilt by the same statements compares equal. Rows are not compared: down scripts drop data by
 * design. Not covered: operators, casts, collations, text search objects, foreign data objects, publications and
 * event triggers, none of which a migration of this repository creates.
 */
final class CatalogSnapshot {

    /** The schemas compared: not PostgreSQL's, and not Flyway's history, which is bookkeeping, not schema. */
    private static final String SCOPE = "n.nspname not in ('pg_catalog', 'information_schema', '"
            + ShippingDatabase.HISTORY_SCHEMA + "') and n.nspname !~ '^pg_(toast|temp_)'";

    private static final List<String> QUERIES = List.of(
            "select 'schema ' || n.nspname || ' owner=' || pg_get_userbyid(n.nspowner) || ' acl='"
                    + " || " + acl("n.nspacl", "'n'", "n.nspowner") + " from pg_namespace n where " + SCOPE,
            "select 'relation ' || n.nspname || '.' || c.relname || ' kind=' || c.relkind::text || ' owner='"
                    + " || pg_get_userbyid(c.relowner) || ' persistence=' || c.relpersistence::text || ' rls='"
                    + " || c.relrowsecurity || ' force=' || c.relforcerowsecurity || ' options='"
                    + " || coalesce(array_to_string(c.reloptions, ','), '') || ' acl=' || " + acl("c.relacl",
                    "case when c.relkind = 'S' then 's'::\"char\" else 'r'::\"char\" end", "c.relowner")
                    + " from pg_class c join pg_namespace n on n.oid = c.relnamespace"
                    + " where " + SCOPE + " and c.relkind not in ('i', 'I', 't')",
            "select 'column ' || n.nspname || '.' || c.relname || '.' || a.attname || ' #'"
                    + " || row_number() over (partition by c.oid order by a.attnum) || ' type='"
                    + " || format_type(a.atttypid, a.atttypmod) || ' notnull=' || a.attnotnull || ' default='"
                    + " || coalesce(pg_get_expr(d.adbin, d.adrelid), '') || ' identity=' || a.attidentity::text"
                    + " || ' generated=' || a.attgenerated::text || ' collation=' || coalesce(o.collname, '')"
                    + " || ' acl=' || " + explode("a.attacl")
                    + " from pg_attribute a join pg_class c on c.oid = a.attrelid"
                    + " join pg_namespace n on n.oid = c.relnamespace"
                    + " left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum"
                    + " left join pg_collation o on o.oid = a.attcollation"
                    + " where " + SCOPE + " and c.relkind not in ('i', 'I', 't') and a.attnum > 0"
                    + " and not a.attisdropped",
            "select 'constraint ' || n.nspname || '.' || coalesce(c.relname, t.typname) || '.' || k.conname"
                    + " || ' type=' || k.contype::text || ' deferrable=' || k.condeferrable || ' deferred='"
                    + " || k.condeferred"
                    + " || ' validated=' || k.convalidated || ' ' || pg_get_constraintdef(k.oid)"
                    + " from pg_constraint k join pg_namespace n on n.oid = k.connamespace"
                    + " left join pg_class c on c.oid = k.conrelid left join pg_type t on t.oid = k.contypid"
                    + " where " + SCOPE,
            "select 'index ' || n.nspname || '.' || c.relname || ' primary=' || i.indisprimary || ' '"
                    + " || pg_get_indexdef(i.indexrelid)"
                    + " from pg_index i join pg_class c on c.oid = i.indexrelid"
                    + " join pg_namespace n on n.oid = c.relnamespace where " + SCOPE,
            "select 'policy ' || n.nspname || '.' || c.relname || '.' || p.polname || ' command='"
                    + " || p.polcmd::text"
                    + " || ' permissive=' || p.polpermissive || ' roles=' || coalesce((select string_agg(r, ','"
                    + " order by r) from (select case when g = 0 then 'PUBLIC' else pg_get_userbyid(g) end as r"
                    + " from unnest(p.polroles) g) roles), '') || ' using='"
                    + " || coalesce(pg_get_expr(p.polqual, p.polrelid), '') || ' check='"
                    + " || coalesce(pg_get_expr(p.polwithcheck, p.polrelid), '')"
                    + " from pg_policy p join pg_class c on c.oid = p.polrelid"
                    + " join pg_namespace n on n.oid = c.relnamespace where " + SCOPE,
            "select 'function ' || n.nspname || '.' || p.proname || '('"
                    + " || pg_get_function_identity_arguments(p.oid) || ') returns='"
                    + " || coalesce(pg_get_function_result(p.oid), '') || ' kind=' || p.prokind::text"
                    + " || ' language='"
                    + " || l.lanname || ' volatility=' || p.provolatile::text || ' security_definer=' || p.prosecdef"
                    + " || ' strict=' || p.proisstrict || ' leakproof=' || p.proleakproof || ' parallel='"
                    + " || p.proparallel::text || ' config=' || coalesce(array_to_string(p.proconfig, ';'), '')"
                    + " || ' owner=' || pg_get_userbyid(p.proowner) || ' acl=' || " + acl("p.proacl", "'f'",
                    "p.proowner") + " || ' body=' || md5(p.prosrc)"
                    + " from pg_proc p join pg_namespace n on n.oid = p.pronamespace"
                    + " join pg_language l on l.oid = p.prolang where " + SCOPE,
            "select 'type ' || n.nspname || '.' || t.typname || ' kind=' || t.typtype::text || ' owner='"
                    + " || pg_get_userbyid(t.typowner) || ' acl=' || " + acl("t.typacl", "'T'", "t.typowner")
                    + " from pg_type t join pg_namespace n on n.oid = t.typnamespace where " + SCOPE,
            "select 'sequence ' || n.nspname || '.' || c.relname || ' type=' || format_type(s.seqtypid, null)"
                    + " || ' start=' || s.seqstart || ' increment=' || s.seqincrement || ' min=' || s.seqmin"
                    + " || ' max=' || s.seqmax || ' cache=' || s.seqcache || ' cycle=' || s.seqcycle"
                    + " from pg_sequence s join pg_class c on c.oid = s.seqrelid"
                    + " join pg_namespace n on n.oid = c.relnamespace where " + SCOPE,
            "select 'view ' || n.nspname || '.' || c.relname || ' definition=' || md5(pg_get_viewdef(c.oid))"
                    + " from pg_class c join pg_namespace n on n.oid = c.relnamespace"
                    + " where " + SCOPE + " and c.relkind in ('v', 'm')",
            "select 'trigger ' || n.nspname || '.' || c.relname || '.' || g.tgname || ' enabled='"
                    + " || g.tgenabled::text"
                    + " || ' ' || pg_get_triggerdef(g.oid)"
                    + " from pg_trigger g join pg_class c on c.oid = g.tgrelid"
                    + " join pg_namespace n on n.oid = c.relnamespace where " + SCOPE + " and not g.tgisinternal",
            "select 'statistics ' || n.nspname || '.' || s.stxname || ' ' || pg_get_statisticsobjdef(s.oid)"
                    + " from pg_statistic_ext s join pg_namespace n on n.oid = s.stxnamespace where " + SCOPE,
            // Objects created after initdb have OIDs from 16384 (FirstNormalObjectId); Flyway's own are left out.
            "select 'comment ' || o.type || ' ' || o.identity || ' = ' || d.description"
                    + " from pg_description d cross join lateral pg_identify_object(d.classoid, d.objoid, d.objsubid) o"
                    + " where d.objoid >= 16384 and coalesce(o.schema, '') <> '" + ShippingDatabase.HISTORY_SCHEMA
                    + "' and o.identity <> '" + ShippingDatabase.HISTORY_SCHEMA + "'",
            "select 'default privileges role=' || pg_get_userbyid(a.defaclrole) || ' schema='"
                    + " || coalesce(n.nspname, '') || ' type=' || a.defaclobjtype::text || ' acl=' || "
                    + explode("a.defaclacl")
                    + " from pg_default_acl a left join pg_namespace n on n.oid = a.defaclnamespace",
            "select 'extension ' || e.extname || ' version=' || e.extversion || ' schema=' || n.nspname"
                    + " from pg_extension e join pg_namespace n on n.oid = e.extnamespace",
            "select 'database owner=' || pg_get_userbyid(d.datdba) || ' acl=' || " + acl("d.datacl", "'d'",
                    "d.datdba") + " from pg_database d where d.datname = current_database()",
            "select 'role ' || r.rolname || ' superuser=' || r.rolsuper || ' inherit=' || r.rolinherit"
                    + " || ' createrole=' || r.rolcreaterole || ' createdb=' || r.rolcreatedb || ' login='"
                    + " || r.rolcanlogin || ' replication=' || r.rolreplication || ' bypassrls=' || r.rolbypassrls"
                    + " || ' connlimit=' || r.rolconnlimit || ' config=' || coalesce(array_to_string(r.rolconfig, ';'),"
                    + " '') || ' member_of=' || coalesce((select string_agg(x, ',' order by x) from (select"
                    + " pg_get_userbyid(m.roleid) || ':' || m.admin_option || ':' || m.inherit_option || ':'"
                    + " || m.set_option as x from pg_auth_members m where m.member = r.oid) memberships), '')"
                    + " || ' members=' || coalesce((select string_agg(x, ',' order by x) from (select"
                    + " pg_get_userbyid(m.member) as x from pg_auth_members m where m.roleid = r.oid) members), '')"
                    + " from pg_roles r where r.rolname in ('" + ShippingDatabase.OWNER + "', '"
                    + ShippingDatabase.APP + "')",
            "select 'role setting role=' || case when s.setrole = 0 then 'ALL' else pg_get_userbyid(s.setrole) end"
                    + " || ' database=' || case when s.setdatabase = 0 then 'ALL' else 'this' end || ' config='"
                    + " || array_to_string(s.setconfig, ';') from pg_db_role_setting s where s.setdatabase in (0,"
                    + " (select oid from pg_database where datname = current_database()))");

    private CatalogSnapshot() {
    }

    /** The catalog as the superuser sees it, through a connection of its own. */
    static List<String> of(ShippingDatabase database) throws SQLException {
        try (Connection connection = database.superuser()) {
            return of(connection);
        }
    }

    /** The catalog as this connection's transaction sees it, its own uncommitted changes included. */
    static List<String> of(Connection connection) throws SQLException {
        List<String> lines = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            for (String query : QUERIES) {
                try (ResultSet rows = statement.executeQuery(query)) {
                    while (rows.next()) {
                        String line = rows.getString(1);
                        if (line == null) {
                            throw new IllegalStateException("a catalog query produced a null line: " + query);
                        }
                        lines.add(line);
                    }
                }
            }
        }
        lines.sort(Comparator.naturalOrder());
        return List.copyOf(lines);
    }

    /** An access control list in words, with the default privileges of its object type when it is null. */
    private static String acl(String column, String type, String owner) {
        return explode("coalesce(" + column + ", acldefault(" + type + ", " + owner + "))");
    }

    /** grantee=PRIVILEGE[*]/grantor for every entry, sorted; empty for an empty or null list. */
    private static String explode(String acl) {
        return "coalesce((select string_agg(x, ',' order by x) from (select case when e.grantee = 0 then 'PUBLIC'"
                + " else pg_get_userbyid(e.grantee) end || '=' || e.privilege_type || case when e.is_grantable"
                + " then '*' else '' end || '/' || pg_get_userbyid(e.grantor) as x from aclexplode(" + acl + ") e)"
                + " entries), '')";
    }
}
