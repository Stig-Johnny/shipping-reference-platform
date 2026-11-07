package io.github.stigjohnny.c4contractkit.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A web root is read as data: every file is classified by its name, code is scanned, style sheets are searched for
 * {@code @import}, and every import is resolved against the files that exist, exactly as written, never against the
 * file system's case rules. Whatever cannot be resolved with certainty is unresolved or unsupported, never guessed.
 */
class SourceReaderTest {

    private static final Set<String> FILES = Set.of("app/App.tsx", "app/main.tsx", "ui/Button.tsx", "ui/index.ts",
            "ui/theme.css", "ui/logo.svg", "features/quotes/index.ts", "features/quotes/QuoteCard.tsx",
            "features/quotes/types.d.ts", "both/x.ts", "both/x/index.ts", "api/client.ts");

    private static Resolution resolve(String importer, String specifier) {
        return Specifiers.resolve(importer, specifier, FILES);
    }

    @ParameterizedTest(name = "{0} from {1}")
    @CsvSource({
        "'./App', app/main.tsx, app/App.tsx",
        "'../ui/Button', app/App.tsx, ui/Button.tsx",
        "'../ui', app/App.tsx, ui/index.ts",
        "'../ui/', app/App.tsx, ui/index.ts",
        "'.', ui/Button.tsx, ui/index.ts",
        "'../../ui/theme.css', features/quotes/QuoteCard.tsx, ui/theme.css",
        "'../../ui/logo.svg', features/quotes/QuoteCard.tsx, ui/logo.svg",
        "'./types', features/quotes/QuoteCard.tsx, features/quotes/types.d.ts",
        "'./../quotes/./QuoteCard', features/quotes/index.ts, features/quotes/QuoteCard.tsx",
        "'../api/client.ts', app/App.tsx, api/client.ts",
        "'../features/quotes/index.ts', app/App.tsx, features/quotes/index.ts",
        "'./types.d.ts', features/quotes/QuoteCard.tsx, features/quotes/types.d.ts",
        "'./x.ts', both/index.ts, both/x.ts"})
    void aRelativeSpecifierResolvesToExactlyOneFile(String specifier, String importer, String file) {
        assertThat(resolve(importer, specifier)).isEqualTo(new Resolution.ToFile(file));
    }

