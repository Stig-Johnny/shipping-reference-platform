package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.shipping.database.Scripts.Migration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The migration files themselves, without a database. Flyway 12.4.0 also runs shell, Python and other script
 * migrations it finds in a location, so its review allows nothing but versioned SQL migrations there
 * (security/reviews/maven--flyway--12.4.0.md, condition 3). Every migration has a down script where Flyway does not
 * look (ADR 0016).
 */
class MigrationFilesTest {

    private static final Pattern TRANSACTION_CONTROL = Pattern.compile(
            "(?im)^\\s*(begin|commit|rollback|start\\s+transaction|savepoint)\\b\\s*;");

    @Test
    void shipmentsHasItsVersionThreeMigrationAndDownScript() {
        assertThat(Scripts.migrations().stream().map(Migration::version).toList()).contains(3);
        assertThat(Scripts.files(Scripts.DOWN_SCRIPTS).stream().map(path -> path.getFileName().toString()).toList())
                .contains("V3__shipments.down.sql");
    }

    @Test
    void theMigrationLocationHoldsVersionedSqlMigrationsAndNothingElse() {
        List<Path> entries = Scripts.files(Scripts.MIGRATIONS);

        assertThat(entries).as("classpath:" + Scripts.MIGRATIONS).isNotEmpty().allSatisfy(entry -> {
            assertThat(Files.isRegularFile(entry)).as("%s is a regular file", entry).isTrue();
            assertThat(entry.getFileName().toString()).matches(Scripts.MIGRATION);
        });
    }

    @Test
    void versionsRunFromOneWithoutGaps() {
        List<Integer> versions = Scripts.migrations().stream().map(Migration::version).toList();

        assertThat(versions).isNotEmpty().isEqualTo(IntStream.rangeClosed(1, versions.size()).boxed().toList());
    }

    @Test
    void everyMigrationHasOneDownScriptAndEveryDownScriptAMigration() {
        List<String> expected = Scripts.migrations().stream().map(Migration::downScriptName).toList();
        List<String> found = Scripts.files(Scripts.DOWN_SCRIPTS).stream().map(path -> path.getFileName().toString())
                .toList();

        assertThat(expected).isNotEmpty();
        assertThat(found).as("classpath:" + Scripts.DOWN_SCRIPTS).containsExactlyElementsOf(expected)
                .allSatisfy(name -> assertThat(name).matches(Scripts.DOWN_SCRIPT));
        assertThat(Scripts.DOWN_SCRIPTS).doesNotStartWith(Scripts.MIGRATIONS);
    }

    @Test
    void scriptsLeaveTransactionsToWhatRunsThemAndHoldNoFlywayPlaceholders() throws IOException {
        // Flyway runs each migration in a transaction of its own, and a down script runs in the transaction that also
        // deletes its version from Flyway's history; a script that commits on its own would break both.
        List<Path> scripts = Scripts.files(Scripts.MIGRATIONS);
        assertThat(scripts).isNotEmpty();
        for (Path script : Stream.concat(scripts.stream(), Scripts.files(Scripts.DOWN_SCRIPTS).stream()).toList()) {
            String sql = Files.readString(script, StandardCharsets.UTF_8);
            assertThat(TRANSACTION_CONTROL.matcher(sql).find()).as("%s controls its transaction", script).isFalse();
            assertThat(sql).as("%s", script).doesNotContain("${");
        }
    }
}
