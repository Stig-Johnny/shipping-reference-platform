package io.github.stigjohnny.docs.testing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.TestEvidence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The copy of the repository that the documentation tests work on: every test source tree comes along, wherever it is
 * (the documentation tool's, the contract kit's, an application's), and the passing reports cover every test that
 * capabilities.yaml names as evidence. Otherwise a test class named as evidence elsewhere would not exist in the copy.
 */
class FixtureTest {

    @TempDir
    Path dir;

    @Test
    void everyTestSourceTreeIsCopiedWhereverItIsAndBuildOutputIsNot() throws IOException {
        Path source = dir.resolve("source");
        write(source, "libs/kit/src/test/java/io/example/kit/KitTest.java", "class KitTest {}\n");
        write(source, "apps/api/src/test/java/io/example/api/ApiTest.java", "class ApiTest {}\n");
        write(source, "tools/docs/src/test/java/io/example/docs/DocsTest.java", "class DocsTest {}\n");
        write(source, "libs/kit/build/generated/src/test/java/io/example/kit/Stale.java", "class Stale {}\n");

        Fixture fixture = Fixture.copy(dir.resolve("copy"), source);

        assertThat(fixture.root().resolve("libs/kit/src/test/java/io/example/kit/KitTest.java")).exists();
        assertThat(fixture.root().resolve("apps/api/src/test/java/io/example/api/ApiTest.java")).exists();
        assertThat(fixture.root().resolve("tools/docs/src/test/java/io/example/docs/DocsTest.java")).exists();
        assertThat(fixture.root().resolve("libs/kit/build")).doesNotExist();
    }

    @Test
    void passingEvidenceCoversEveryTestTheCopiedCapabilitiesName() throws IOException {
        Path source = dir.resolve("source");
        write(source, "libs/kit/src/test/java/io/example/kit/KitTest.java", "class KitTest {}\n");
        write(source, "tools/docs/src/test/java/io/example/docs/DocsTest.java", "class DocsTest {}\n");
        write(source, Capabilities.PATH, """
                schemaVersion: 1
                capabilities: []
                controls:
                  - id: control.example
                    title: An example control.
                    where: libs/kit
                    check: java
                    status: shipped
                    target: v0.1
                    evidence: [KitTest, DocsTest]
                    acceptanceTests: [KitTest, DocsTest]
                    negativeFixture: None.
                    docs: docs/example.md
                """);

        Fixture fixture = Fixture.copy(dir.resolve("copy"), source).passingEvidence();
        TestEvidence evidence = TestEvidence.read(fixture.root(), List.of(fixture.reports()));

        assertThat(evidence.passed("KitTest")).isTrue();
        assertThat(evidence.passed("DocsTest")).isTrue();
        assertThat(fixture.reports().resolve("TEST-io.example.kit.KitTest.xml")).exists();
    }

    @Test
    void everyEvidenceTestOfTheRealRepositoryPassesInItsCopy() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        Capabilities capabilities = Capabilities.parse(fixture.read(Capabilities.PATH));
        TestEvidence evidence = TestEvidence.read(fixture.root(), List.of(fixture.reports()));

        capabilities.all().forEach(capability -> capability.evidence().forEach(test ->
                assertThat(evidence.passed(test)).as(capability.id() + ": " + test).isTrue()));
    }

    private static void write(Path root, String path, String text) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
