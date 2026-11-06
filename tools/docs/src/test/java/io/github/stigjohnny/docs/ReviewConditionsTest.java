package io.github.stigjohnny.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.testing.Repository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The review conditions that are about how this tool's own code uses its dependencies, checked on the source text:
 * security/reviews/maven--docs-tool-scope-extension.md, maven--snakeyaml-engine--3.2.md and maven--structurizr-dsl--6.2.4.md.
 * Behaviour is tested next to each class; this test keeps every use behind the one class that is allowed to make it.
 */
class ReviewConditionsTest {

    private static final Path MAIN = Repository.file("tools/docs/src/main/java/io/github/stigjohnny/docs");
    private static final Map<String, String> SOURCES = sources();

    @Test
    void snakeYamlIsUsedOnlyThroughTheOneFactory() {
        assertThat(filesContaining("org.snakeyaml")).containsExactly("yaml/SafeYaml.java");
        assertThat(source("yaml/SafeYaml.java"))
                .doesNotContain("loadAll", "composeAll", "setEnvConfig", "setTagConstructors", "BaseConstructor",
                        "ConstructNode", "FailsafeSchema", "setCustomProperty");
    }

    @Test
    void jacksonIsNeverUsedDirectly() {
        assertThat(filesContaining("com.fasterxml")).isEmpty();
    }

    @Test
    void structurizrIsUsedOnlyInTheModelPackageAndParsersComeFromTheFactory() {
        assertThat(filesContaining("com.structurizr")).allMatch(file -> file.startsWith("model/"));
        assertThat(filesContaining("new StructurizrDslParser")).containsExactly("model/RestrictedStructurizr.java");
        assertThat(filesContaining(".allow(")).isEmpty();
        assertThat(filesContaining("setRestricted")).isEmpty();
    }

    @Test
    void onlyTheMermaidExporterIsUsedAndItsOptionsAreNeverSet() {
        List<String> exportImports = SOURCES.values().stream().flatMap(String::lines)
                .filter(line -> line.startsWith("import com.structurizr.export")).distinct().sorted().toList();

        assertThat(exportImports).containsExactly("import com.structurizr.export.Diagram;",
                "import com.structurizr.export.mermaid.MermaidDiagramExporter;");
        assertThat(filesContaining("MERMAID_ICONS_PROPERTY")).isEmpty();
        assertThat(filesContaining("MERMAID_TITLE_PROPERTY")).isEmpty();
        assertThat(filesContaining("MERMAID_SEQUENCE_DIAGRAM_PROPERTY")).isEmpty();
        assertThat(filesContaining(".addProperty(")).isEmpty();
        assertThat(filesContaining("mermaid.icons")).isEmpty();
    }

    @Test
    void theRawExportIsReadOnlyByTheAdapter() {
        assertThat(filesContaining("RawMermaid")).containsExactlyInAnyOrder("model/RawMermaid.java",
                "model/ParsedWorkspace.java", "model/Workspaces.java", "mermaid/MermaidAdapter.java");
        assertThat(filesContaining(".definition()")).allMatch(file -> !file.startsWith("model/"));
    }

    @Test
    void commonMarkIsUsedOnlyByTheRendererWithTablesAloneAndNoRawHtml() {
        assertThat(filesContaining("org.commonmark")).containsExactlyInAnyOrder("markdown/MarkdownRenderer.java",
                "markdown/SiteUrlSanitizer.java");
        List<String> extensionImports = SOURCES.values().stream().flatMap(String::lines)
                .filter(line -> line.startsWith("import org.commonmark.ext")).distinct().sorted().toList();
        assertThat(extensionImports).containsExactly("import org.commonmark.ext.gfm.tables.TableBlock;",
                "import org.commonmark.ext.gfm.tables.TablesExtension;");
        assertThat(filesContaining(".raw(")).isEmpty();
        assertThat(filesContaining("AttributeProvider")).isEmpty();
        assertThat(filesContaining("NodeRenderer")).isEmpty();
        assertThat(filesContaining("percentEncodeUrls")).isEmpty();
    }

    @Test
    void theHtmlRendererIsBuiltOnceWithEscapingAndTheSanitizer() {
        String renderer = source("markdown/MarkdownRenderer.java");

        assertThat(filesContaining("HtmlRenderer.builder()")).containsExactly("markdown/MarkdownRenderer.java");
        assertThat(count(renderer, "HtmlRenderer.builder()")).isEqualTo(1);
        assertThat(renderer).contains("private static final HtmlRenderer RENDERER = HtmlRenderer.builder()")
                .contains(".escapeHtml(true)").contains(".sanitizeUrls(true)")
                .contains(".urlSanitizer(new SiteUrlSanitizer())");
        assertThat(count(renderer, "Parser.builder()")).isEqualTo(1);
    }

    private static List<String> filesContaining(String text) {
        return SOURCES.entrySet().stream().filter(e -> e.getValue().contains(text)).map(Map.Entry::getKey).sorted()
                .toList();
    }

    private static String source(String file) {
        String text = SOURCES.get(file);
        assertThat(text).as(file).isNotNull();
        return text == null ? "" : text;
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }

    private static Map<String, String> sources() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toMap(p -> MAIN.relativize(p).toString().replace('\\', '/'),
                            ReviewConditionsTest::read, (a, b) -> a, TreeMap::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The file's code without comments, so that documentation may name what the code must not do. */
    private static String read(Path file) {
        try {
            return withoutComments(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String withoutComments(String java) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < java.length()) {
            char c = java.charAt(i);
            if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < java.length() && java.charAt(end) != c) {
                    end += java.charAt(end) == '\\' ? 2 : 1;
                }
                out.append(java, i, Math.min(end + 1, java.length()));
                i = end + 1;
            } else if (java.startsWith("/*", i)) {
                int end = java.indexOf("*/", i + 2);
                i = end < 0 ? java.length() : end + 2;
            } else if (java.startsWith("//", i)) {
                int end = java.indexOf('\n', i);
                i = end < 0 ? java.length() : end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