    @ParameterizedTest(name = "{0} from {1}")
    @CsvSource({
        "'./Missing', app/App.tsx, 'matches no file: tried app/Missing.ts, app/Missing.tsx, app/Missing.d.ts, app/Missing/index.ts, app/Missing/index.tsx, app/Missing/index.d.ts'",
        "'../ui/button', app/App.tsx, 'matches no file: tried ui/button.ts, ui/button.tsx, ui/button.d.ts, ui/button/index.ts, ui/button/index.tsx, ui/button/index.d.ts'",
        "'../ui/missing.css', app/App.tsx, 'matches no file: ui/missing.css does not exist'",
        "'./x', both/index.ts, 'is ambiguous: it matches both/x.ts and both/x/index.ts'",
        "'../../x', app/App.tsx, 'leaves the web root'",
        "'../app/', ui/Button.tsx, 'matches no file: tried app/index.ts, app/index.tsx, app/index.d.ts'",
        "'./Missing.ts', app/App.tsx, 'matches no file: app/Missing.ts does not exist'",
        "'./App.ts', app/main.tsx, 'matches no file: app/App.ts does not exist'",
        "'../api/Client.ts', app/App.tsx, 'matches no file: api/Client.ts does not exist'",
        "'./types.ts', features/quotes/QuoteCard.tsx, 'matches no file: features/quotes/types.ts does not exist'",
        "'../../api/client.ts', app/App.tsx, 'leaves the web root'"})
    void aRelativeSpecifierThatMatchesNoFileOrSeveralIsUnresolved(String specifier, String importer, String reason) {
        assertThat(resolve(importer, specifier)).isEqualTo(new Resolution.Unresolved(reason));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "react, react",
        "react-dom/client, react-dom",
        "@shipping-reference-platform/client, @shipping-reference-platform/client",
        "@scope/name/deep/path, @scope/name",
        "lodash.debounce, lodash.debounce"})
    void aBareSpecifierNamesAPackage(String specifier, String name) {
        assertThat(resolve("app/App.tsx", specifier)).isEqualTo(new Resolution.ToPackage(name));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
        "''| an empty module specifier is not supported",
        "/src/app/App| an absolute module specifier is not supported: write it relative to the importing file",
        "node:fs| a module specifier with a scheme (such as node:, https: or virtual:) is not supported",
        "https://cdn.example/x.js| a module specifier with a scheme (such as node:, https: or virtual:) is not supported",
        "virtual:pwa| a module specifier with a scheme (such as node:, https: or virtual:) is not supported",
        "#internal| a query or fragment in a module specifier (such as ?url, ?raw or #name) is not supported",
        "./logo.svg?url| a query or fragment in a module specifier (such as ?url, ?raw or #name) is not supported",
        "./App.tsx| a module specifier with the file extension .tsx is not supported: name a .ts file with its extension, or write the specifier without one",
        "./App.js| a module specifier with the file extension .js is not supported: name a .ts file with its extension, or write the specifier without one",
        "./App.mts| a module specifier with the file extension .mts is not supported: name a .ts file with its extension, or write the specifier without one",
        "./App.TS| a module specifier with the file extension .TS is not supported: name a .ts file with its extension, or write the specifier without one",
        "./data.json| a module specifier with the file extension .json is not supported: name a .ts file with its extension, or write the specifier without one",
        ".//App| an empty path segment in a module specifier is not supported",
        "React| 'React' is not a valid npm package name",
        "@scope| '@scope' is not a valid npm package name",
        "_private| '_private' is not a valid npm package name",
        "@/ui/Button| '@/ui' is not a valid npm package name"})
    void whatTheGrammarCannotResolveWithCertaintyIsUnsupported(String specifier, String reason) {
        assertThat(resolve("app/App.tsx", specifier)).isEqualTo(new Resolution.Unsupported(reason.strip()));
    }

    @Test
    void aDotTsSpecifierNamesExactlyThatFileAndNothingElseIsTried(@TempDir Path root) throws IOException {
        // TypeScript with rewriteRelativeImportExtensions emits './QuoteCard.ts' as './QuoteCard.js'. The check names
        // exactly the file written: no other extension, no index file and no other letter case is tried.
        write(root, "features/quotes/index.ts", "export { QuoteCard } from './QuoteCard.ts';\n");
        write(root, "features/quotes/QuoteCard.ts", "export const QuoteCard = 1;\n");
        write(root, "features/quotes/Offer.tsx", "export const Offer = 1;\n");
        write(root, "app/App.ts", """
                import { QuoteCard } from '../features/quotes/index.ts';
                import type { Offer } from '../features/quotes/Offer.ts';
                const lazy = () => import('../features/quotes/quotecard.ts');
                """);

        SourceSet set = SourceReader.read(root, "src");

        assertThat(set.refusals()).isEmpty();
        assertThat(set.imports()).extracting(ResolvedImport::file, ResolvedImport::line, ResolvedImport::specifier,
                ResolvedImport::kind, ResolvedImport::resolution).containsExactly(
                tuple("app/App.ts", 1, "../features/quotes/index.ts", ImportKind.IMPORT,
                        new Resolution.ToFile("features/quotes/index.ts")),
                tuple("app/App.ts", 2, "../features/quotes/Offer.ts", ImportKind.TYPE_IMPORT,
                        new Resolution.Unresolved("matches no file: features/quotes/Offer.ts does not exist")),
                tuple("app/App.ts", 3, "../features/quotes/quotecard.ts", ImportKind.DYNAMIC,
                        new Resolution.Unresolved("matches no file: features/quotes/quotecard.ts does not exist")),
                tuple("features/quotes/index.ts", 1, "./QuoteCard.ts", ImportKind.RE_EXPORT,
                        new Resolution.ToFile("features/quotes/QuoteCard.ts")));
    }

    @Test
    void directoryImportsOfTheParentAndOfTheRootNameTheirIndexFiles() {
        Set<String> files = Set.of("index.ts", "ui/index.ts", "ui/forms/Field.tsx", "ui/logo.PNG");

        assertThat(Specifiers.resolve("ui/forms/Field.tsx", "..", files)).isEqualTo(new Resolution.ToFile("ui/index.ts"));
        assertThat(Specifiers.resolve("main.tsx", "./", files)).isEqualTo(new Resolution.ToFile("index.ts"));
        assertThat(Specifiers.resolve("ui/index.ts", "./logo.PNG", files))
                .isEqualTo(new Resolution.ToFile("ui/logo.PNG"));
        assertThat(Specifiers.resolve("main.tsx", "./ui/forms/Field", files))
                .isEqualTo(new Resolution.ToFile("ui/forms/Field.tsx"));
    }

    @Test
    void packageNamesFollowTheNpmRulesForNewPackages() {
        String longest = "a".repeat(214);

        assertThat(Specifiers.resolve("a.ts", longest, FILES)).isEqualTo(new Resolution.ToPackage(longest));
        assertThat(Specifiers.resolve("a.ts", longest + "a", FILES))
                .isEqualTo(new Resolution.Unsupported("'" + longest + "a' is not a valid npm package name"));
        assertThat(Specifiers.resolve("a.ts", "@scope/", FILES))
                .isEqualTo(new Resolution.Unsupported("'@scope/' is not a valid npm package name"));
        assertThat(Specifiers.resolve("a.ts", ".bin", FILES))
                .isEqualTo(new Resolution.Unsupported("'.bin' is not a valid npm package name"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "App.tsx, CODE", "index.ts, CODE", "types.d.ts, CODE", "App.test.tsx, TEST", "format.test.ts, TEST",
        "App.spec.tsx, CODE", "theme.css, STYLE", "logo.svg, ASSET", "photo.png, ASSET", "photo.jpg, ASSET",
        "photo.jpeg, ASSET", "anim.gif, ASSET", "photo.webp, ASSET", "photo.avif, ASSET", "favicon.ico, ASSET",
        "font.woff, ASSET", "font.woff2, ASSET", "App.TS, UNSUPPORTED", "legacy.js, UNSUPPORTED",
        "data.json, UNSUPPORTED", "Makefile, UNSUPPORTED", "notes.md, UNSUPPORTED", "logo.SVG, UNSUPPORTED"})
    void everyFileIsClassifiedByItsName(String name, FileKind kind) {
        assertThat(SourceReader.kind(name)).isEqualTo(kind);
    }

    @Test
    void theReaderClassifiesScansAndResolvesEveryFile(@TempDir Path root) throws IOException {
        write(root, "app/main.tsx", "import { App } from './App';\nimport '../ui/theme.css';\n");
        write(root, "app/App.tsx", "import type { Props } from '../ui';\nexport const App = () => <div/>;\n");
        write(root, "app/App.test.tsx", "import { App } from './App';\nrequire('never-read');\n");
        write(root, "ui/index.ts", "export * from './Button';\nexport type Props = { a: 1 };\n");
        write(root, "ui/Button.tsx", "export const Button = () => null;\n");
        write(root, "ui/theme.css", ":root { --a: 1; }\n@import './more.css';\n");
        write(root, "ui/logo.svg", "<svg/>\n");
        write(root, "ui/data.json", "{}\n");
        write(root, ".hidden/notes.ts", "require('ignored');\n");
        write(root, "ui/.DS_Store", "binary");

        SourceSet set = SourceReader.read(root, "apps/web/src");

        assertThat(set.root()).isEqualTo("apps/web/src");
        assertThat(set.files()).extracting(SourceFile::path, SourceFile::kind).containsExactly(
                tuple("app/App.test.tsx", FileKind.TEST),
                tuple("app/App.tsx", FileKind.CODE),
                tuple("app/main.tsx", FileKind.CODE),
                tuple("ui/Button.tsx", FileKind.CODE),
                tuple("ui/data.json", FileKind.UNSUPPORTED),
                tuple("ui/index.ts", FileKind.CODE),
                tuple("ui/logo.svg", FileKind.ASSET),
                tuple("ui/theme.css", FileKind.STYLE));
        assertThat(set.imports()).extracting(ResolvedImport::file, ResolvedImport::line, ResolvedImport::specifier,
                ResolvedImport::kind, ResolvedImport::resolution).containsExactly(
                tuple("app/App.tsx", 1, "../ui", ImportKind.TYPE_IMPORT, new Resolution.ToFile("ui/index.ts")),
                tuple("app/main.tsx", 1, "./App", ImportKind.IMPORT, new Resolution.ToFile("app/App.tsx")),
                tuple("app/main.tsx", 2, "../ui/theme.css", ImportKind.SIDE_EFFECT,
                        new Resolution.ToFile("ui/theme.css")),
                tuple("ui/index.ts", 1, "./Button", ImportKind.RE_EXPORT, new Resolution.ToFile("ui/Button.tsx")));
        assertThat(set.refusals()).extracting(FileRefusal::file, FileRefusal::line, FileRefusal::reason)
                .containsExactly(tuple("ui/theme.css", 2, ImportScanner.CSS_IMPORT));
    }

    @Test
    void aFileThatIsNotUtf8IsRefusedAndNotScanned(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("ui"));
        Files.write(root.resolve("ui/Bad.ts"), new byte[] {'c', 'o', 'n', 's', 't', ' ', (byte) 0xC3, (byte) 0x28});

        SourceSet set = SourceReader.read(root, "src");

        assertThat(set.imports()).isEmpty();
        assertThat(set.refusals()).extracting(FileRefusal::file, FileRefusal::line, FileRefusal::reason)
                .containsExactly(tuple("ui/Bad.ts", 1, "the file is not valid UTF-8"));
    }

    @Test
    void aMissingRootHasNoFilesAndARootThatIsAFileIsAnError(@TempDir Path root) throws IOException {
        assertThat(SourceReader.read(root.resolve("absent"), "absent").files()).isEmpty();

        Path file = Files.writeString(root.resolve("file.ts"), "", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> SourceReader.read(file, "file.ts"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The web root file.ts is not a directory");
    }

    @Test
    void aSymbolicLinkAnywhereUnderTheRootIsAnError(@TempDir Path root) throws IOException {
        write(root, "ui/Button.tsx", "export const Button = 1;\n");
        Files.createSymbolicLink(root.resolve("ui/Alias.tsx"), root.resolve("ui/Button.tsx"));

        assertThatThrownBy(() -> SourceReader.read(root, "src"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The web root src holds a symbolic link, ui/Alias.tsx: the check reads only regular files "
                        + "and directories");
    }

    @Test
    void aRootThatIsASymbolicLinkIsAnError(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("real"));
        Path link = Files.createSymbolicLink(root.resolve("link"), root.resolve("real"));

        assertThatThrownBy(() -> SourceReader.read(link, "link"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The web root link is a symbolic link: the check reads only regular files and "
                        + "directories");
    }

    private static void write(Path root, String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
