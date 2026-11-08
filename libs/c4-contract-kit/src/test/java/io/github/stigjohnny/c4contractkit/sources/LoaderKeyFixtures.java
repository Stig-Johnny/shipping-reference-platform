package io.github.stigjohnny.c4contractkit.sources;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/** Shared synthetic scanner and file-reader regressions; no fixture is executed as JavaScript. */
final class LoaderKeyFixtures {
    static final String ESCAPED = "a property key with a backslash escape is not supported";
    static final String COMPUTED = "a computed property key must be one plain string or number literal: "
            + "the reader does not evaluate keys or follow constant bindings";
    static final String TIMER = "a timer reference is not supported unless directly called with a function literal";
    static final String CONSTRUCTOR = "a constructor property reference is not supported: it can reach Function";

    private LoaderKeyFixtures() {
    }

    static Stream<Arguments> keys() {
        return Map.of("Function", ImportScanner.FUNCTION, "eval", ImportScanner.EVAL,
                "Worker", ImportScanner.WORKER, "SharedWorker", ImportScanner.WORKER,
                "importScripts", ImportScanner.WORKER, "serviceWorker", ImportScanner.WORKER,
                "setTimeout", TIMER, "setInterval", TIMER, "constructor", CONSTRUCTOR)
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).flatMap(entry -> {
                    String key = entry.getKey();
                    String escaped = "\\u%04x".formatted((int) key.charAt(0)) + key.substring(1);
                    return Stream.of(
                            arguments("quoted binding " + key, "const { '" + key + "': F } = globalThis; "
                                    + "F(\"return import('unlisted')\")();", entry.getValue()),
                            arguments("double quoted binding " + key, "const { \"" + key
                                    + "\": W } = globalThis; new W('https://example.invalid/a.js');", entry.getValue()),
                            arguments("escaped bracket " + key, "const F = globalThis['" + escaped + "'];", ESCAPED),
                            arguments("escaped binding " + key, "const { '" + escaped + "': F } = globalThis;", ESCAPED),
                            arguments("template key " + key, "const F = globalThis[`" + key + "`];", COMPUTED),
                            arguments("constant key " + key, "const key = '" + key + "'; const F = globalThis[key];", COMPUTED),
                            arguments("constant binding " + key, "const key = '" + key
                                    + "'; const { [key]: F } = globalThis;", COMPUTED),
                            arguments("concatenated key " + key, "const F = globalThis['" + key.charAt(0)
                                    + "' + '" + key.substring(1) + "'];", COMPUTED),
                            arguments("parenthesized key " + key, "const F = globalThis[('" + key + "')];", COMPUTED));
                });
    }
}
