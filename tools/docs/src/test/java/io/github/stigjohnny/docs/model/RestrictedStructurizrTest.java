package io.github.stigjohnny.docs.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.structurizr.dsl.Features;
import com.structurizr.dsl.StructurizrDslParser;
import io.github.stigjohnny.docs.DocsException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The conditions of security/reviews/maven--structurizr-dsl--6.2.4.md and maven--docs-tool-scope-extension.md that
 * apply to the docs tool's own parser factory.
 */
class RestrictedStructurizrTest {

    private static final String MODEL = """
            workspace "Fixture" {
                !identifiers flat
                model {
                    u = person "User" "Uses the system."
                    s = softwareSystem "System" "%s"
                    u -> s "Uses"
                    %s
                }
                views {
                    %s
                    systemContext s "Context" "The context." {
                        include *
                        autoLayout lr
                    }
                }
            }
            """;

    @TempDir
    Path dir;

    /** Every Features constant, found by reflection, so that a feature added by a future version cannot slip in. */
    @Test
    void everyFeatureIsOffOnTheFactorysParsers() throws IllegalAccessException {
        StructurizrDslParser parser = RestrictedStructurizr.newParser();
        List<String> features = new ArrayList<>();
        for (Field field : Features.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                features.add((String) field.get(null));
            }
        }

        assertThat(features).hasSizeGreaterThanOrEqualTo(10);
        assertThat(features).allSatisfy(feature -> assertThat(parser.getFeatures().isEnabled(feature))
                .as("feature %s", feature).isFalse());
    }

    @Test
    void theParsersHttpClientAllowsNoUrl() throws ReflectiveOperationException {
        Object client = RestrictedStructurizr.newParser().getHttpClient();
        Field allowed = client.getClass().getDeclaredField("allowedUrlRegexes");
        allowed.setAccessible(true);

        assertThat((Collection<?>) allowed.get(client)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"!include other.dsl", "!script groovy {\n workspace.name = \"x\"\n }",
            "!plugin com.example.Plugin", "!docs docs", "!adrs adrs", "!INCLUDE https://example.com/x.dsl"})
    void aDirectiveIsRejected(String directive) throws IOException {
        Path dsl = write(MODEL.formatted("Does it.", directive, ""));

        assertThatThrownBy(() -> Workspaces.read(dsl, "architecture/fixture.dsl"))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining("architecture/fixture.dsl")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(dir.toString()));
    }

    @Test
    void anEnvironmentReferenceIsRejected() throws IOException {
        Path dsl = write(MODEL.formatted("Reads ${HOME} at parse time.", "", ""));

        assertThatThrownBy(() -> Workspaces.read(dsl, "architecture/fixture.dsl"))
                .isInstanceOf(DocsException.class).hasMessageContaining("${HOME}");
    }

    @Test
    void directivesInCommentsAreOnlyText() throws IOException {
        Path dsl = write("// no !include, !script or ${NAME} here\n/* nor !plugin\n here */\n"
                + MODEL.formatted("Does it.", "# !docs is a comment too", ""));

        assertThat(Workspaces.read(dsl, "architecture/fixture.dsl").model().views()).hasSize(1);
    }

    @Test
    void mermaidExportPropertiesAreRejected() throws IOException {
        Path dsl = write(MODEL.formatted("Does it.", "", "properties {\n \"mermaid.icons\" \"true\"\n }"));

        assertThatThrownBy(() -> Workspaces.read(dsl, "architecture/fixture.dsl"))
                .isInstanceOf(DocsException.class).hasMessageContaining("mermaid.icons");
    }

    @Test
    void themesAndElementUrlsAreRejected() throws IOException {
        Path themed = write(MODEL.formatted("Does it.", "", "theme https://example.com/theme.json"));
        Path linked = write(MODEL.formatted("Does it.", "s -> u \"Notifies\" {\n url https://example.com\n }", ""));

        assertThatThrownBy(() -> Workspaces.read(themed, "a.dsl")).isInstanceOf(DocsException.class)
                .hasMessageContaining("theme");
        assertThatThrownBy(() -> Workspaces.read(linked, "b.dsl")).isInstanceOf(DocsException.class)
                .hasMessageContaining("URL");
    }

    @Test
    void aMissingFileIsAProblemNotACrash() {
        assertThatThrownBy(() -> Workspaces.read(dir.resolve("absent.dsl"), "architecture/absent.dsl"))
                .isInstanceOf(DocsException.class).hasMessageContaining("architecture/absent.dsl");
    }

    private Path write(String text) throws IOException {
        Path file = Files.createTempFile(dir, "model", ".dsl");
        Files.writeString(file, text);
        return file;
    }
}
