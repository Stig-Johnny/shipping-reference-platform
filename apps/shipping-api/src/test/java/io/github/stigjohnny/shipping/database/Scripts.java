package io.github.stigjohnny.shipping.database;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The SQL that the database tests run: the role bootstrap, the migrations Flyway finds on the class path and the down
 * scripts beside them (ADR 0016).
 */
final class Scripts {

    /** Flyway's location of the migrations, the only one the tests and the application configure. */
    static final String MIGRATIONS = "db/migration";
    /** The down scripts' location, which Flyway never scans. */
    static final String DOWN_SCRIPTS = "db/down";
    static final Pattern MIGRATION = Pattern.compile("V([1-9][0-9]*)__([a-z][a-z0-9_]*)\\.sql");
    static final Pattern DOWN_SCRIPT = Pattern.compile("V([1-9][0-9]*)__([a-z][a-z0-9_]*)\\.down\\.sql");

    private static final String INIT_ROLES = "postgres/init-roles.sql";

    private Scripts() {
    }

    /** One versioned migration: {@code V<version>__<description>.sql}. */
    record Migration(int version, String description) {

        String fileName() {
            return "V" + version + "__" + description + ".sql";
        }

        String downScriptName() {
            return "V" + version + "__" + description + ".down.sql";
        }

        String downSql() {
            return resource(DOWN_SCRIPTS + "/" + downScriptName());
        }

        @Override
        public String toString() {
            return fileName();
        }
    }

    /** postgres/init-roles.sql of :apps:shipping-api, the project directory in which Gradle runs the tests. */
    static String initRoles() {
        Path script = Path.of(INIT_ROLES).toAbsolutePath();
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("the tests run in apps/shipping-api, which holds " + INIT_ROLES
                    + "; it is missing from " + script.getParent());
        }
        try {
            return Files.readString(script, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The migrations in {@link #MIGRATIONS}, sorted by version. A file with another name fails. */
    static List<Migration> migrations() {
        List<Migration> migrations = new ArrayList<>();
        for (Path file : files(MIGRATIONS)) {
            Matcher name = MIGRATION.matcher(file.getFileName().toString());
            if (!name.matches()) {
                throw new IllegalStateException(MIGRATIONS + " holds " + file.getFileName()
                        + ", which is not a versioned SQL migration");
            }
            migrations.add(new Migration(Integer.parseInt(name.group(1)), name.group(2)));
        }
        migrations.sort(Comparator.comparingInt(Migration::version));
        return List.copyOf(migrations);
    }

    /**
     * Every entry, of any kind, in every class-path directory named {@code location}: what Flyway scans for
     * {@code classpath:<location>}. Sorted by name; empty when no such directory exists.
     */
    static List<Path> files(String location) {
        List<Path> files = new ArrayList<>();
        try {
            for (URL url : Collections.list(Scripts.class.getClassLoader().getResources(location))) {
                if (!"file".equals(url.getProtocol())) {
                    throw new IllegalStateException(location + " is read from " + url
                            + "; the tests expect a directory");
                }
                try (Stream<Path> entries = Files.list(Path.of(url.toURI()))) {
                    entries.forEach(files::add);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return List.copyOf(files);
    }

    /** A class-path resource as UTF-8 text. */
    static String resource(String name) {
        try (InputStream in = Scripts.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("the class path holds no " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
