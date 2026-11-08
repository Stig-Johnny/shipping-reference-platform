package io.github.stigjohnny.c4contractkit.sources;

import java.util.List;
import java.util.stream.Stream;

/** Shared scanner, reader and public-gate regressions: synthetic text only, never executed. */
public final class LexicalBoundaryFixtures {
    static final String NUMBER = "a numeric literal or its continuation is outside the supported grammar";
    static final String FLAGS = "regular expression flags must be distinct letters from dgimsuy; "
            + "Unicode sets (v) and escaped or unknown flags are not supported";

    public record Fixture(String name, String source, int line, List<String> reasons) {
        @Override
        public String toString() {
            return name;
        }
    }

    private LexicalBoundaryFixtures() {
    }

    public static Stream<Fixture> cases() {
        Stream<Fixture> numbers = Stream.of("1.", "1.0", "1n", "0x1", "0XFF", "0b1", "0B10", "0o7", "0O7",
                "1e3", "1E3", "1e+3", "1e-3", "1_000.", "1_000.0", "1_000n", "0xA_B", "0b1_0n",
                "0o7_0n", "0x1n", ".5", ".5e2", "0.5_0", "1e1_0")
                .map(number -> new Fixture("numeric member " + number,
                        number + ".constructor.constructor(\"return import('unlisted')\")();", 2,
                        List.of(ImportScanner.CONSTRUCTOR_REFERENCE, ImportScanner.CONSTRUCTOR_CALL)));
        Stream<Fixture> continuations = Stream.of("1_", "1__0", "1_.0", "1._0", "1e_3", "1e+", "1e",
                "0x", "0x_1", "0b2", "0o8", "01", "00n", "1.0n", "1e3n", "1n0", "1n_0", "1_000.x",
                "1constructor", "1e3constructor", "0x1constructor", "1nconstructor", "1α", "1\\u0061")
                .map(number -> new Fixture("uncertain number " + number, "const n = " + number + ";", 2,
                        List.of(NUMBER)));
        Stream<Fixture> flags = Stream.of("gconstructor", "Function", "eval", "require", "gg", "uv", "v", "g0",
                "g_", "gα", "g\\u0069", "g\\u{69}")
                .map(flag -> new Fixture("uncertain regex flags " + flag,
                        "const r = /x/" + flag + ";", 2, List.of(FLAGS)));
        Stream<Fixture> contexts = Stream.of(
                new Fixture("numeric template substitution", "const t = `text ${1..constructor}`;", 2,
                        List.of(ImportScanner.CONSTRUCTOR_REFERENCE)),
                new Fixture("numeric nested template substitution", "const t = `text ${`nested ${1n.constructor}`}`;",
                        2, List.of(ImportScanner.CONSTRUCTOR_REFERENCE)),
                new Fixture("numeric optional continuation", "1..constructor?.constructor('return 1')();", 2,
                        List.of(ImportScanner.CONSTRUCTOR_REFERENCE, ImportScanner.CONSTRUCTOR_CALL)),
                new Fixture("numeric non-null continuation", "1..constructor!.constructor('return 1')();", 2,
                        List.of(ImportScanner.CONSTRUCTOR_REFERENCE, ImportScanner.CONSTRUCTOR_CALL)),
                new Fixture("numeric after continued string", "const s = 'text\\\ncontinued';\n1n.constructor;", 4,
                        List.of(ImportScanner.CONSTRUCTOR_REFERENCE)),
                new Fixture("numeric after private field", "class Box { #value = 1; run() { return 1n.constructor; } }",
                        2, List.of(ImportScanner.CONSTRUCTOR_REFERENCE)));
        return Stream.of(numbers, continuations, flags, contexts).flatMap(stream -> stream);
    }
}
