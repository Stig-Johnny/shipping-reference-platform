package io.github.stigjohnny.c4contractkit.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The web app's configuration is read as data, and every setting that would make TypeScript, Node.js or the browser
 * resolve a specifier otherwise than the check does, or add an import that no source file shows, is refused.
 */
class AppConfigurationTest {

    /** The web app's own tsconfig.json and index.html, as written for the first runnable slice. */
    private static final String TSCONFIG = """
            {
              "compilerOptions": {
                "target": "es2022",
                "lib": ["es2023", "dom", "dom.iterable"],
                "module": "nodenext",
                "moduleResolution": "nodenext",
                "rewriteRelativeImportExtensions": true,
                "verbatimModuleSyntax": true,
                "types": [],
                "strict": true,
                "rootDir": "src",
                "outDir": "dist"
              },
              "include": ["src"],
              "exclude": ["src/**/*.test.ts"]
            }
            """;
    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8">
                <meta http-equiv="Content-Security-Policy" content="default-src 'self'; base-uri 'none'">
                <title>Shipping workbench</title>
                <link rel="stylesheet" href="/workbench.css">
                <script type="module" src="/app/main.js"></script>
              </head>
              <body><div id="app"><noscript>The shipping workbench needs JavaScript.</noscript></div></body>
            </html>
            """;
    private static final String PACKAGE = """
            {
              "name": "@shipping-reference-platform/web",
              "private": true,
              "type": "module",
              "engines": { "node": "26.13.0" },
              "scripts": { "test": "node --test src" },
              "devDependencies": { "typescript": "5.9.3" }
            }
            """;

    @TempDir
    Path app;

    private void write(String name, String text) throws IOException {
        Files.writeString(app.resolve(name), text, StandardCharsets.UTF_8);
    }

    private AppConfiguration read() {
        return AppConfiguration.read(app, "apps/web");
    }

    @Test
    void theWebAppsConfigurationIsReadWithoutARefusal() throws IOException {
        write("tsconfig.json", TSCONFIG);
        write("index.html", PAGE);
        write("package.json", PACKAGE);
        write("README.md", "not read\n");

        AppConfiguration configuration = read();

        assertThat(configuration.refusals()).isEmpty();
        assertThat(configuration.app()).isEqualTo("apps/web");
        assertThat(configuration.node()).contains("26.13.0");
        assertThat(configuration.dependencies()).isEmpty();
        assertThat(configuration.devDependencies()).containsExactly(java.util.Map.entry("typescript", "5.9.3"));
    }

    @Test
    void aMissingDirectoryHasNoConfiguration() {
        AppConfiguration configuration = AppConfiguration.read(app.resolve("absent"), "absent");

        assertThat(configuration.refusals()).isEmpty();
        assertThat(configuration.node()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "\"paths\": { \"@/*\": [\"src/*\"] }| compilerOptions.paths is not supported: it changes what a specifier names or adds an import that no source file shows",
        "\"baseUrl\": \".\"| compilerOptions.baseUrl is not supported: it changes what a specifier names or adds an import that no source file shows",
        "\"rootDirs\": [\"src\", \"generated\"]| compilerOptions.rootDirs is not supported: it changes what a specifier names or adds an import that no source file shows",
        "\"typeRoots\": [\"./types\"]| compilerOptions.typeRoots is not supported: it changes what a specifier names or adds an import that no source file shows",
        "\"moduleSuffixes\": [\".ios\", \"\"]| compilerOptions.moduleSuffixes is not supported: it changes what a specifier names or adds an import that no source file shows",
        "\"customConditions\": [\"dev\"]| compilerOptions.customConditions is not supported: it changes what a specifier names or adds an import that no source file shows",
        "\"allowArbitraryExtensions\": true| compilerOptions.allowArbitraryExtensions is not supported: it changes what a specifier names or adds an import that no source file shows",
        "\"jsxImportSource\": \"preact\"| compilerOptions.jsxImportSource is not supported: it changes what a specifier names or adds an import that no source file shows",
        "\"types\": [\"node\"]| compilerOptions.types is not supported: it must be empty: each name in it adds the types of a package to every file",
        "\"importHelpers\": true| compilerOptions.importHelpers is not supported: it imports tslib into every file that needs a helper",
        "\"jsx\": \"react-jsx\"| compilerOptions.jsx is not supported: it makes every .tsx file import a JSX runtime"})
    void aCompilerOptionThatChangesResolutionOrAddsAnImportIsRefusedWithItsLine(String option, String reason)
            throws IOException {
        write("tsconfig.json", "{\n  \"compilerOptions\": {\n    \"strict\": true,\n    " + option + "\n  }\n}\n");

        assertThat(read().refusals()).extracting(FileRefusal::file, FileRefusal::line, FileRefusal::reason)
                .containsExactly(tuple("tsconfig.json", 4, reason));
    }

    @Test
    void settingsFromAnotherFileAndAConfigurationThatIsNotPlainJsonAreRefused() throws IOException {
        write("tsconfig.json", "{\n  \"extends\": \"./tsconfig.base.json\",\n  \"references\": []\n}\n");
        write("tsconfig.base.json", "{\n  // a comment\n  \"compilerOptions\": {}\n}\n");

        assertThat(read().refusals()).extracting(FileRefusal::file, FileRefusal::line, FileRefusal::reason)
                .containsExactly(
                        tuple("tsconfig.base.json", 2, "is not plain JSON (no comments, no trailing commas): expected "
                                + "a member name in double quotes (comments and trailing commas are not JSON)"),
                        tuple("tsconfig.json", 2, "extends is not supported: settings from another file could change "
                                + "what a specifier names"),
                        tuple("tsconfig.json", 3, "references is not supported: settings from another file could "
                                + "change what a specifier names"));
    }

    @Test
    void aJsxSettingThatAddsNoImportAndTheOtherOptionsPass() throws IOException {
        write("tsconfig.json", "{ \"compilerOptions\": { \"jsx\": \"preserve\", \"types\": [], \"importHelpers\": false } }");

        assertThat(read().refusals()).isEmpty();
    }

    @Test
    void packageJsonMayNameExactVersionsOnlyAndNoResolutionOverride() throws IOException {
        write("package.json", """
                {
                  "imports": { "#x": "./x.js" },
                  "overrides": { "a": "1.0.0" },
                  "dependencies": { "lit": "^3.2.0", "tiny": "1.2.3" },
                  "devDependencies": { "typescript": "5.9.3" },
                  "optionalDependencies": { "fsevents": "2.3.3" },
                  "engines": { "node": ">=26" }
                }
                """);

        AppConfiguration configuration = read();

        assertThat(configuration.dependencies()).containsExactly(java.util.Map.entry("tiny", "1.2.3"));
        assertThat(configuration.node()).isEmpty();
        assertThat(configuration.refusals()).extracting(FileRefusal::line, FileRefusal::reason).containsExactly(
                tuple(2, "imports is not supported: it changes what a specifier names or what is installed"),
                tuple(3, "overrides is not supported: it changes what a specifier names or what is installed"),
                tuple(4, "dependencies: the version of lit must be exact, so that it names one reviewed release"),
                tuple(6, "optionalDependencies is not supported: it changes what a specifier names or what is "
                        + "installed"),
                tuple(7, "engines.node must name one exact version of Node.js, whose review approves its built-in "
                        + "modules"));
    }

    @Test
    void aPageMayLoadItsOwnScriptAndStyleSheetOnly() throws IOException {
        write("index.html", """
                <!doctype html>
                <base href="https://cdn.example/">
                <script type="importmap">{ "imports": { "x": "/x.js" } }</script>
                <script type="module">import './hidden.js';</script>
                <script src="https://cdn.example/x.js"></script>
                <script src="//cdn.example/y.js"></script>
                <link rel="stylesheet" href="https://cdn.example/x.css">
                <style>
                  @import url('/more.css');
                </style>
                <SCRIPT TYPE="ImportMap">{}</SCRIPT>
                <script src=/app/main.js type=module></script>
                """);

        assertThat(read().refusals()).extracting(FileRefusal::line, FileRefusal::reason).containsExactly(
                tuple(2, "a <base> element is not supported: it changes what every URL of the page names"),
                tuple(3, "an import map is not supported: it changes what a specifier names"),
                tuple(4, "an inline script is not supported: the check never reads its imports"),
                tuple(5, "a script from another origin (https://cdn.example/x.js) is not supported"),
                tuple(6, "a script from another origin (//cdn.example/y.js) is not supported"),
                tuple(7, "a link to another origin (https://cdn.example/x.css) is not supported"),
                tuple(9, "CSS @import is not supported: link each style sheet from index.html"),
                tuple(11, "an import map is not supported: it changes what a specifier names"));
    }

    @Test
    void aTagThatCannotBeReadIsRefused() throws IOException {
        write("index.html", "<script src=\"/a.js\" =x></script>\n<script src=\"/b.js\"\n");

        assertThat(read().refusals()).extracting(FileRefusal::line, FileRefusal::reason).containsExactly(
                tuple(1, "the attributes of a <script> element cannot be read with certainty"),
                tuple(2, "a <script> tag that the reader cannot read with certainty"));
    }

    @Test
    void aConfigurationFileThatIsNotUtf8OrIsALinkIsRefused() throws IOException {
        Files.write(app.resolve("package.json"), new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'});
        Files.createSymbolicLink(app.resolve("tsconfig.json"), app.resolve("package.json"));

        assertThat(read().refusals()).extracting(FileRefusal::file, FileRefusal::reason).containsExactly(
                tuple("package.json", "is not valid UTF-8"),
                tuple("tsconfig.json", "is not a regular file: the check reads only regular files"));
    }
}
