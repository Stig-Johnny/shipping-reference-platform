package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.BuiltSite;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A reader can follow current declarations, and rebuilding cannot retain removed usage evidence. */
class DependencyUsagePageTest {

    private static final String PAGE = "trust/allowlist.html";
    private static final String LOCK = "tools/docs/gradle.lockfile";
    private static final String DECLARATION = "org.jspecify:jspecify:1.0.1=compileClasspath,runtimeClasspath\n";

    @TempDir
    Path dir;

    @Test
    void aPackageLinksToItsExactDeclarationWithBuildContext() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(LOCK, DECLARATION);
        BuiltSite site = build(fixture);
        String page = site.text(PAGE);
        String row = row(page, "org.jspecify:jspecify", "1.0.1");

        assertThat(row).contains("<details", "Current declarations", "tools/docs/gradle.lockfile:1");
        Matcher link = Pattern.compile("href=\"#(usage-source-[a-f0-9]+)\"").matcher(row);
        assertThat(link.find()).isTrue();
        assertThat(site.ids(PAGE)).contains(link.group(1));
        assertThat(page).contains("id=\"usage-evidence\"", DECLARATION.strip(), "compileClasspath",
                "Declaration evidence is not runtime execution evidence.");
        assertThat(page).doesNotContain("mermaid.min.js\"", "https://github.com/Stig-Johnny/");
    }

    @Test
    void removingOrChangingAVersionRemovesItsLinksOnTheNextBuild() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(LOCK, DECLARATION);
        String first = row(build(fixture).text(PAGE), "org.jspecify:jspecify", "1.0.1");
        assertThat(first).contains("tools/docs/gradle.lockfile:1");

        fixture.write(LOCK, DECLARATION.replace(":1.0.1=", ":9.9.9="));
        String changed = row(build(fixture).text(PAGE), "org.jspecify:jspecify", "1.0.1");
        assertThat(changed).doesNotContain("tools/docs/gradle.lockfile:1").contains("Approved");

        fixture.write(LOCK, "# No dependencies in this fixture\nempty=compileClasspath\n");
        String removed = row(build(fixture).text(PAGE), "org.jspecify:jspecify", "1.0.1");
        assertThat(removed).doesNotContain("tools/docs/gradle.lockfile:1").contains("Approved");
    }

    @Test
    void rejectionAndMissingEvidenceStayDifferentFromPermissionOrProvenAbsence() {
        String page = build(Fixture.copy(dir).passingEvidence()).text(PAGE);

        assertThat(row(page, "org.slf4j:jcl-over-slf4j", "2.0.20"))
                .contains("Rejected", "No declaration found in the scanned inputs.");
        assertThat(page).contains("Evidence not collected for this kind.", "Approved scope",
                "The review history stays in this register when a declaration is removed.")
                .doesNotContain("Not used anywhere", "Unused package");
    }

    private BuiltSite build(Fixture fixture) {
        Path out = dir.resolve("site");
        SiteBuilder.build(Sources.read(fixture.root(), List.of(fixture.reports())), out,
                "0123456789abcdef0123456789abcdef01234567");
        return BuiltSite.read(out);
    }

    private static String row(String page, String coordinate, String version) {
        Matcher rows = Pattern.compile("<tr class=\"entry[^\"]*\">(.*?)</tr>", Pattern.DOTALL).matcher(page);
        while (rows.find()) {
            if (rows.group(1).contains("<code>" + coordinate + "</code>")
                    && rows.group(1).contains("<span class=\"version\">" + version + "</span>")) {
                return rows.group(1);
            }
        }
        throw new AssertionError("No review row for " + coordinate + " " + version);
    }
}
