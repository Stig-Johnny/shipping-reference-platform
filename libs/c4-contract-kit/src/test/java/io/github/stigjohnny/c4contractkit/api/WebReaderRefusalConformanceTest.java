package io.github.stigjohnny.c4contractkit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.github.stigjohnny.c4contractkit.testing.WebFixture;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Every corrected reader refusal must reach the public gate with its file, line and reason. */
class WebReaderRefusalConformanceTest {
    private static final String ESCAPED = "a property key with a backslash escape is not supported";
    private static final String COMPUTED = "a computed property key must be one plain string or number literal: "
            + "the reader does not evaluate keys or follow constant bindings";
    private static final String TIMER = "a timer reference is not supported unless directly called with a function literal";
    private static final String CONSTRUCTOR = "a constructor property reference is not supported: it can reach Function";
    private static final String FUNCTION = "the Function constructor is not supported: the code it builds could "
            + "load modules that the check never sees";
    private static final List<String> SOURCE_PATHS = List.of("web/e2e/loader.ts", "web/e2e/loader.tsx",
            "web/src/api/loader.test.ts", "web/src/api/loader.test.tsx", "web/src/api/loader.ts", "web/src/api/loader.tsx");

    @TempDir
    Path repository;

    @ParameterizedTest(name = "gate loader key: {0}")
    @MethodSource("io.github.stigjohnny.c4contractkit.sources.LoaderKeyFixtures#keys")
    void loaderKeysFailTheGateInBrowserSourcesAndBothKindsOfTests(String description, String source, String reason) {
        assertSourceRefusals(description, source, List.of(reason));
    }

    @ParameterizedTest(name = "gate key syntax and timers: [{index}]")
    @MethodSource("keySyntaxAndTimers")
    void keySyntaxAndIndirectTimersFailTheGate(String source, String reason) {
        assertSourceRefusals(source, source, List.of(reason));
    }

    static Stream<Arguments> keySyntaxAndTimers() {
        return Stream.of(
                arguments("const F = globalThis['Funct\\u0069on']; F(\"return import('unlisted')\")();", ESCAPED),
                arguments("const F = globalThis['Funct\\u{69}on'];", ESCAPED),
                arguments("const F = globalThis['Funct\\x69on'];", ESCAPED),
                arguments("const F = globalThis['Funct\\ion'];", ESCAPED),
                arguments("const F = globalThis['Funct\\\nion'];", ESCAPED),
                arguments("const { 'Funct\\x69on': F } = globalThis;", ESCAPED),
                arguments("const F = globalThis?.['Funct\\u0069on'];", ESCAPED),
                arguments("const F = globalThis[`Funct\\u0069on`];", COMPUTED),
                arguments("const F = globalThis[`Funct${'i'}on`];", COMPUTED),
                arguments("const { [`Function`]: F } = globalThis;", COMPUTED),
                arguments("const key = 'Fun' + 'ction'; const F = (globalThis)[key];", COMPUTED),
                arguments("const key = `Function`; const F = globalThis?.[key];", COMPUTED),
                arguments("const key = 'Function'; const F = globalThis![key];", COMPUTED),
                arguments("const key = 'Function'; const F = (globalThis as typeof globalThis)[key];", COMPUTED),
                arguments("const keys = { name: 'Function' }; const F = globalThis[keys.name];", COMPUTED),
                arguments("const F = globalThis['constructor']; F('return 1')();", CONSTRUCTOR),
                arguments("const F = (() => undefined).constructor; F('return 1')();", CONSTRUCTOR),
                arguments("globalThis['setTimeout'](\"import('unlisted')\", 0);", TIMER),
                arguments("globalThis[`setInterval`](\"import('unlisted')\", 0);", COMPUTED),
                arguments("const schedule = setTimeout; schedule(\"import('unlisted')\", 0);", TIMER),
                arguments("const code = \"import('unlisted')\"; setTimeout(code, 0);", TIMER),
                arguments("setInterval.call(null, \"import('unlisted')\", 0);", TIMER),
                arguments("setTimeout?.(\"import('unlisted')\", 0);", TIMER),
                arguments("setTimeout(function () {} && \"import('unlisted')\", 0);", TIMER),
                arguments("setInterval(function task() {} ? \"import('unlisted')\" : '', 0);", TIMER),
                arguments("setTimeout(async function () {} && \"import('unlisted')\", 0);", TIMER),
                arguments("setInterval(function* () {} && \"import('unlisted')\", 0);", TIMER));
    }

    @ParameterizedTest(name = "gate keeps the quoted key finding before {0}")
    @ValueSource(strings = {"globalThis.Funct\\u0069on", "globalThis.\\u0046unction",
        "globalThis.\\u{46}unction", "\\u0065val", "\\u0057orker", "set\\u0054imeout"})
    void escapedIdentifiersDoNotHideEarlierQuotedLoaderFindings(String identifier) {
        assertSourceRefusals(identifier, "const { 'Function': F } = globalThis;\nconst loader = " + identifier + ";",
                List.of(FUNCTION, "a backslash outside a string, template or regular expression is not supported"));
    }

    private void assertSourceRefusals(String description, String source, List<String> reasons) {
        Map<String, String> files = WebFixture.files();
        SOURCE_PATHS.forEach(path -> files.put("/" + path, "// synthetic fixture\n" + source));
        String model = WebFixture.MODEL.replace("\"web.root\" \"web/src\"",
                "\"web.root\" \"web/src\"\n                \"web.testRoots\" \"web/e2e\"");
        WebReport report = check(model, files);

        assertThat(report.passed()).as(description).isFalse();
        assertThat(report.violations()).as(description).containsExactlyElementsOf(SOURCE_PATHS.stream()
                .flatMap(path -> java.util.stream.IntStream.range(0, reasons.size()).mapToObj(index ->
                        refusal(WebViolationType.UNSUPPORTED_SYNTAX, path, index + 2, reasons.get(index))))
                .toList());
    }

    @ParameterizedTest(name = "gate ambiguous script and link URLs: [{index}]")
    @MethodSource("io.github.stigjohnny.c4contractkit.sources.AppConfigurationTest#uncertainUrls")
    void ambiguousUrlsFailTheGateBeforeOriginClassification(String url) {
        Map<String, String> files = WebFixture.files();
        files.put("/web/index.html", "<!doctype html>\n<script src=\"" + url + "\"></script>");
        files.put("/web/style.html", "<!doctype html>\n<link rel=stylesheet href=\"" + url + "\">");
        WebReport report = check(WebFixture.MODEL, files);

        assertThat(report.passed()).isFalse();
        assertThat(report.violations()).containsExactly(
                refusal(WebViolationType.UNSUPPORTED_CONFIGURATION, "web/index.html", 2,
                        "the attributes of a <script> element cannot be read with certainty"),
                refusal(WebViolationType.UNSUPPORTED_CONFIGURATION, "web/style.html", 2,
                        "the attributes of a <link> element cannot be read with certainty"));
    }

    private WebReport check(String model, Map<String, String> files) {
        return WebConformance.check(WebFixture.write(repository, model, files), repository);
    }

    private static WebViolation refusal(WebViolationType type, String path, int line, String reason) {
        return new WebViolation(type, List.of(path), path + ":" + line + ": " + reason + ".",
                List.of(new ImportEvidence(path, line, "", "refused", "")));
    }
}
