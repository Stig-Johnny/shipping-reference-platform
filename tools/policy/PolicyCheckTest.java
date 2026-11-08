// PolicyCheckTest: regression tests for the dependency allowlist gate, tools/policy/PolicyCheck.java.
//
// Run with the JDK alone, from any directory:   java tools/policy/PolicyCheckTest.java
//
// Like the checker, it needs nothing but the JDK: no JUnit, no Gradle, nothing downloaded. The source launcher compiles
// this file and, because it uses PolicyCheck, PolicyCheck.java from the same directory (multi-file source launch, JDK 22
// and later). So the cases call the checker's package-private code directly and the checker needs no change to be
// tested; launching PolicyCheck.java never compiles this file. The command-line cases run PolicyCheck.java in a child
// JVM, as CI does, to see its exit codes; a child that does not finish in time fails its case and is stopped. Every
// repository a case checks is built in a temporary directory and deleted afterwards. It prints one line per case and
// exits with 0 only when every case passed, 1 otherwise.
//
// Every type in this file is nested in PolicyCheckTest: a second top-level type would share the checker's package when
// the two files compile together, and could stand in for a name the checker uses.

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PolicyCheckTest {

    /** The day the in-process cases run on. The command-line cases run on the real date, so entries expire in 2099. */
    static final LocalDate TODAY = LocalDate.of(2026, 11, 7);
    static final String LATER = "2099-12-31";
    static final String SHA_A = "a".repeat(64);
    static final String SHA_B = "b".repeat(64);
    static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    /** Text that must never reach the checker's output: it stands for contents the checker reads. */
    static final String MARKER = "marker-" + UUID.randomUUID();
    /** Four letters that are no hexadecimal digits, for a broken escape. */
    static final String MARK4 = "QJZX";
    static final String LINK_REFUSED = "refused: symbolic link in the checked tree: ";
    static final String ROOT_REFUSED = "refused: the checked tree is a symbolic link";
    static final String UNREADABLE_LINE = "unreadable line (expected group:name:version=configurations)";
    static final byte[] WRAPPER_JAR = utf8("a synthetic wrapper jar\n");
    static final byte[] WIDGET = utf8("/* widget 1.0.0, a synthetic vendored file */\n");

    interface Case {
        void run() throws Exception;
    }

    record Run(int exit, String output, String error) {}

    static int passed;
    static final List<String> failed = new ArrayList<>();
    static final List<Path> temporary = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        try {
            json();
            allowlist();
            acceptance();
            gradle();
            wrapper();
            actions();
            npm();
            vendored();
            images();
            imageBypasses();
            imageNormalisation();
            imageFailClosed();
            imageFailClosedForms();
            imageOwnEvasionsRound3();
            reviewedWebCommands();
            tree();
            commandLine();
        } finally {
            for (Path dir : temporary) delete(dir);
        }
        System.out.printf("%n%d passed, %d failed%n", passed, failed.size());
        failed.forEach(name -> System.out.println("FAILED " + name));
        System.exit(failed.isEmpty() && passed > 0 ? 0 : 1);
    }

    static void test(String name, Case body) {
        try {
            body.run();
            passed++;
            System.out.println("ok   " + name);
        } catch (Throwable e) {
            failed.add(name);
            System.out.println("FAIL " + name + "\n       " + e);
        }
    }

    // ---- the JSON reader: RFC 8259 and nothing more ----

    static void json() {
        // A number is -?(0|[1-9][0-9]*)(.[0-9]+)?([eE][-+]?[0-9]+)? and is kept as written: the digest covers that text.
        for (String number : List.of("0", "-0", "7", "-7", "10", "1234567890123456789012345678901234567890", "0.5",
                "-0.5", "1.0", "1.50", "1e5", "1E5", "1e+5", "1e-5", "1.5E-10", "0e0", "-0.0e+0")) {
            test("json: reads the number " + number + " and keeps it as written", () -> {
                expectEquals(new PolicyCheck.Json.JNumber(number), parse(number));
                expectEquals("{\"a\":[" + number + "],\"n\":" + number + "}",
                        canonical("{\"n\": " + number + ", \"a\": [" + number + "]}"));
            });
        }
        for (String token : List.of("+", "-", "+1", "--1", "01", "-01", "00", "1.", ".5", "-.5", "1.e5", "1e", "1E", "1e+",
                "1e-", "e5", "-e5", ".", "1-2", "1+2", "1.2.3", "1e5e5", "2026-11-07", "0x1F", "1_000", "Infinity",
                "-Infinity", "NaN")) {
            test("json: refuses the number " + token, () -> refusedAnywhere(token));
        }
        test("json: reads true, false and null", () -> expectEquals("[true,false,null]", canonical("[true, false, null]")));
        for (String token : List.of("tru", "fals", "nul", "True", "FALSE", "Null", "nil", "undefined", "truex")) {
            test("json: refuses the literal " + token, () -> refusedAnywhere(token));
        }
        test("json: decodes every escape", () -> {
            String[][] escapes = {{"\\\"", "\""}, {"\\\\", "\\"}, {"\\/", "/"}, {"\\b", "\b"}, {"\\f", "\f"},
                    {"\\n", "\n"}, {"\\r", "\r"}, {"\\t", "\t"}, {"\\u0041", "A"}, {"\\u00e9", "\u00e9"},
                    {"\\u00E9", "\u00e9"}, {"\\u0000", "\0"}, {"\\ud83d\\ude00", "\ud83d\ude00"}};
            for (String[] escape : escapes) {
                expectEquals(new PolicyCheck.Json.JString("x" + escape[1] + "y"), parse("\"x" + escape[0] + "y\""));
            }
        });
        test("json: keeps unescaped characters as they are, DEL and non-ASCII included", () -> expectEquals(
                new PolicyCheck.Json.JString("a\u007f\u00e9\u2028\ud83d\ude00 z"),
                parse("\"a\u007f\u00e9\u2028\ud83d\ude00 z\"")));
        for (String text : List.of("\"abc", "\"", "[\"a]", "{\"a\":\"b}", "\"\\\"", "\"\\", "\"a\\")) {
            test("json: refuses the unterminated string " + show(text), () -> refused(text));
        }
        for (char c : new char[] {0, '\t', '\n', '\r', 0x1f}) {
            test("json: refuses U+%04X unescaped in a string".formatted((int) c), () -> refusedAnywhere("\"a" + c + "b\""));
        }
        for (String escape : List.of("\\x", "\\U0041", "\\'", "\\0", "\\u12", "\\u004", "\\u+041", "\\u-041", "\\u 041",
                "\\uGGGG", "\\u00g1", "\\u\u0660\u0660\u0664\u0661", "\\u\uff10\uff10\uff14\uff11")) {
            test("json: refuses the escape " + show(escape), () -> refusedAnywhere("\"" + escape + "\""));
        }
        for (String text : List.of("", " ", "{", "[", "}", "]", "{\"a\":1,}", "[1,]", "[,1]", "{,\"a\":1}", "[1,,2]",
                "{\"a\":1 \"b\":2}", "[1 2]", "{\"a\" 1}", "{\"a\"}", "{\"a\":}", "{\"a\":1]", "[1}", "{a:1}", "{'a':1}",
                "['a']", "{1:1}", "{} {}", "[1] x", "1 2", "{\"a\":1}}")) {
            test("json: refuses " + show(text), () -> refused(text));
        }
        for (String text : List.of("{\"a\":1,\"a\":2}", "{\"a\":1,\"\\u0061\":1}", "{\"o\":{\"k\":1,\"k\":2}}",
                "[{\"k\":1,\"k\":2}]")) {
            test("json: refuses the duplicate key in " + show(text), () -> refused(text));
        }
        test("json: keys that differ in letter case, or are in different objects, are not duplicates", () -> {
            expectEquals("{\"A\":2,\"a\":1}", canonical("{\"a\":1,\"A\":2}"));
            expectEquals("[{\"k\":1},{\"k\":2}]", canonical("[{\"k\":1},{\"k\":2}]"));
        });
        test("json: reads space, tab, line feed and carriage return between tokens", () -> expectEquals(
                "{\"a\":[1,2],\"b\":{}}", canonical(" \t\r\n{ \"a\" :\t[ 1 ,\r\n2 ] ,\n\"b\": { } }\r\n")));
        for (char c : new char[] {'\f', 0x0b, 0x1c, 0xa0, 0x2028, 0x3000, 0xfeff}) {
            test("json: refuses U+%04X between tokens".formatted((int) c), () -> {
                refused(c + "{}");
                refused("{\"a\":" + c + "1}");
                refused("[1" + c + "]");
            });
        }
        test("json: a member's text is its string, number or boolean, and empty for anything else", () -> {
            PolicyCheck.Json.JObject o = parse("{\"s\":\"x\",\"n\":1.50,\"b\":true,\"z\":null,\"a\":[1],\"o\":{}}").asObject();
            expectEquals(List.of("x", "1.50", "true", "", "", "", ""),
                    Stream.of("s", "n", "b", "z", "a", "o", "absent").map(o::text).toList());
        });
        test("json: canonical text sorts the keys at every level and has no whitespace", () -> expectEquals(
                "{\"a\":[{\"x\":null,\"y\":false}],\"b\":\"\",\"c\":{}}",
                canonical("{\"c\": {}, \"b\": \"\", \"a\": [ {\"y\": false, \"x\": null} ]}")));
        test("json: canonical strings escape the quote, the backslash and control characters, and nothing else", () ->
                expectEquals("\"\\\"\\\\/\\u0008\\u000c\\n\\r\\t\\u0000\\u001f\u007f\u00e9\u2028\"",
                        PolicyCheck.Json.canonical(new PolicyCheck.Json.JString(
                                "\"\\/\b\f\n\r\t\0" + (char) 0x1f + "\u007f\u00e9\u2028"))));
    }

    static PolicyCheck.Json parse(String json) {
        return PolicyCheck.Json.parse(json);
    }

    static String canonical(String json) {
        return PolicyCheck.Json.canonical(parse(json));
    }

    /** The reader refuses the text with its own error, not by accident (an index or number format exception). */
    static void refused(String json) {
        PolicyCheck.Json value;
        try {
            value = parse(json);
        } catch (IllegalArgumentException e) {
            if (!(e instanceof NumberFormatException) && e.getMessage() != null && e.getMessage().startsWith("JSON ")) {
                return;
            }
            throw new AssertionError("refused " + show(json) + " only by accident: " + e);
        } catch (RuntimeException e) {
            throw new AssertionError("refused " + show(json) + " only by accident: " + e);
        }
        throw new AssertionError("accepted " + show(json) + " as " + show(PolicyCheck.Json.canonical(value)));
    }

    /** Refused at the top level, as an array element and as a member value, before the end, ',', ']' and '}'. */
    static void refusedAnywhere(String token) {
        for (String json : List.of(token, "[" + token + "]", "[" + token + ",0]", "{\"n\":" + token + "}",
                "{\"n\":" + token + ",\"m\":0}")) {
            refused(json);
        }
    }

    // ---- the allowlist: every coordinate approved, reviewed and unexpired ----

    static void allowlist() {
        test("allowlist: an approved, reviewed, unexpired coordinate passes", () -> expectClean(Repo.clean().findings()));
        test("allowlist: APPROVE-WITH-CONDITIONS approves, and a review that expires today still passes", () -> {
            Repo repo = Repo.clean();
            repo.lib().put("verdict", "APPROVE-WITH-CONDITIONS");
            repo.lib().put("expiresAt", TODAY.toString());
            expectClean(repo.findings());
        });
        test("allowlist: an unreviewed coordinate fails", () -> {
            Repo repo = Repo.clean().append("gradle.lockfile", "org.example:other:2.0.0=runtimeClasspath\n");
            expectOnly(repo.findings(), "gradle", "org.example:other 2.0.0 (in gradle.lockfile) has no review on the allowlist");
        });
        test("allowlist: a review of another version or another kind does not cover a coordinate", () -> {
            Repo version = Repo.clean().edit("gradle.lockfile", "org.example:lib:1.0.0=", "org.example:lib:1.0.1=");
            expectOnly(version.findings(), "gradle", "org.example:lib 1.0.1 (in gradle.lockfile) has no review on the allowlist");
            Repo kind = Repo.clean();
            kind.lib().put("kind", "npm");
            expectOnly(kind.findings(), "gradle", "org.example:lib 1.0.0 (in gradle.lockfile) has no review on the allowlist");
        });
        test("allowlist: a rejected coordinate fails and names its review", () -> {
            Repo repo = Repo.clean();
            repo.lib().put("verdict", "REJECT");
            expectOnly(repo.findings(), "gradle",
                    "org.example:lib 1.0.0 (in gradle.lockfile) was REJECTED in security/reviews/maven--org.example-lib--1.0.0.md");
        });
        test("allowlist: an expired review fails", () -> {
            Repo repo = Repo.clean();
            repo.lib().put("expiresAt", TODAY.minusDays(1).toString());
            expectOnly(repo.findings(), "allowlist", "org.example:lib 1.0.0: its review expired on 2026-11-06");
        });
        test("allowlist: an expiry that is not a date fails", () -> {
            Repo repo = Repo.clean();
            repo.lib().put("expiresAt", "2026-13-01");
            expectOnly(repo.findings(), "allowlist", "org.example:lib 1.0.0: expiresAt '2026-13-01' is not a date");
        });
        test("allowlist: an unknown verdict fails, in any letter case", () -> {
            for (String verdict : List.of("APPROVED", "approve", "")) {
                Repo repo = Repo.clean();
                repo.lib().put("verdict", verdict);
                expectOnly(repo.findings(), "allowlist", "org.example:lib 1.0.0 has an unknown verdict '" + verdict + "'");
            }
        });
        test("allowlist: a review record that is missing, blank or not a file fails", () -> {
            for (String record : List.of("security/reviews/missing.md", "", "security/reviews")) {
                Repo repo = Repo.clean();
                repo.lib().put("reviewRecord", record);
                expectOnly(repo.findings(), "allowlist", "org.example:lib 1.0.0: review record '" + record + "' does not exist");
            }
        });
        // Each edit leaves an allowlist the first reader took for valid, with a value the check then read as text.
        for (String[] edit : new String[][] {{"\"schemaVersion\":1", "\"schemaVersion\":+"},
                {"\"version\":\"1.0.0\"", "\"version\":1.0.0"}, {"\"expiresAt\":\"2099-12-31\"", "\"expiresAt\":2099-12-31"},
                {"\"verdict\":\"APPROVE\"", "\"verdict\":\"REJECT\",\"verdict\":\"APPROVE\""}}) {
            test("allowlist: " + edit[1] + " is not JSON, and the check stops at its place in the file", () -> {
                Repo repo = Repo.clean().save().edit("security/allowlist.json", edit[0], edit[1]);
                IOException e = expectThrows(IOException.class, () -> new PolicyCheck(repo.root, TODAY));
                expect(e.getMessage().startsWith("security/allowlist.json:1:") && e.getMessage().contains(": JSON "),
                        e.toString());
            });
        }
    }

    // ---- the owner's acceptance of exactly the reviewed set ----

    static void acceptance() {
        test("acceptance: a set the owner has not accepted fails", () -> {
            Repo repo = Repo.clean();
            repo.acceptance.put("status", "pending");
            expectOnly(repo.findings(), "acceptance",
                    "the owner has not accepted the reviewed set (acceptance.status is 'pending')");
        });
        test("acceptance: an acceptance that records no digest fails", () -> {
            Repo repo = Repo.clean();
            repo.acceptance.put("reviewedSetSha256", null);
            expectOnly(repo.findings(), "acceptance", "the reviewed set changed after the owner accepted it", "accepted none");
        });
        test("acceptance: an allowlist without an acceptance record stops the check", () -> {
            Repo repo = Repo.clean();
            repo.acceptance = null;
            IOException e = expectThrows(IOException.class, () -> repo.check().run());
            expectEquals("security/allowlist.json: missing JSON member 'acceptance'", e.getMessage());
        });
        test("acceptance: a review record edited after the acceptance fails", () -> {
            Repo repo = Repo.clean().accept();
            repo.append((String) repo.lib().get("reviewRecord"), "Condition: none.\n");
            expectOnly(repo.findings(), "acceptance", "the reviewed set changed after the owner accepted it");
        });
        test("acceptance: a field the checker does not enforce, changed after the acceptance, fails", () -> {
            Repo repo = Repo.clean().accept();
            repo.lib().put("conditions", List.of("test scope only"));
            expectOnly(repo.findings(), "acceptance", "the reviewed set changed after the owner accepted it");
        });
        test("acceptance: an entry added after the acceptance fails", () -> {
            Repo repo = Repo.clean().accept();
            repo.entry("npm", "example-pkg", "1.0.0", "example-pkg-1.0.0.tgz", null);
            expectOnly(repo.findings(), "acceptance", "the reviewed set changed after the owner accepted it");
        });
        test("acceptance: an entry removed after the acceptance fails", () -> {
            Repo repo = Repo.clean();
            repo.entry("npm", "example-pkg", "1.0.0", "example-pkg-1.0.0.tgz", null);
            repo.accept().entries.removeLast();
            expectOnly(repo.findings(), "acceptance", "the reviewed set changed after the owner accepted it");
        });
        test("acceptance: the digest does not depend on the order of entries or keys, or on whitespace", () -> {
            Repo repo = Repo.clean();
            repo.entry("npm", "example-pkg", "1.0.0", "example-pkg-1.0.0.tgz", null);
            repo.accept();
            Collections.reverse(repo.entries);
            repo.entries.replaceAll(PolicyCheckTest::reversedKeys);
            repo.pretty = true;
            expectClean(repo.findings());
        });
        test("acceptance: the digest is SHA-256 over the sorted lines 'canonical entry|SHA-256 of its review'", () -> {
            Repo repo = Repo.clean();
            repo.entry("npm", "example-pkg", "1.0.0", "example-pkg-1.0.0.tgz", null);
            List<String> lines = new ArrayList<>();
            for (Map<String, Object> entry : repo.entries) {
                Path review = repo.root.resolve((String) entry.get("reviewRecord"));
                lines.add(json(new TreeMap<>(entry), false) + "|" + sha256(Files.readAllBytes(review)));
            }
            Collections.sort(lines);
            expectEquals(sha256(utf8(String.join("\n", lines))), repo.check().entriesDigest());
        });
    }

    // ---- Gradle: lockfiles and verification metadata ----

    static void gradle() {
        test("gradle: an unreadable lockfile line fails by its number, with or without '=', never by its content", () -> {
            for (String line : List.of("org.example:lib=runtimeClasspath", "org.example:lib:1.0.0:extra=runtimeClasspath",
                    "org.example:other:2.0.0", MARKER)) {
                List<String> findings = Repo.clean().append("gradle.lockfile", line + "\n").findings();
                expectOnly(findings, "gradle", "gradle.lockfile:4: " + UNREADABLE_LINE);
                expect(!findings.getFirst().contains(line), "the finding repeats the line: " + findings);
            }
        });
        test("gradle: lockfiles of nested projects are checked", () -> {
            Repo repo = Repo.clean().write("libs/demo/gradle.lockfile", "org.example:other:2.0.0=runtimeClasspath\n");
            expectOnly(repo.findings(), "gradle", "org.example:other 2.0.0 (in libs/demo/gradle.lockfile) has no review");
        });
        test("gradle: lockfiles without gradle/verification-metadata.xml fail", () -> {
            Repo repo = Repo.clean();
            Files.delete(repo.root.resolve("gradle/verification-metadata.xml"));
            expectOnly(repo.findings(), "gradle", "gradle/verification-metadata.xml is missing");
        });
        test("gradle: verification metadata that does not verify metadata fails", () -> {
            Repo repo = Repo.clean().edit("gradle/verification-metadata.xml", ">true<", ">false<");
            expectOnly(repo.findings(), "gradle", "verification-metadata.xml does not verify metadata");
        });
        test("gradle: a jar without a SHA-256 in the verification metadata fails", () -> {
            Repo repo = Repo.clean().edit("gradle/verification-metadata.xml", "<sha256 value=\"" + SHA_A + "\"/>", "");
            expectOnly(repo.findings(), "gradle", "org.example:lib:1.0.0 lib-1.0.0.jar has no SHA-256");
        });
        test("gradle: a jar whose SHA-256 differs from its review fails", () -> {
            Repo repo = Repo.clean().edit("gradle/verification-metadata.xml", SHA_A, SHA_B);
            expectOnly(repo.findings(), "gradle", "org.example:lib:1.0.0 lib-1.0.0.jar: downloaded SHA-256 " + SHA_B
                    + " differs from the reviewed " + SHA_A);
        });
        test("gradle: a jar SHA-256 matches its review in either letter case", () ->
                expectClean(Repo.clean().edit("gradle/verification-metadata.xml", SHA_A, SHA_A.toUpperCase()).findings()));
        test("gradle: verification metadata with a doctype declaration stops the check", () -> {
            Repo repo = Repo.clean().edit("gradle/verification-metadata.xml", "<verification-metadata>",
                    "<!DOCTYPE verification-metadata [<!ENTITY x \"y\">]>\n<verification-metadata>");
            IOException e = expectThrows(IOException.class, () -> repo.check().run());
            expect(e.getMessage().startsWith("gradle/verification-metadata.xml:2:") && e.getMessage().contains("unreadable XML"),
                    e.toString());
        });
    }

    // ---- the Gradle wrapper ----

    /** The clean repository with a Gradle wrapper whose distribution and JAR are reviewed. */
    static Repo wrapped() throws Exception {
        Repo repo = Repo.clean().write("gradle/wrapper/gradle-wrapper.properties", """
                distributionSha256Sum=%s
                distributionUrl=https\\://example.org/distributions/gradle-9.9.9-bin.zip
                """.formatted(SHA_B)).write("gradle/wrapper/gradle-wrapper.jar", WRAPPER_JAR);
        repo.entry("gradle-distribution", "gradle", "9.9.9", "gradle-9.9.9-bin.zip", SHA_B);
        repo.entry("gradle-distribution", "gradle", "9.9.9", "gradle/wrapper/gradle-wrapper.jar (gradle-9.9.9-wrapper.jar)",
                sha256(WRAPPER_JAR));
        return repo;
    }

    static void wrapper() {
        test("wrapper: the reviewed distribution and wrapper JAR pass", () -> expectClean(wrapped().findings()));
        test("wrapper: a wrapper JAR that matches no approved review fails", () -> {
            byte[] other = utf8("another wrapper jar\n");
            Repo repo = wrapped().write("gradle/wrapper/gradle-wrapper.jar", other);
            expectOnly(repo.findings(), "wrapper",
                    "gradle-wrapper.jar SHA-256 " + sha256(other) + " matches no approved wrapper review");
        });
        test("wrapper: a distribution checksum that differs from its review fails", () -> {
            Repo repo = wrapped().edit("gradle/wrapper/gradle-wrapper.properties", SHA_B, SHA_A);
            expectOnly(repo.findings(), "wrapper", "distributionSha256Sum " + SHA_A + " differs from the reviewed " + SHA_B);
        });
        test("wrapper: a wrapper without distributionSha256Sum fails", () -> {
            Repo repo = wrapped().edit("gradle/wrapper/gradle-wrapper.properties", "distributionSha256Sum=" + SHA_B, "");
            expectFinding(repo.findings(), "wrapper", "gradle-wrapper.properties has no distributionSha256Sum");
        });
        test("wrapper: a distribution that is not on the allowlist, or was rejected, fails", () -> {
            Repo unlisted = wrapped().edit("gradle/wrapper/gradle-wrapper.properties", "9.9.9-bin", "9.9.8-bin");
            expectOnly(unlisted.findings(), "wrapper", "gradle-9.9.8-bin.zip is not an approved Gradle distribution");
            Repo rejected = wrapped();
            rejected.entries.get(1).put("verdict", "REJECT");
            expectOnly(rejected.findings(), "wrapper", "gradle-9.9.9-bin.zip is not an approved Gradle distribution");
        });
        test("wrapper: a missing wrapper JAR stops the check", () -> {
            Repo repo = wrapped();
            Files.delete(repo.root.resolve("gradle/wrapper/gradle-wrapper.jar"));
            expectThrows(IOException.class, () -> repo.check().run());
        });
    }

    // ---- GitHub Actions ----

    /** The clean repository with a reviewed Action and a workflow whose eighth line uses the given reference. */
    static Repo withWorkflow(String uses) throws IOException {
        Repo repo = Repo.clean().write(".github/workflows/ci.yml", """
                name: ci
                on: push
                jobs:
                  build:
                    runs-on: ubuntu-24.04
                    steps:
                      - uses: ./.github/actions/local
                      - uses: %s
                      - run: echo "uses: is only text here"
                """.formatted(uses));
        repo.entry("github-action", "example-org/example-action", "1.0.0", COMMIT, null);
        return repo;
    }

    static void actions() {
        for (String uses : List.of("example-org/example-action@" + COMMIT + " # v1.0.0",
                "'example-org/example-action@" + COMMIT + "'", "\"example-org/example-action/sub/dir@" + COMMIT + "\"")) {
            test("actions: an Action pinned to its allowlisted commit passes: " + uses,
                    () -> expectClean(withWorkflow(uses).findings()));
        }
        for (String uses : List.of("example-org/example-action@v1", "example-org/example-action@main",
                "example-org/example-action@" + COMMIT.substring(0, 7), "example-org/example-action")) {
            test("actions: an Action not pinned to a full commit SHA fails: " + uses, () -> expectOnly(
                    withWorkflow(uses).findings(), "actions", ".github/workflows/ci.yml:8: " + uses + " is not pinned"));
        }
        test("actions: a commit or a repository that is not on the allowlist fails", () -> {
            String other = "f".repeat(40);
            expectOnly(withWorkflow("example-org/example-action@" + other).findings(), "actions",
                    "example-org/example-action@" + other + " is not on the allowlist");
            expectOnly(withWorkflow("other-org/example-action@" + COMMIT).findings(), "actions",
                    "other-org/example-action@" + COMMIT + " is not on the allowlist");
        });
        test("actions: an Action whose review was rejected fails", () -> {
            Repo repo = withWorkflow("example-org/example-action@" + COMMIT);
            repo.entries.getLast().put("verdict", "REJECT");
            expectOnly(repo.findings(), "actions", "example-org/example-action@" + COMMIT + " is not on the allowlist");
        });
        test("actions: container actions fail", () -> expectOnly(withWorkflow("docker://example.org/tool:1.0").findings(),
                "actions", "container actions are not allowed (docker://example.org/tool:1.0)"));
        test("actions: workflows named .yaml are checked too", () -> {
            Repo repo = withWorkflow("example-org/example-action@" + COMMIT).write(".github/workflows/other.yaml",
                    "jobs:\n  x:\n    steps:\n      - uses: example-org/example-action@v1\n");
            expectOnly(repo.findings(), "actions", ".github/workflows/other.yaml:4: example-org/example-action@v1 is not pinned");
        });
    }

    // ---- npm ----

    /** The clean repository with a package-lock.json of reviewed packages: plain, scoped and nested, and a link. */
    static Repo withNpm() throws IOException {
        Map<String, Object> packages = map("", map("name", "demo", "version", "0.0.0"),
                "node_modules/example-pkg", npmPackage("example-pkg", "1.0.0"),
                "node_modules/@example/scoped-pkg", npmPackage("@example/scoped-pkg", "2.0.0"),
                "node_modules/example-pkg/node_modules/nested-pkg", npmPackage("nested-pkg", "3.0.0"),
                "node_modules/local-workspace", map("resolved", "packages/local-workspace", "link", true),
                "packages/local-workspace", map("version", "0.0.0"));
        Repo repo = Repo.clean().write("package-lock.json",
                json(map("name", "demo", "lockfileVersion", 3, "requires", true, "packages", packages), true));
        repo.entry("npm", "example-pkg", "1.0.0", "https://registry.npmjs.org/example-pkg/-/example-pkg-1.0.0.tgz", null);
        repo.entry("npm", "@example/scoped-pkg", "2.0.0", "https://registry.npmjs.org/@example/scoped-pkg/-/scoped-pkg-2.0.0.tgz", null);
        repo.entry("npm", "nested-pkg", "3.0.0", "https://registry.npmjs.org/nested-pkg/-/nested-pkg-3.0.0.tgz", null);
        return repo;
    }

    static Map<String, Object> npmPackage(String name, String version) {
        String file = name.substring(name.lastIndexOf('/') + 1) + "-" + version + ".tgz";
        return map("version", version, "resolved", "https://registry.npmjs.org/" + name + "/-/" + file,
                "integrity", "sha512-" + name + "-integrity");
    }

    static void npm() {
        test("npm: reviewed packages from registry.npmjs.org with an integrity hash pass", () -> expectClean(withNpm().findings()));
        for (String host : List.of("https://registry.example.org/", "http://registry.npmjs.org/",
                "https://registry.npmjs.org.example.org/")) {
            test("npm: a package resolved from " + host + " fails", () -> {
                Repo repo = withNpm().edit("package-lock.json", "https://registry.npmjs.org/example-pkg/", host + "example-pkg/");
                expectOnly(repo.findings(), "npm", "package-lock.json example-pkg@1.0.0 is not resolved from registry.npmjs.org");
            });
        }
        test("npm: a package without a sha512 integrity hash fails", () -> {
            Repo repo = withNpm().edit("package-lock.json", "sha512-example-pkg-", "sha1-example-pkg-");
            expectOnly(repo.findings(), "npm", "package-lock.json example-pkg@1.0.0 has no sha512 integrity hash");
        });
        test("npm: an unreviewed version fails", () -> {
            Repo repo = withNpm().edit("package-lock.json", "\"3.0.0\"", "\"3.0.1\"");
            expectOnly(repo.findings(), "npm", "nested-pkg 3.0.1 (in package-lock.json) has no review on the allowlist");
        });
        test("npm: a package-lock.json that is not JSON stops the check", () -> {
            Repo repo = withNpm().edit("package-lock.json", "\"lockfileVersion\" : 3", "\"lockfileVersion\" : +3");
            IOException e = expectThrows(IOException.class, () -> repo.check().run());
            expectEquals("package-lock.json:3:23: JSON unexpected character", e.getMessage());
        });
    }

    // ---- vendored files ----

    static Repo withVendored() throws Exception {
        Repo repo = Repo.clean().write("site/vendor/widget/1.0.0/widget.min.js", WIDGET);
        repo.entry("vendored-js", "widget", "1.0.0", "widget.min.js", sha256(WIDGET));
        return repo;
    }

    static void vendored() {
        test("vendored: a file identical to its approved, reviewed artifact passes", () -> expectClean(withVendored().findings()));
        test("vendored: a file that differs by one byte fails", () -> {
            byte[] changed = WIDGET.clone();
            changed[3] ^= 1;
            Repo repo = withVendored().write("site/vendor/widget/1.0.0/widget.min.js", changed);
            expectOnly(repo.findings(), "vendored", "site/vendor/widget/1.0.0/widget.min.js (SHA-256 " + sha256(changed)
                    + ") matches no approved vendored artifact");
        });
        test("vendored: a file whose review was rejected, or that only a non-vendored entry matches, fails", () -> {
            for (String[] change : new String[][] {{"verdict", "REJECT"}, {"kind", "maven"}}) {
                Repo repo = withVendored();
                repo.entries.getLast().put(change[0], change[1]);
                expectOnly(repo.findings(), "vendored", "site/vendor/widget/1.0.0/widget.min.js", "matches no approved");
            }
        });
        test("vendored: an unreviewed file next to a reviewed one fails", () -> {
            Repo repo = withVendored().write("site/vendor/widget/1.0.0/extra.js", utf8("/* not reviewed */\n"));
            expectOnly(repo.findings(), "vendored", "site/vendor/widget/1.0.0/extra.js", "matches no approved");
        });
    }

    // ---- container images: only approved digests, only in the forms the reviews allow ----

    /** The approved image's index digest, the rejected one's and one that no entry has (all synthetic). */
    static final String POSTGRES = "a1".repeat(32);
    static final String RYUK = "b2".repeat(32);
    static final String UNLISTED = "c3".repeat(32);
    /** The database harness: the one source that may use Testcontainers, so the fixtures put their test code there. */
    static final String HARNESS = "apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/ReviewedPostgres.java";
    static final String DATABASE_TEST = HARNESS;
    static final String OTHER_TEST = "apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/OtherTest.java";
    static final String WORKFLOW = ".github/workflows/ci.yml";
    static final String STAGING = "tools/ci/stage-image.sh";

    /**
     * The clean repository with an approved postgres image and a rejected ryuk image on the allowlist, a test that
     * starts postgres in the reviewed form, and a script that stages it by digest.
     */
    static Repo withImages() throws IOException {
        Repo repo = Repo.clean();
        repo.entry("container-image", "docker.io/library/postgres", "18.6", "OCI image index", POSTGRES);
        repo.entry("container-image", "docker.io/testcontainers/ryuk", "0.14.0", "manifest list", RYUK)
                .put("verdict", "REJECT");
        return repo.write(DATABASE_TEST, database("DockerImageName.parse(\"postgres@sha256:" + POSTGRES + "\")",
                "new PostgreSQLContainer(IMAGE)")).write(STAGING, staging("docker pull --quiet postgres@sha256:" + POSTGRES));
    }

    /** A test class: its seventh line declares the image, its eighth constructs the container. */
    static String database(String image, String container) {
        return """
                package demo;

                import org.testcontainers.postgresql.PostgreSQLContainer;
                import org.testcontainers.utility.DockerImageName;

                class DatabaseTest {
                    static final DockerImageName IMAGE = %s;
                    final PostgreSQLContainer postgres = %s;
                }
                """.formatted(image, container);
    }

    /** A staging script whose fourth line is the given command. */
    static String staging(String command) {
        return """
                #!/usr/bin/env bash
                # Stages the reviewed image; the docker group and a docker run in this comment are only text.
                set -euo pipefail
                %s
                docker image inspect --format '{{json .RepoDigests}}' postgres@sha256:%s
                sudo systemctl restart docker
                """.formatted(command, POSTGRES);
    }

    static void images() {
        test("images: an approved image by digest, in the reviewed form, passes", () ->
                expectClean(withImages().findings()));
        for (String name : List.of("docker.io/library/postgres", "library/postgres", "index.docker.io/library/postgres")) {
            test("images: the approved digest spelled " + name + " passes", () -> expectClean(withImages()
                    .write(DATABASE_TEST, database("DockerImageName.parse(\"" + name + "@sha256:" + POSTGRES + "\")",
                            "new PostgreSQLContainer(IMAGE)")).findings()));
        }
        test("images: a digest that is not on the allowlist fails, by file and line", () -> expectOnly(withImages()
                .edit(DATABASE_TEST, POSTGRES, UNLISTED).findings(), "images",
                DATABASE_TEST + ":7: postgres@sha256:" + UNLISTED + " has no review on the allowlist"));
        for (String name : List.of("mysql", "registry.example.org/library/postgres", "ghcr.io/library/postgres",
                "docker.io/example/postgres")) {
            test("images: the approved digest under the name " + name + " fails", () -> expectOnly(withImages()
                    .edit(DATABASE_TEST, "\"postgres@", "\"" + name + "@").findings(), "images",
                    DATABASE_TEST + ":7: " + name + "@sha256:" + POSTGRES + " has no review on the allowlist"));
        }
        test("images: a rejected image fails and names its review", () -> expectOnly(withImages()
                .edit(DATABASE_TEST, "\"postgres@sha256:" + POSTGRES, "\"testcontainers/ryuk@sha256:" + RYUK).findings(),
                "images", "testcontainers/ryuk@sha256:" + RYUK
                        + " was REJECTED in security/reviews/container-image--docker.io-testcontainers-ryuk--0.14.0.md"));
        test("images: an image named by tag only fails", () -> expectOnly(withImages()
                .edit(DATABASE_TEST, "postgres@sha256:" + POSTGRES, "postgres:18.6").findings(), "images",
                DATABASE_TEST + ":7: postgres:18.6 is not pinned by digest"));
        test("images: a tag next to the digest fails", () -> expectOnly(withImages()
                .edit(DATABASE_TEST, "postgres@sha256:", "postgres:18.6@sha256:").findings(), "images",
                DATABASE_TEST + ":7: postgres:18.6@sha256:" + POSTGRES + " names a tag next to its digest"));
        for (String digest : List.of(POSTGRES.toUpperCase(), POSTGRES.substring(1), POSTGRES + "0", "")) {
            test("images: a digest that is not 64 lowercase hexadecimal digits fails: " + show(digest), () ->
                    expectOnly(withImages().edit(DATABASE_TEST, POSTGRES, digest).findings(), "images",
                            DATABASE_TEST + ":7: ", "is not a sha256 digest of 64 lowercase hexadecimal digits"));
        }
        for (String image : List.of("DockerImageName.parse(NAME)", "DockerImageName.parse(\"postgres@sha256:\" + DIGEST)",
                "DockerImageName.parse(\"postgres\", \"18.6\")")) {
            test("images: DockerImageName.parse of anything but one string literal fails: " + image, () ->
                    expectFinding(withImages().write(DATABASE_TEST, database(image, "new PostgreSQLContainer(IMAGE)"))
                            .findings(), "images", DATABASE_TEST + ":7: DockerImageName.parse takes something other "
                            + "than one string literal"));
        }
        for (String image : List.of("DockerImageName::parse", "org.testcontainers.utility.DockerImageName::parse")) {
            test("images: DockerImageName::parse as a method reference fails: " + image, () -> expectFinding(withImages()
                    .write(DATABASE_TEST, database(image, "new PostgreSQLContainer(IMAGE)")).findings(), "images",
                    DATABASE_TEST + ":7: DockerImageName::parse"));
        }
        test("images: a static import of DockerImageName.parse fails", () -> expectFinding(withImages()
                .edit(DATABASE_TEST, "import org.testcontainers.utility.DockerImageName;",
                        "import static org.testcontainers.utility.DockerImageName.parse;").findings(), "images",
                DATABASE_TEST + ":4: a static import of DockerImageName"));
        for (String container : List.of("new PostgreSQLContainer(\"postgres:18.6\")", "new PostgreSQLContainer()",
                "new PostgreSQLContainer(name)", "new PostgreSQLContainer(IMAGE.toString())",
                "new PostgreSQLContainer(IMAGE) { }")) {
            test("images: a PostgreSQLContainer made from anything but a parsed digest fails: " + container, () ->
                    expectFinding(withImages().write(DATABASE_TEST, database("DockerImageName.parse(\"postgres@sha256:"
                            + POSTGRES + "\")", container)).findings(), "images", DATABASE_TEST + ":8: "));
        }
        test("images: a PostgreSQLContainer made from DockerImageName.parse of the approved digest passes", () ->
                expectClean(withImages().write(DATABASE_TEST, database("DockerImageName.parse(\"postgres@sha256:"
                        + POSTGRES + "\")", "new org.testcontainers.postgresql.PostgreSQLContainer(DockerImageName.parse("
                        + "\"postgres@sha256:" + POSTGRES + "\"))")).findings()));
        for (String container : List.of("new GenericContainer<>(IMAGE)", "new FixedHostPortGenericContainer(IMAGE)",
                "new org.testcontainers.containers.GenericContainer<>(IMAGE)", "PostgreSQLContainer::new")) {
            test("images: a container class other than the reviewed PostgreSQLContainer fails: " + container, () ->
                    expectFinding(withImages().write(DATABASE_TEST, database("DockerImageName.parse(\"postgres@sha256:"
                            + POSTGRES + "\")", container)).findings(), "images", DATABASE_TEST + ":8: "));
        }
        test("images: the deprecated org.testcontainers.containers.PostgreSQLContainer fails", () -> expectFinding(
                withImages().edit(DATABASE_TEST, "org.testcontainers.postgresql.PostgreSQLContainer",
                        "org.testcontainers.containers.PostgreSQLContainer").findings(), "images",
                DATABASE_TEST + ":3: org.testcontainers.containers.PostgreSQLContainer"));
        test("images: a class that extends a container fails", () -> expectFinding(withImages().edit(DATABASE_TEST,
                "class DatabaseTest {", "class DatabaseTest extends PostgreSQLContainer {").findings(), "images",
                DATABASE_TEST + ":6: extends PostgreSQLContainer"));
        for (String call : List.of("IMAGE.withTag(\"18\")", "IMAGE.withRegistry(\"registry.example.org\")",
                "IMAGE.withRepository(\"other\")", "IMAGE.asCompatibleSubstituteFor(\"postgres\")",
                "new DockerImageName(\"postgres\")", "new ImageFromDockerfile()", "new RemoteDockerImage(IMAGE)")) {
            test("images: an image name derived, built or resolved in code fails: " + call, () -> expectFinding(
                    withImages().edit(DATABASE_TEST, "    final PostgreSQLContainer postgres",
                            "    final Object other = " + call + ";\n    final PostgreSQLContainer postgres").findings(),
                    "images", DATABASE_TEST + ":8: "));
        }
        for (String call : List.of("withFileSystemBind(\"/tmp\", \"/x\")", "withClasspathResourceMapping(\"a\", \"/b\", null)",
                "withCopyToContainer(null, \"/x\")", "withCopyFileToContainer(null, \"/x\")", "copyFileToContainer(null, \"/x\")",
                "withAccessToHost(true)", "withPrivilegedMode(true)", "withReuse(true)", "withNetworkMode(\"host\")",
                "withCreateContainerCmdModifier(cmd -> { })", "withVolumesFrom(null, null)")) {
            test("images: a container feature the reviews forbid fails: " + call, () -> expectFinding(withImages()
                    .edit(DATABASE_TEST, "new PostgreSQLContainer(IMAGE)", "new PostgreSQLContainer(IMAGE)." + call)
                    .findings(), "images", DATABASE_TEST + ":8: "));
        }
        for (String call : List.of("Testcontainers.exposeHostPorts(5432)", "new DockerComposeContainer(null)",
                "new ComposeContainer(null)", "client.createContainerCmd(\"alpine\")", "client.pullImageCmd(\"alpine\")",
                "client.buildImageCmd()", "client.loadImageCmd(null)", "client.copyArchiveToContainerCmd(\"x\")",
                "factory.runInsideDocker(null, null)", "factory.checkAndPullImage(client, \"alpine\")")) {
            test("images: a Testcontainers or docker-java call that pulls, builds, copies or runs another container fails: "
                    + call, () -> expectFinding(withImages().edit(DATABASE_TEST, "    final PostgreSQLContainer postgres",
                    "    final Object other = " + call + ";\n    final PostgreSQLContainer postgres").findings(), "images",
                    DATABASE_TEST + ":8: "));
        }
        test("images: a Testcontainers JDBC URL fails, in code and in configuration", () -> {
            Repo repo = withImages().edit(DATABASE_TEST, "    final PostgreSQLContainer postgres",
                    "    final String url = \"jdbc:tc:postgresql:18.6:///shipping\";\n    final PostgreSQLContainer postgres")
                    .write("apps/demo/src/test/resources/application.yml",
                            "spring:\n  datasource:\n    url: jdbc:tc:postgresql:18.6:///shipping\n");
            List<String> findings = repo.findings();
            expect(findings.size() == 2, findings.toString());
            expectFinding(findings, "images", DATABASE_TEST + ":8: a Testcontainers JDBC URL");
            expectFinding(findings, "images", "apps/demo/src/test/resources/application.yml:3: a Testcontainers JDBC URL");
        });
        for (String file : List.of("apps/demo/src/test/resources/testcontainers.properties", ".testcontainers.properties",
                "testcontainers.properties", "apps/demo/src/test/resources/META-INF/services/"
                        + "org.testcontainers.utility.ImageNameSubstitutor")) {
            test("images: a Testcontainers configuration file fails wherever it is: " + file, () -> expectOnly(
                    withImages().write(file, "# only a comment\n").findings(), "images",
                    file + ": a Testcontainers configuration file"));
        }
        for (String[] setting : new String[][] {
                {".github/workflows/ci.yml", "jobs:\n  x:\n    env:\n      TESTCONTAINERS_RYUK_CONTAINER_IMAGE: other\n", "4"},
                {"apps/demo/build.gradle.kts", "tasks.test { environment(\"TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX\", \"m/\") }\n", "1"},
                {"gradle.properties", "systemProp.image.substitutor=demo.Substitutor\n", "1"},
                {DATABASE_TEST, database("DockerImageName.parse(\"postgres@sha256:" + POSTGRES + "\")",
                        "new PostgreSQLContainer(IMAGE); final String key = \"tinyimage.container.image\"").strip(), "8"}}) {
            test("images: a Testcontainers image setting fails in " + setting[0], () -> expectOnly(withImages()
                    .write(setting[0], setting[1]).findings(), "images",
                    setting[0] + ":" + setting[2] + ": ", "configures an image for Testcontainers"));
        }
        test("images: a pull of anything but one digest fails, and an unapproved digest is named", () -> {
            for (String command : List.of("docker pull alpine:3.17", "docker image pull alpine", "docker pull \"$image\"",
                    "docker pull")) {
                expectOnly(withImages().write(STAGING, staging(command)).findings(), "images",
                        STAGING + ":4: docker pull of something other than one image by digest");
            }
            expectOnly(withImages().write(STAGING, staging("docker pull postgres@sha256:" + UNLISTED)).findings(),
                    "images", STAGING + ":4: postgres@sha256:" + UNLISTED + " has no review on the allowlist");
            expectClean(withImages().write(STAGING, staging("docker pull --platform linux/amd64 postgres@sha256:"
                    + POSTGRES)).findings());
        });
        for (String command : List.of("docker run --rm postgres@sha256:" + POSTGRES, "docker create alpine",
                "docker container run alpine", "docker build .", "docker buildx build .", "docker compose up",
                "docker login", "docker load --input image.tar", "docker image import image.tar", "docker exec x id",
                "docker cp file x:/tmp", "docker tag a b", "docker --host tcp://example.org:2375 info", "docker \"$command\"",
                "docker-compose up", "podman run alpine")) {
            test("images: a workflow or script runs no container, builds no image and logs in nowhere: " + command, () ->
                    expectOnly(withImages().write(STAGING, staging(command)).findings(), "images", STAGING + ":4: "));
        }
        test("images: the docker commands of the staging, the checks and the cleanup pass", () -> {
            for (String command : List.of("docker version --format '{{.Server.Version}}'", "docker info",
                    "docker image ls --digests", "docker images", "docker ps -aq --filter label=x",
                    "docker rm -f -v \"$id\"", "docker network ls -q", "docker network rm \"$id\"",
                    "docker events --since 1 --until 2", "docker inspect x", "docker manifest inspect --verbose postgres@sha256:"
                            + POSTGRES, "echo ok # docker run is only a comment here")) {
                expectClean(withImages().write(STAGING, staging(command)).findings());
            }
        });
        test("images: a workflow with a job or service container fails", () -> {
            for (String key : List.of("container", "services")) {
                Repo repo = withImages().write(".github/workflows/ci.yml",
                        "jobs:\n  x:\n    runs-on: ubuntu-24.04\n    " + key + ":\n      image: alpine\n");
                expectOnly(repo.findings(), "images", ".github/workflows/ci.yml:4: a job or service container");
            }
        });
        test("images: a build script or a test that runs a container runtime as a command fails", () -> {
            expectOnly(withImages().write("apps/demo/build.gradle.kts",
                    "tasks.register<Exec>(\"pull\") { commandLine(\"docker\", \"pull\", \"alpine\") }\n").findings(),
                    "images", "apps/demo/build.gradle.kts:1: runs a container runtime as a command");
            expectOnly(withImages().edit(DATABASE_TEST, "    final PostgreSQLContainer postgres",
                    "    final ProcessBuilder pull = new ProcessBuilder(\"podman\", \"pull\");\n    final PostgreSQLContainer postgres")
                    .findings(), "images", DATABASE_TEST + ":8: runs a container runtime as a command");
        });
        test("images: a reference in documents, reviews and the checker's own tests is no use of an image", () -> {
            Repo repo = withImages().write("docs/notes.md", "Ryuk would be testcontainers/ryuk@sha256:" + RYUK
                    + "; docker run alpine:3.17 and jdbc:tc:postgresql:18.6:///x are rejected.\n")
                    .write("tools/policy/Example.java", "class Example { String s = \"alpine@sha256:" + UNLISTED + "\"; }\n");
            expectClean(repo.findings());
        });
        test("images: a file that is not Testcontainers code may name its own classes Container", () ->
                expectClean(withImages().write("apps/demo/src/main/java/demo/Parcel.java",
                        "package demo;\n\nrecord Parcel(ShippingContainer container) {\n"
                                + "    Parcel { container = new ShippingContainer(\"blue\"); }\n}\n").findings()));
        test("images: markup in an image reference is printed as safe characters", () -> {
            List<String> findings = withImages().edit(DATABASE_TEST, "\"postgres@sha256:" + POSTGRES,
                    "\"**md**_x/postgres@sha256:" + UNLISTED).findings();
            expectOnly(findings, "images", "??md???x/postgres@sha256:" + UNLISTED + " has no review on the allowlist");
            expect(!findings.getFirst().contains("**"), findings.toString());
        });
    }

    /** A workflow whose fifth line is the given text, inside a job. */
    static String workflow(String fifth) {
        return """
                name: ci
                on: push
                jobs:
                  x:
                %s
                    steps:
                      - run: echo "the test containers are removed"
                """.formatted(fifth);
    }

    /** Ordinary shell and YAML spellings that name what the check refuses, each of which got through it once. */
    static void imageBypasses() {
        for (String command : List.of("/usr/bin/docker run alpine:3.17", "\"docker\" run alpine:3.17",
                "'docker' run alpine:3.17", "\"/usr/bin/docker\" \"run\" alpine:3.17", "sudo /usr/bin/docker run alpine:3.17",
                "/usr/bin/docker login", "/usr/bin/docker cp file x:/tmp", "/usr/local/bin/docker build .",
                "/usr/bin/docker run --network host -v /:/host postgres@sha256:" + POSTGRES, "exec docker run alpine",
                "env docker run alpine", "$(command -v docker) run alpine", "runtime=docker", "\"docker\"")) {
            test("bypasses: a script that names docker this way is refused: " + command, () -> expectFinding(
                    withImages().write(STAGING, staging(command)).findings(), "images", STAGING + ":4: "));
        }
        test("bypasses: a workflow step that runs docker by its path is refused", () -> expectOnly(withImages()
                .write(WORKFLOW, workflow("    steps:\n      - run: /usr/bin/docker run alpine:3.17").replace(
                        "    steps:\n      - run: echo", "      - run: echo")).findings(), "images",
                WORKFLOW + ":6: docker run is not an allowed use"));
        test("bypasses: restarting the docker service is no docker command", () -> expectClean(withImages()
                .write(STAGING, staging("sudo systemctl restart docker")).findings()));
        for (String key : List.of("    \"container\": alpine:3.17", "    'container': alpine:3.17",
                "    \"services\":", "    'services': {db: {image: alpine}}", "    {container: alpine}",
                "    runs-on: ubuntu-24.04 # then\n    defaults: {run: {shell: bash}, container: alpine}",
                "    \"cont\\u0061iner\": alpine", "    ? container\n    : alpine")) {
            test("bypasses: a job or service container in another YAML spelling is refused: " + show(key), () ->
                    expectFinding(withImages().write(WORKFLOW, workflow(key)).findings(), "images", WORKFLOW + ":"));
        }
        test("bypasses: a test that only has the harness's handle cannot reach the container's API", () -> expectFinding(
                withImages().write(OTHER_TEST, """
                        package io.github.stigjohnny.shipping.database;

                        class OtherTest {
                            void bind() {
                                var postgres = ReviewedPostgres.start();
                                postgres.withFileSystemBind("/", "/host");
                            }
                        }
                        """).findings(), "images", OTHER_TEST + ":6: withFileSystemBind is forbidden"));
        test("bypasses: a test other than the harness may not use Testcontainers, not even in the reviewed form", () ->
                expectFinding(withImages().write(OTHER_TEST, database("DockerImageName.parse(\"postgres@sha256:" + POSTGRES
                        + "\")", "new PostgreSQLContainer(IMAGE)").replace("package demo;",
                        "package io.github.stigjohnny.shipping.database;")).findings(), "images",
                        OTHER_TEST + ":3: ", "only ReviewedPostgres"));
        test("bypasses: a fully qualified docker-java call outside the harness is refused", () -> expectFinding(
                withImages().write(OTHER_TEST, "class OtherTest { Object c = com.github.dockerjava.api.model.Bind.parse(\"/:/h\"); }\n")
                        .findings(), "images", OTHER_TEST + ":1: ", "only ReviewedPostgres"));
        test("bypasses: a unicode escape does not hide Testcontainers from the check", () -> expectFinding(
                withImages().write(OTHER_TEST, "import \\u006frg.testcontainers.containers.GenericContainer;\n"
                        + "class OtherTest { }\n").findings(), "images", OTHER_TEST + ":1: ", "only ReviewedPostgres"));
        for (String script : List.of(
                "tasks.register(\"image\") { doLast { org.testcontainers.utility.DockerImageName.parse(\"alpine:3.17\") } }\n",
                "val image = DockerImageName.parse(\"alpine:3.17\")\n",
                "tasks.register<Exec>(\"pull\") { commandLine(\"sh\", \"-c\", \"docker run alpine:3.17\") }\n",
                "tasks.register<Exec>(\"pull\") { commandLine(\"/usr/bin/docker\", \"run\", \"alpine\") }\n",
                "dependencies { testImplementation(\"org.testcontainers:testcontainers-postgresql\") }\n"
                        + "val pg = GenericContainer<Nothing>(\"alpine\").withFileSystemBind(\"/\", \"/h\")\n")) {
            test("bypasses: a build script is read like test code: " + show(script.strip()), () -> expectFinding(
                    withImages().write("apps/demo/build.gradle.kts", script).findings(), "images",
                    "apps/demo/build.gradle.kts:"));
        }
        test("bypasses: a script without the .sh extension is read by its #! line", () -> {
            expectOnly(withImages().write("tools/ci/run-it", "#!/usr/bin/env bash\nset -e\ndocker run alpine:3.17\n")
                    .findings(), "images", "tools/ci/run-it:3: docker run is not an allowed use");
            expectClean(withImages().write("docs/notes", "Not a script: docker run alpine:3.17 is refused.\n").findings());
        });
        test("bypasses: a build script may still declare the reviewed Testcontainers artifacts", () -> expectClean(
                withImages().write("apps/demo/build.gradle.kts",
                        "dependencies { testImplementation(\"org.testcontainers:testcontainers-postgresql\") }\n")
                        .findings()));
    }

    /**
     * What the shell, javac, kotlinc, groovyc or a YAML parser reads as docker, Testcontainers or a container, whatever
     * the spelling: quotes, escapes, comments and string forms are normalised before matching, not enumerated. First
     * the counterexamples of the second review of pull request #54, then the spellings this pull request tried itself.
     */
    static void imageNormalisation() {
        String groovy = "apps/demo/build.gradle";
        String kotlin = "apps/demo/build.gradle.kts";
        // The second review's counterexamples.
        for (String command : List.of("d\"ocker\" run alpine:3.17", "dock\\er run alpine:3.17")) {
            test("normalised: the shell joins quotes and escapes into docker: " + show(command), () -> expectFinding(
                    withImages().write(STAGING, staging(command)).findings(), "images", STAGING + ":4: docker run"));
        }
        test("normalised: a Groovy build script's single-quoted strings are strings", () -> expectFinding(withImages()
                .write(groovy, "tasks.register('pull', Exec) { commandLine 'docker', 'run', 'alpine:3.17' }\n")
                .findings(), "images", groovy + ":1: runs a container runtime as a command"));
        test("normalised: a comment between the parts of a package name hides nothing", () -> expectFinding(withImages()
                .write(OTHER_TEST, "import org./*gap*/testcontainers.containers.GenericContainer;\n\nclass OtherTest {\n"
                        + "    void run() {\n        new GenericContainer<>(\"alpine:3.17\").start();\n    }\n}\n")
                .findings(), "images", OTHER_TEST + ":1: ", "only ReviewedPostgres"));
        // The class of each: shell quoting, escapes and expansions; comments; every string form; any reference.
        for (String command : List.of("'do''cker' run alpine:3.17", "do\"\"cker run alpine:3.17",
                "\"$(echo docker)\" run alpine:3.17", "`echo docker` run alpine:3.17", "x=${runtime:-docker}",
                "bash -c 'd''ocker run alpine:3.17'", "eval d\\\"ocker\\\" run alpine:3.17", "sudo -E d\\ocker run alpine")) {
            test("normalised: a script that spells docker this way is refused: " + show(command), () -> expectFinding(
                    withImages().write(STAGING, staging(command)).findings(), "images", STAGING + ":4: "));
        }
        test("normalised: a workflow step that quotes docker is refused", () -> expectFinding(withImages().write(WORKFLOW,
                workflow("    steps:\n      - run: d\"ocker\" run alpine:3.17").replace(
                        "    steps:\n      - run: echo", "      - run: echo")).findings(), "images", WORKFLOW + ":6: "));
        test("normalised: a line comment and a line break hide no package name", () -> {
            expectFinding(withImages().write(OTHER_TEST, "import org.//gap\ntestcontainers.containers.GenericContainer;\n"
                    + "class OtherTest { }\n").findings(), "images", OTHER_TEST + ":1: ", "only ReviewedPostgres");
            expectFinding(withImages().write(OTHER_TEST, "class OtherTest { Object c = com./**/github./**/dockerjava.api"
                    + ".model.Bind.parse(\"/:/h\"); }\n").findings(), "images", OTHER_TEST + ":1: ", "only ReviewedPostgres");
        });
        test("normalised: any reference to Testcontainers outside the harness is refused, a star import too", () ->
                expectFinding(withImages().write(OTHER_TEST, "import org.testcontainers.containers.*;\n\nclass OtherTest {\n"
                        + "    Object c = new GenericContainer<>(\"alpine:3.17\");\n}\n").findings(), "images",
                        OTHER_TEST + ":1: ", "only ReviewedPostgres"));
        for (String script : List.of("tasks.register('pull', Exec) { commandLine '''docker''', 'run', 'alpine:3.17' }\n",
                "tasks.register('pull', Exec) { commandLine \"\"\"docker\"\"\", 'run', 'alpine:3.17' }\n",
                "tasks.register('pull', Exec) { commandLine 'd\\u006fcker', 'run', 'alpine:3.17' }\n")) {
            test("normalised: a Groovy string form is read: " + show(script.strip()), () -> expectFinding(withImages()
                    .write(groovy, script).findings(), "images", groovy + ":1: runs a container runtime as a command"));
        }
        for (String script : List.of(
                "tasks.register<Exec>(\"pull\") { commandLine(\"sh\", \"-c\", \"\"\"d\"ocker\" run alpine\"\"\") }\n",
                "tasks.register<Exec>(\"pull\") { commandLine(\"\\u0064ocker\", \"run\", \"alpine\") }\n")) {
            test("normalised: a Kotlin string form is read: " + show(script.strip()), () -> expectFinding(withImages()
                    .write(kotlin, script).findings(), "images", kotlin + ":1: runs a container runtime as a command"));
        }
        // Spellings this pull request tried against its own check. The first three are new to it: ANSI-C quoting, a
        // Java octal escape and an escaped YAML string. Each reaches docker in bash, javac or the workflow's parser.
        test("own evasion 1: ANSI-C quoting spells docker with an escape", () -> expectFinding(withImages().write(STAGING,
                staging("$'\\x64ocker' run alpine:3.17")).findings(), "images", STAGING + ":4: "));
        test("own evasion 2: a Java octal escape spells docker in a command", () -> expectFinding(withImages()
                .write(OTHER_TEST, "class OtherTest {\n    ProcessBuilder p = new ProcessBuilder(\"\\144ocker\", \"run\", "
                        + "\"alpine:3.17\");\n}\n").findings(), "images", OTHER_TEST + ":2: runs a container runtime"));
        test("own evasion 3: an escaped YAML string spells docker in a workflow step", () -> expectFinding(withImages()
                .write(WORKFLOW, workflow("    steps:\n      - run: \"\\u0064ocker run alpine:3.17\"").replace(
                        "    steps:\n      - run: echo", "      - run: echo")).findings(), "images", WORKFLOW + ":6: "));
        test("own evasion 4: a heredoc that bash runs is read as commands", () -> expectFinding(withImages().write(
                STAGING, staging("bash <<'SCRIPT'\ndocker run alpine:3.17\nSCRIPT")).findings(), "images", STAGING + ":5: "));
        test("own evasion 5: a Java text block joins a line break into docker", () -> expectFinding(withImages()
                .write(OTHER_TEST, "class OtherTest {\n    String s = \"\"\"\n        doc\\\n        ker run alpine:3.17\n"
                        + "        \"\"\";\n}\n").findings(), "images", OTHER_TEST + ":", "runs a container runtime"));
        // Then against the normalising version itself: spellings that still reached docker after the first round of
        // normalisation, or that try to. Each must be refused.
        test("own evasion 6: brace expansion assembles docker run", () -> expectFinding(withImages().write(STAGING,
                staging("{docker,run} alpine:3.17")).findings(), "images", STAGING + ":4: "));
        test("own evasion 7: a Groovy slashy string is a string", () -> expectFinding(withImages().write(groovy,
                "tasks.register('pull', Exec) { commandLine /docker/, 'run', 'alpine:3.17' }\n").findings(), "images",
                groovy + ":1: "));
        test("own evasion 8: a Kotlin string template splits docker around a literal", () -> expectFinding(withImages()
                .write(kotlin, "tasks.register<Exec>(\"pull\") { commandLine(\"doc${\"\"}ker\", \"run\", \"alpine\") }\n")
                .findings(), "images", kotlin + ":1: "));
        test("own evasion 9: a line continuation in a YAML block joins the indented next line", () -> expectFinding(
                withImages().write(WORKFLOW, workflow("    steps:\n      - run: |\n          doc\\\n          ker run alpine:3.17")
                        .replace("    steps:\n      - run: echo", "      - run: echo")).findings(), "images", WORKFLOW + ":"));
        test("own evasion 10: a GitHub expression that builds the command", () -> expectFinding(withImages().write(
                WORKFLOW, workflow("    steps:\n      - run: ${{ format('{0}ocker run alpine:3.17', 'd') }}").replace(
                        "    steps:\n      - run: echo", "      - run: echo")).findings(), "images", WORKFLOW + ":6: "));
        test("own evasion 11: a command substitution in the place of the command", () -> expectFinding(withImages()
                .write(STAGING, staging("$(printf '\\x64ocker') run alpine:3.17")).findings(), "images", STAGING + ":4: "));
        test("own evasion 12: a test resource that holds the command", () -> expectFinding(withImages().write(
                "apps/demo/src/test/resources/command.txt", "docker run alpine:3.17\n").findings(), "images",
                "apps/demo/src/test/resources/command.txt:1: "));
        test("own evasion 13: a Java file outside src/, run by java as a single-file program", () -> expectFinding(
                withImages().write("tools/run/Pull.java", "class Pull { public static void main(String[] a) throws Exception {"
                        + " new ProcessBuilder(\"docker\", \"run\", \"alpine:3.17\").start(); } }\n").findings(), "images",
                "tools/run/Pull.java:1: runs a container runtime as a command"));
        // What the normalisation must leave alone.
        test("normalised: paths, the socket, the service and a read of the engine are no refused command", () -> {
            for (String command : List.of("printf '{\"ip\":\"127.0.0.1\"}\\n' | sudo tee /etc/docker/daemon.json > /dev/null",
                    "export DOCKER_HOST=unix:///var/run/docker.sock", "sudo systemctl restart docker || exit 1",
                    "echo \"the engine runs\"", "docker version --format \"$(printf '%s' '{{.Server.Version}}')\"")) {
                expectClean(withImages().write(STAGING, staging(command)).findings());
            }
            expectClean(withImages().write(OTHER_TEST, "class OtherTest {\n    String socket = \"unix:///var/run/docker.sock\";"
                    + "\n    // A comment may say that a docker run is refused.\n}\n").findings());
        });
    }

    /**
     * Fail closed: a form that this check cannot decode completely is refused, whatever it holds. First the
     * counterexamples of the third review of pull request #54, each through every entry point it named.
     */
    static void imageFailClosed() {
        String heredoc = "cat <<EOF\n# $(d\"ocker\" run alpine:3.17)\nEOF";
        test("closed: an unquoted heredoc in a script, where # does not start a comment", () -> expectFinding(withImages()
                .write(STAGING, staging(heredoc)).findings(), "images", STAGING + ":4: ", "heredoc"));
        test("closed: the heredoc as a bash -c string from Java", () -> expectFinding(withImages().write(OTHER_TEST,
                "class OtherTest {\n    ProcessBuilder p = new ProcessBuilder(\"bash\", \"-c\", \"cat <<EOF\\n# $(d\\\"ocker\\\""
                        + " run alpine:3.17)\\nEOF\");\n}\n").findings(), "images", OTHER_TEST + ":2: ", "heredoc"));
        test("closed: the heredoc as a bash -c string from Kotlin", () -> expectFinding(withImages().write(
                "apps/demo/src/test/kotlin/demo/Probe.kt", "package demo\n\nval p = ProcessBuilder(\"bash\", \"-c\", \"\"\"" + heredoc
                        + "\"\"\")\n").findings(), "images", "apps/demo/src/test/kotlin/demo/Probe.kt:3: ", "heredoc"));
        test("closed: the heredoc as a bash -c string from the Kotlin DSL", () -> expectFinding(withImages().write(
                "apps/demo/build.gradle.kts", "tasks.register<Exec>(\"probe\") { commandLine(\"bash\", \"-c\", \"\"\"" + heredoc
                        + "\"\"\") }\n").findings(), "images", "apps/demo/build.gradle.kts:1: ", "heredoc"));
        test("closed: the heredoc as a bash -c string from Groovy", () -> expectFinding(withImages().write(
                "apps/demo/src/test/groovy/demo/Probe.groovy", "class Probe { def p = ['bash', '-c', '''" + heredoc
                        + "'''].execute() }\n").findings(), "images", "apps/demo/src/test/groovy/demo/Probe.groovy:1: "));
        test("closed: the heredoc as a bash -c string from the Groovy DSL", () -> expectFinding(withImages().write(
                "apps/demo/build.gradle", "tasks.register('probe', Exec) { commandLine 'bash', '-c', '''" + heredoc
                        + "''' }\n").findings(), "images", "apps/demo/build.gradle:1: "));
        test("closed: a YAML double-quoted scalar that spans lines, with an escape", () -> expectFinding(withImages()
                .write(WORKFLOW, workflow("    steps:\n      - run: \"\n          d\\u006fcker run alpine:3.17\"").replace(
                        "    steps:\n      - run: echo", "      - run: echo")).findings(), "images", WORKFLOW + ":6: "));
        test("closed: a quoted line continuation in a run block, which YAML dedents", () -> expectFinding(withImages()
                .write(WORKFLOW, workflow("    steps:\n      - run: |\n          d\"oc\\\n          ker\" run alpine:3.17")
                        .replace("    steps:\n      - run: echo", "      - run: echo")).findings(), "images", WORKFLOW + ":"));
        test("closed: a Kotlin escaped identifier in a package name", () -> expectFinding(withImages().write(
                "apps/demo/src/test/kotlin/demo/Probe.kt", "package demo\n\nimport org.`testcontainers`.containers.GenericContainer"
                        + "\n\nfun test() { GenericContainer<Nothing>(\"alpine:3.17\").start() }\n").findings(), "images",
                "apps/demo/src/test/kotlin/demo/Probe.kt:3: "));
        test("closed: a constant concatenation that spells docker", () -> expectFinding(withImages().write(OTHER_TEST,
                "class OtherTest {\n    Process p() throws Exception {\n        return new ProcessBuilder(\"do\" + \"cker\", \"run\", "
                        + "\"alpine:3.17\").start();\n    }\n}\n").findings(), "images", OTHER_TEST + ":3: "));
        test("closed: a script in a resource, by its #! line, whatever its extension", () -> expectFinding(withImages()
                .write("apps/demo/src/test/resources/probe.bash", "#!/usr/bin/env bash\ndocker run alpine:3.17\n").findings(),
                "images", "apps/demo/src/test/resources/probe.bash:2: docker run"));
        test("closed: a resource is normalised like shell text", () -> expectFinding(withImages().write(
                "apps/demo/src/test/resources/command.txt", "d\"ocker\" run alpine:3.17\n").findings(), "images",
                "apps/demo/src/test/resources/command.txt:1: "));
        // What must still pass: the repository's own forms.
        test("closed: here-strings, arithmetic, conditions, case patterns and wrappers are read, not refused", () -> {
            for (String command : List.of("read -r a b <<< \"$line\"", "count=$((count + 1))",
                    "if (( 10#${have[i]} > 10#${need[i]} )); then echo more; fi",
                    "[[ \"$name\" != TESTCONTAINERS_* || \"$name\" == \"X\" ]] || exit 1",
                    "case \"${1:-}\" in\n  prepare | stage) echo one ;;\n  *) echo usage ;;\nesac",
                    "(cd \"$dir\" && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum) > out",
                    "exec /usr/bin/grep -q x file", "sudo -E env A=1 tee /etc/docker/daemon.json < /dev/null")) {
                expectClean(withImages().write(STAGING, staging(command)).findings());
            }
            expectClean(withImages().write(WORKFLOW, "name: ci\non: push\njobs:\n  x:\n    runs-on: ubuntu-24.04\n"
                    + "    env:\n      FLAG: 'true'\n    steps:\n      - name: A step\n        if: ${{ !cancelled() }}\n"
                    + "        run: |\n          echo \"3b8a  x.jar\" | sha256sum --check --strict\n"
                    + "          if [[ -n \"$links\" ]]; then\n            exit 1\n          fi\n").findings());
        });
    }

    /** A workflow whose seventh line is the first of the given step lines. */
    static String steps(String lines) {
        return "name: ci\non: push\njobs:\n  x:\n    runs-on: ubuntu-24.04\n    steps:\n" + lines + "\n";
    }

    /** Each form the check now refuses because it cannot decode it, one fixture each, and the forms it still reads. */
    static void imageFailClosedForms() {
        for (String[] form : new String[][] {
                {"cat <<-EOF\n\tx\n\tEOF", "heredoc"}, {"x=$(( 1 << 2 ))", "heredoc"},
                {"/usr/bin/d?cker run alpine:3.17", "a glob in the place of a command"},
                {"c=dock; ${c}er run alpine:3.17", "the command is an expansion"},
                {"d{o,}cker run alpine:3.17", "brace expansion"},
                {"$\"docker\" run alpine:3.17", "docker run is not an allowed use"},
                {"python3 -c 'print(1)'", "an interpreter whose programs this check does not read"},
                {"bash docs/notes.md", "a file this check does not read as a script"}}) {
            test("forms: a script that holds this form is refused: " + show(form[0]), () -> expectFinding(withImages()
                    .write("docs/notes.md", "Notes.\n").write(STAGING, staging(form[0])).findings(), "images",
                    STAGING + ":4: ", form[1]));
        }
        for (String[] form : new String[][] {
                {"      - run: >\n          echo x", "a folded block"},
                {"      - run: |2\n          echo x", "an indentation indicator"},
                {"      - run: &cmd echo x", "anchor"}, {"      - run: *cmd", "alias"},
                {"      - run: !!str echo x", "tag"},
                {"      - run: echo one\n          two", "continued over lines", "8"},
                {"      - run: 'echo\n          x'", "single-quoted YAML scalar continued over lines"},
                {"      - {name: x, run: echo x}", "inside a YAML flow collection"},
                {"      - run: echo ${{ github.sha }}", "a GitHub expression in a run step"},
                {"      - run: echo x\n        shell: python {0}", "a shell other than bash or sh", "8"},
                {"      <<: {run: echo x}", "merge key"}}) {
            test("forms: a workflow that holds this form is refused: " + show(form[0]), () -> expectFinding(withImages()
                    .write(WORKFLOW, steps(form[0])).findings(), "images", WORKFLOW + ":" + (form.length > 2 ? form[2]
                    : "7") + ": ", form[1]));
        }
        test("forms: a tab in a workflow's indentation is refused", () -> expectFinding(withImages().write(WORKFLOW,
                steps("      - run: echo x\n\t  name: y")).findings(), "images", WORKFLOW + ":8: ", "a tab"));
        test("forms: an action that is not composite is refused", () -> expectFinding(withImages().write(
                ".github/actions/probe/action.yml", "runs:\n  using: node20\n  main: index.js\n").findings(), "images",
                ".github/actions/probe/action.yml:2: ", "not composite"));
        test("forms: a #! line for another interpreter is refused", () -> expectFinding(withImages().write(
                "tools/run/probe", "#!/usr/bin/python3\nprint(1)\n").findings(), "images", "tools/run/probe:1: a #! line"));
        test("forms: a Groovy file is refused, whatever it holds", () -> expectFinding(withImages().write(
                "apps/demo/build.gradle", "// nothing\n").findings(), "images", "apps/demo/build.gradle:1: a Groovy"));
        test("forms: a backquoted Kotlin name is refused", () -> expectFinding(withImages().write(
                "apps/demo/src/test/kotlin/demo/Probe.kt", "package demo\n\nfun `a test`() { }\n").findings(), "images",
                "apps/demo/src/test/kotlin/demo/Probe.kt:3: a backquoted Kotlin identifier"));
        test("forms: a binary file under src/ is refused", () -> expectFinding(withImages().write(
                "apps/demo/src/test/resources/image.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', (byte) 0xff, 0}).findings(),
                "images", "apps/demo/src/test/resources/image.png: not UTF-8 text"));
        test("forms: an executable anywhere in the tree is refused", () -> expectFinding(withImages().write(
                "tools/bin/probe", new byte[] {0x7f, 'E', 'L', 'F', 2, 1, 1, 0}).findings(), "images",
                "tools/bin/probe: an executable or an archive (an ELF executable)"));
        test("forms: a container build file is refused", () -> expectFinding(withImages().write("tools/Dockerfile",
                "FROM alpine:3.17\n").findings(), "images", "tools/Dockerfile: a container build or compose file"));
        test("forms: a string literal that names an interpreter is refused", () -> expectFinding(withImages().write(
                OTHER_TEST, "class OtherTest {\n    ProcessBuilder p = new ProcessBuilder(\"python3\", \"x.py\");\n}\n")
                .findings(), "images", OTHER_TEST + ":2: runs python3"));
        test("forms: $'...' quoting in a string literal is refused", () -> expectFinding(withImages().write(OTHER_TEST,
                "class OtherTest {\n    ProcessBuilder p = new ProcessBuilder(\"bash\", \"-c\", \"$'\\\\x64ocker' run "
                        + "alpine\");\n}\n").findings(), "images", OTHER_TEST + ":2: $'...' quoting"));
        test("forms: $'...' without an escape is plain quoting, as in a SQL pattern in a resource", () -> expectClean(
                withImages().write("apps/demo/src/main/resources/db/V1__x.sql", "select 1 where 'a' ~ '^a$';\n")
                        .write(STAGING, staging("echo $'plain'")).findings()));
        test("forms: functions, arrays, traps, loops, process substitution and case in a substitution are read", () ->
                expectClean(withImages().write(STAGING, staging("f() { local -a names=(\"$1\" \"$2\"); printf '%s\\n' "
                        + "\"${names[@]}\"; }\ntrap 'rm -f \"$tmp\"' EXIT\nwhile read -r x; do echo \"$x\"; done < <(printf "
                        + "'a\\n')\nfor x in a b; do f \"$x\" y; done\ny=$(case \"$x\" in a) echo 1 ;; *) echo 2 ;; esac)\n"
                        + "echo \"$x is set\" 2>&1 | tee out")).findings()));
    }

    /**
     * Ten evasions this pull request tried against the fail-closed version itself. The first seven got through it and
     * are refused since; the last three were refused at the first try.
     */
    static void imageOwnEvasionsRound3() {
        test("own evasion 14: a pipe into bash runs text that printf assembles", () -> expectFinding(withImages()
                .write(STAGING, staging("printf '%s%s run alpine:3.17\\n' d ocker | bash")).findings(), "images",
                STAGING + ":4: ", "reads its commands from its input"));
        test("own evasion 15: BASH_ENV makes every bash step source a file this check does not read", () ->
                expectFinding(withImages().write("tools/ci/steps.txt", "docker run alpine:3.17\n").write(WORKFLOW,
                        "name: ci\non: push\njobs:\n  x:\n    runs-on: ubuntu-24.04\n    env:\n      BASH_ENV: "
                                + "tools/ci/steps.txt\n    steps:\n      - run: echo x\n").findings(), "images",
                        WORKFLOW + ":7: sets BASH?ENV"));
        test("own evasion 16: an awk program runs the command", () -> expectFinding(withImages().write(STAGING,
                staging("awk 'BEGIN { system(\"d\" \"ocker run alpine:3.17\") }'")).findings(), "images", STAGING + ":4: ",
                "an awk program that runs commands"));
        test("own evasion 17: an executable without #! or extension, run by its name on PATH", () -> {
            Repo repo = withImages().write("tools/bin/probe", "docker run alpine:3.17\n")
                    .write(STAGING, staging("PATH=\"$PWD/tools/bin:$PATH\" probe"));
            Files.setPosixFilePermissions(repo.root.resolve("tools/bin/probe"), PosixFilePermissions.fromString("rwxr-xr-x"));
            expectFinding(repo.findings(), "images", "tools/bin/probe:1: docker run");
        });
        test("own evasion 18: make runs a Makefile's recipe", () -> expectFinding(withImages().write("tools/ci/probe.mk",
                "x:\n\tdocker run alpine:3.17\n").write(STAGING, staging("make -f tools/ci/probe.mk")).findings(), "images",
                STAGING + ":4: runs make"));
        test("own evasion 19: a test talks to the engine's socket, naming no runtime", () -> expectFinding(withImages()
                .write(OTHER_TEST, "class OtherTest {\n    Object engine() throws Exception {\n        return java.nio.channels"
                        + ".SocketChannel.open(java.net.UnixDomainSocketAddress.of(\"/var/run/docker.sock\"));\n    }\n}\n")
                .findings(), "images", OTHER_TEST + ":3: opens a Unix socket"));
        test("own evasion 20: a JVM option in gradle.properties runs the command", () -> expectFinding(withImages()
                .write("gradle.properties", "org.gradle.jvmargs=-XX:OnError=d''ocker run alpine:3.17\n").findings(),
                "images", "gradle.properties:1: "));
        test("own evasion 21: a char array spells docker", () -> expectFinding(withImages().write(OTHER_TEST,
                "class OtherTest {\n    ProcessBuilder p = new ProcessBuilder(new String(new char[] {'d', 'o', 'c', 'k', "
                        + "'e', 'r'}), \"run\", \"alpine:3.17\");\n}\n").findings(), "images", OTHER_TEST + ":2: ",
                "split across string literals"));
        test("own evasion 22: a Kotlin template joins a variable to the rest of the name", () -> expectFinding(withImages()
                .write("apps/demo/src/test/kotlin/demo/Probe.kt", "package demo\n\nval d = \"do\"\nval p = ProcessBuilder("
                        + "\"${d}cker\", \"run\", \"alpine:3.17\")\n").findings(), "images",
                "apps/demo/src/test/kotlin/demo/Probe.kt:3: ", "split across string literals"));
        test("own evasion 23: a local composite action runs the step", () -> expectFinding(withImages().write(
                ".github/actions/probe/action.yml", "runs:\n  using: composite\n  steps:\n    - run: docker run alpine:3.17\n"
                        + "      shell: bash\n").write(WORKFLOW, steps("      - uses: ./.github/actions/probe")).findings(),
                "images", ".github/actions/probe/action.yml:4: docker run"));
    }

    // ---- the checked tree: no symbolic link in it, nothing read from outside it ----

    static void tree() {
        test("tree: a symbolic link to a lockfile is refused and named", () -> expectRefused(
                Repo.clean().link("libs/demo/gradle.lockfile", Path.of("../../gradle.lockfile")),
                LINK_REFUSED + "libs/demo/gradle.lockfile"));
        test("tree: a symbolic link to a directory that holds a lockfile is refused", () -> {
            Path elsewhere = temporaryDirectory();
            Files.writeString(elsewhere.resolve("gradle.lockfile"), "org.example:unreviewed:1.0.0=runtimeClasspath\n");
            expectRefused(Repo.clean().link("libs/linked", elsewhere), LINK_REFUSED + "libs/linked");
        });
        test("tree: a dangling symbolic link is refused", () -> expectRefused(
                Repo.clean().link("docs/dangling.md", Path.of("missing.md")), LINK_REFUSED + "docs/dangling.md"));
        test("tree: a symbolic link to a file outside the tree is refused before the file is read", () -> {
            Path outside = temporaryDirectory().resolve("notes.txt");
            Files.writeString(outside, MARKER + "=runtimeClasspath\n");
            IOException e = expectRefused(Repo.clean().link("settings-gradle.lockfile", outside),
                    LINK_REFUSED + "settings-gradle.lockfile");
            expect(!e.getMessage().contains(MARKER), "the refusal repeats the file: " + e);
        });
        test("tree: symbolic links in the directories the checks skip are refused too", () -> {
            for (String path : List.of(".git/hooks/pre-commit", "build/output", ".gradle/cache", "node_modules/.bin/tool")) {
                // A printed path passes through PolicyCheck.safe, which has no '_': names come from the pull request.
                expectRefused(Repo.clean().link(path, Path.of("elsewhere")), LINK_REFUSED + path.replace('_', '?'));
            }
        });
        test("tree: a checked tree that is itself a symbolic link is refused", () -> {
            Repo repo = Repo.clean().save();
            Path link = temporaryDirectory().resolve("repository");
            Files.createSymbolicLink(link, repo.root);
            IOException e = expectThrows(IOException.class, () -> new PolicyCheck(link, TODAY));
            expect(e.getMessage().contains(ROOT_REFUSED), "expected " + ROOT_REFUSED + ", got " + e);
        });
        test("tree: a review record outside the tree is refused, by an absolute or a relative path", () -> {
            Path outside = temporaryDirectory().resolve("review.md");
            Files.writeString(outside, "# A review record outside the repository\n");
            for (String record : List.of(outside.toString(), "../" + outside.getParent().getFileName() + "/review.md")) {
                Repo repo = Repo.clean();
                repo.lib().put("reviewRecord", record);
                expectRefused(repo, "refused: the review record of org.example:lib 1.0.0 is outside the checked tree");
            }
        });
        test("tree: a review record is probed and read at its normalized path, not as it is spelled", () -> {
            // Spelled to leave the tree and come back: <outside>/probe/../../<tree>/security/reviews/<record>.
            List<List<String>> findings = new ArrayList<>();
            for (boolean probe : List.of(false, true)) {
                Repo repo = Repo.clean();
                Path outside = temporaryDirectory().toRealPath();
                if (probe) Files.createDirectory(outside.resolve("probe"));
                String record = (String) repo.lib().get("reviewRecord");
                repo.lib().put("reviewRecord",
                        outside + "/probe/../../" + repo.root.toRealPath().getFileName() + "/" + record);
                findings.add(repo.findings());
            }
            expectEquals(List.of(List.of(), List.of()), findings);
        });
        test("tree: a directory the walk cannot read stops the check", () -> {
            Repo repo = Repo.clean().save();
            Path locked = Files.createDirectories(repo.root.resolve("locked"));
            Files.setPosixFilePermissions(locked, Set.of());
            try {
                if (!Files.isReadable(locked)) {
                    IOException e = expectThrows(IOException.class, () -> new PolicyCheck(repo.root, TODAY));
                    expectEquals("locked: cannot be inspected", e.getMessage());
                } else {
                    // File permissions do not bind this user, so it is root: the check runs as the unprivileged nobody.
                    List<String> command = new ArrayList<>(unprivileged().orElseThrow(() -> new AssertionError(
                            "file permissions do not bind this user, and neither setpriv, runuser nor sudo runs the check"
                                    + " as an unprivileged one: run the tests as a non-root user")));
                    Path checker = Files.copy(checker(), shared(temporaryDirectory()).resolve("PolicyCheck.java"));
                    command.addAll(List.of(java(), checker.toString(), shared(repo.root).toString()));
                    Run run = run(command, Duration.ofMinutes(5), null);
                    expect(run.exit() == 2 && run.output().contains("locked: cannot be inspected"), run.toString());
                }
            } finally {
                Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
            }
        });
        test("tree: no file outside the tree is read, not even through a link made after the walk", () -> {
            Path outside = temporaryDirectory().resolve("notes.txt");
            Files.writeString(outside, MARKER + "\n");
            Repo repo = Repo.clean().save();
            PolicyCheck check = new PolicyCheck(repo.root, TODAY);
            // Real paths, as the checker forms them: a path that only looks outside would be refused before the probe.
            Path root = repo.root.toRealPath();
            Path late = Files.createSymbolicLink(root.resolve("late-link.txt"), outside);
            // A missing file outside is refused too, not reported missing: nothing outside the tree is probed.
            for (Path path : List.of(outside, outside.resolveSibling("missing.txt"), late, root.resolve("security"))) {
                IOException e = expectThrows(IOException.class, () -> check.read(path));
                expect(e.getMessage().contains("is not a regular file inside the checked tree")
                        && !e.getMessage().contains(MARKER), e.toString());
            }
        });
    }

    /** A command prefix that runs a command as the unprivileged user nobody, when a standard tool here can. */
    static Optional<List<String>> unprivileged() {
        for (List<String> prefix : List.of(List.of("setpriv", "--reuid=65534", "--regid=65534", "--clear-groups"),
                List.of("runuser", "-u", "nobody", "--"), List.of("sudo", "-n", "-u", "nobody", "--"))) {
            List<String> probe = new ArrayList<>(prefix);
            probe.addAll(List.of(java(), "-version"));
            try {
                if (run(probe, Duration.ofMinutes(1), null).exit() == 0) return Optional.of(prefix);
            } catch (Exception | AssertionError e) {
                // This tool is not installed here, or cannot run the JDK as nobody.
            }
        }
        return Optional.empty();
    }

    /** Lets every user list and enter a directory that a case created. */
    static Path shared(Path dir) throws IOException {
        return Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    /** The check stops before it reads anything, with this reason in its message. */
    static IOException expectRefused(Repo repo, String reason) {
        IOException e = expectThrows(IOException.class, repo::findings);
        expect(e.getMessage() != null && e.getMessage().contains(reason), "expected a refusal with " + reason + ", got " + e);
        return e;
    }

    // ---- the command line: exit codes 0, 1 and 2 ----

    static void commandLine() {
        test("cli: a clean repository exits 0", () -> {
            Run run = launch(Repo.clean().save().root.toString());
            expect(run.exit() == 0 && run.output().contains("## Dependency allowlist: clean"), run.toString());
        });
        test("cli: findings exit 1 and are listed", () -> {
            Repo repo = Repo.clean().append("gradle.lockfile", "org.example:other:2.0.0=runtimeClasspath\n").save();
            Run run = launch(repo.root.toString());
            expect(run.exit() == 1 && run.output().contains("## Dependency allowlist: 1 finding(s)\n")
                    && run.output().contains("- org.example:other 2.0.0 (in gradle.lockfile) has no review"), run.toString());
        });
        test("cli: --report-only lists the findings and exits 0", () -> {
            Repo repo = Repo.clean().append("gradle.lockfile", "org.example:other:2.0.0=runtimeClasspath\n").save();
            Run run = launch("--report-only", repo.root.toString());
            expect(run.exit() == 0 && run.output().contains("1 finding(s) (report-only)"), run.toString());
        });
        test("cli: an allowlist that is not JSON exits 2, so a broken check never reads as a pass", () -> {
            Repo repo = Repo.clean().save().edit("security/allowlist.json", "\"schemaVersion\":1", "\"schemaVersion\":+");
            Run run = launch(repo.root.toString());
            expect(run.exit() == 2 && run.output().contains("## Dependency allowlist: could not complete"), run.toString());
        });
        test("cli: a repository without an allowlist exits 2, naming the file", () -> {
            Run run = launch(new Repo().root.toString());
            expect(run.exit() == 2 && run.output().contains("security/allowlist.json: not found"), run.toString());
        });
        test("cli: --print-digest prints the reviewed-set digest and exits 0", () -> {
            Repo repo = Repo.clean().save();
            Run run = launch("--print-digest", repo.root.toString());
            expect(run.exit() == 0, run.toString());
            expectEquals(repo.check().entriesDigest() + System.lineSeparator(), run.output());
        });
        test("cli: an unreadable lockfile line is reported by its number, never by its content", () -> {
            for (String line : List.of(MARKER + "=runtimeClasspath", MARKER)) {
                Run run = launch(Repo.clean().append("gradle.lockfile", line + "\n").save().root.toString());
                expect(!run.output().contains(MARKER) && !run.error().contains(MARKER), "the line was printed: " + run);
                expect(run.exit() == 1 && run.output().contains("- gradle.lockfile:4: " + UNREADABLE_LINE), run.toString());
            }
        });
        test("cli: a duplicate JSON key is reported by its place in the file, never by the key", () -> {
            Repo repo = Repo.clean().save().edit("security/allowlist.json", "\"schemaVersion\":1",
                    "\"schemaVersion\":1,\"" + MARKER + "\":1,\"" + MARKER + "\":2");
            String text = Files.readString(repo.root.resolve("security/allowlist.json"));
            int column = text.indexOf("\"" + MARKER + "\"", text.indexOf(MARKER) + MARKER.length()) + 1;
            Run run = launch(repo.root.toString());
            expectNoContent(run, MARKER);
            expect(run.exit() == 2 && run.output().contains("security/allowlist.json:1:" + column + ": JSON duplicate key"),
                    run.toString());
        });
        test("cli: a malformed JSON value is reported by its place in the file, never by its text", () -> {
            for (String value : List.of("\"\\u" + MARK4 + "\"", "\u00a7" + MARKER)) {
                Repo repo = Repo.clean().save().edit("security/allowlist.json", "\"schemaVersion\":1",
                        "\"schemaVersion\":" + value);
                Run run = launch(repo.root.toString());
                expectNoContent(run, MARKER, MARK4, "\u00a7");
                expect(run.exit() == 2 && run.output().contains("security/allowlist.json:1:"), run.toString());
            }
        });
        test("cli: a malformed XML element is reported by its place in the file, and the parser prints nothing", () -> {
            Repo repo = Repo.clean().save().edit("gradle/verification-metadata.xml", "<components>",
                    "<components><" + MARKER + ">");
            Run run = launch(repo.root.toString());
            expectNoContent(run, MARKER);
            expect(run.exit() == 2 && run.error().isEmpty()
                    && run.output().contains("gradle/verification-metadata.xml:15:") && run.output().contains("unreadable XML"),
                    run.toString());
        });
        test("cli: markup and control characters in a coordinate, an Action or a URL are printed as safe characters", () -> {
            Repo repo = withNpm()
                    .append("gradle.lockfile", "org.example:**md**<b>x</b>\u001b[31m:1.0.0=runtimeClasspath\n")
                    .append("gradle.lockfile", "org.example:_emphasis_:1.0.0=runtimeClasspath\n")
                    .append("gradle.lockfile", "org.example:__strong__:1.0.0=runtimeClasspath\n")
                    .write(".github/workflows/ci.yml", "jobs:\n  x:\n    steps:\n      - uses: <script>x</script>@v1\n")
                    .edit("package-lock.json", "https://registry.npmjs.org/example-pkg/-/example-pkg-1.0.0.tgz",
                            "https://registry.example.org/[click](javascript:alert(1))<img src=x>")
                    .save();
            Run run = launch(repo.root.toString());
            expectNoContent(run, "**md**", "<b>", "</b>", "\u001b", "<script>", "[click](", "<img", "src=x>",
                    "_emphasis_", "__strong__");
            expect(run.exit() == 1 && run.error().isEmpty()
                    && run.output().contains("- org.example:??md???b?x?/b???31m 1.0.0 (in gradle.lockfile) has no review")
                    && run.output().contains("- org.example:?emphasis? 1.0.0 (in gradle.lockfile) has no review")
                    && run.output().contains("- org.example:??strong?? 1.0.0 (in gradle.lockfile) has no review")
                    && run.output().contains("- .github/workflows/ci.yml:4: ?script?x?/script?@v1 is not pinned")
                    && run.output().contains("(https://registry.example.org/?click??javascript:alert?1???img?src=x?)"),
                    run.toString());
        });
        test("cli: a symbolic link in the checked tree exits 2 and is named", () -> {
            Repo repo = Repo.clean().save().link("docs/dangling.md", Path.of("missing.md"));
            Run run = launch(repo.root.toString());
            expect(run.exit() == 2 && run.output().contains(LINK_REFUSED + "docs/dangling.md"), run.toString());
        });
        test("cli: a child that does not finish fails by timeout, counted from when it runs, and is stopped", () -> {
            Path dir = temporaryDirectory();
            Path hang = dir.resolve("Hang.java");
            Path ready = dir.resolve("ready");
            // It starts slowly, more slowly than its timeout, as a slow compile would; then it signals and never exits.
            Files.writeString(hang, """
                    import java.nio.file.Files;
                    import java.nio.file.Path;

                    public class Hang {
                        public static void main(String[] args) throws Exception {
                            Thread.sleep(Long.parseLong(args[1]));
                            System.out.println("running, with standard output open");
                            Files.writeString(Path.of(args[0]), "ready");
                            Thread.sleep(Long.MAX_VALUE);
                        }
                    }
                    """);
            within(Duration.ofMinutes(3), () -> {
                AssertionError timeout = expectThrows(AssertionError.class, () -> run(
                        List.of(java(), hang.toString(), ready.toString(), "2000"), Duration.ofSeconds(1), ready));
                expect(timeout.getMessage().startsWith("the child did not finish within") && Files.exists(ready),
                        timeout + "; the child signalled that it runs: " + Files.exists(ready));
            });
            expect(ProcessHandle.current().children().noneMatch(ProcessHandle::isAlive), "the child is still running");
        });
    }

    /** Runs PolicyCheck.java in a child JVM of the same JDK, as CI does, and gives it five minutes. */
    static Run launch(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(java(), checker().toString()));
        command.addAll(List.of(args));
        return run(command, Duration.ofMinutes(5), null);
    }

    /**
     * Runs a child process with its standard output and error in files, never in pipes that it could fill or hold open,
     * and reads them only once it has exited. Given a ready file, the timeout starts only when the child has created
     * it, which must happen within a minute. A child still running after the timeout fails the case; whatever happens,
     * it is stopped with everything it started, and awaited.
     */
    static Run run(List<String> command, Duration timeout, Path ready) throws Exception {
        Path dir = temporaryDirectory();
        Path output = dir.resolve("stdout");
        Path error = dir.resolve("stderr");
        Process process = new ProcessBuilder(command).redirectOutput(output.toFile()).redirectError(error.toFile()).start();
        try {
            process.getOutputStream().close();
            if (ready != null) awaitReady(process, ready, Duration.ofMinutes(1));
            expect(process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS), "the child did not finish within " + timeout);
            return new Run(process.exitValue(), new String(Files.readAllBytes(output), StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(error), StandardCharsets.UTF_8));
        } finally {
            stop(process.toHandle());
        }
    }

    /** Waits, at most for the limit, until the child has created the ready file; a child that exits first fails. */
    static void awaitReady(Process process, Path ready, Duration limit) throws InterruptedException {
        long deadline = System.nanoTime() + limit.toNanos();
        while (!Files.exists(ready)) {
            expect(process.isAlive(), "the child exited before it was ready");
            expect(System.nanoTime() < deadline, "the child was not ready within " + limit);
            Thread.sleep(20);
        }
    }

    /** Kills a process and every process it started, and waits until each has exited; one that does not, fails. */
    static void stop(ProcessHandle process) throws Exception {
        List<ProcessHandle> all = Stream.concat(process.descendants(), Stream.of(process)).toList();
        all.forEach(ProcessHandle::destroyForcibly);
        for (ProcessHandle each : all) {
            try {
                each.onExit().get(1, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                throw new AssertionError("process " + each.pid() + " still runs a minute after it was killed");
            }
        }
    }

    static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /**
     * Runs the body on a thread of its own. Still running after the limit, it fails, and every child of this JVM is
     * stopped and awaited.
     */
    static void within(Duration limit, Case body) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            body.run();
            return null;
        });
        Thread thread = new Thread(task, "a case limited to " + limit);
        thread.setDaemon(true);
        thread.start();
        try {
            task.get(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            for (ProcessHandle child : ProcessHandle.current().children().toList()) stop(child);
            throw new AssertionError("still running after " + limit + ": without the limit, the suite would hang here");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            if (e.getCause() instanceof Error error) throw error;
            throw e;
        }
    }

    /** PolicyCheck.java next to this file: the source launcher gives the classes it compiles the launched file's URL. */
    static Path checker() throws Exception {
        CodeSource source = PolicyCheckTest.class.getProtectionDomain().getCodeSource();
        Path checker = source == null ? null : Path.of(source.getLocation().toURI()).resolveSibling("PolicyCheck.java");
        expect(checker != null && Files.isRegularFile(checker),
                "PolicyCheck.java is not next to this file; run it as java tools/policy/PolicyCheckTest.java");
        return checker;
    }

    // ---- fixtures ----

    /** A repository in a temporary directory: security/allowlist.json, its review records and the files a case adds. */
    /** Round 4: the same complete command must receive the same decision at every execution boundary. */
    static void reviewedWebCommands() {
        for (String command : List.of("node --version", "npm --version", "npm ci --ignore-scripts",
                "tsc -p apps/shipping-web", "node --test", "npm run build:web --ignore-scripts",
                "npm run test:unit --ignore-scripts")) {
            for (String site : List.of("shell", "workflow", "java", "kotlin")) {
                test("web forms: accepts " + site + " " + command, () ->
                        expectClean(webCommand(webRepo(), site, command, webCwd(command)).findings()));
            }
        }
        for (String command : List.of("node --test --watch", "node --test ../outside.test.ts", "node -e code",
                "node --import startup.mjs --test", "npm ci", "npm ci --ignore-scripts --foreground-scripts",
                "npm run launch", "npm run build:web", "npm run build:web --ignore-scripts -- --watch",
                "npm exec tsc", "npx tsc", "npm install --package-lock-only --ignore-scripts --no-audit --no-fund",
                "tsc -p apps/shipping-web --watch", "tsc -p packages/shipping-client")) {
            for (String site : List.of("shell", "workflow", "java", "kotlin")) {
                test("web forms: refuses " + site + " " + command, () -> expectFinding(
                        webCommand(webRepo(), site, command, webCwd(command)).findings(), "images"));
            }
        }
        for (String site : List.of("shell", "workflow", "java", "kotlin")) {
            test("web forms: refuses wrong cwd in " + site, () -> expectFinding(
                    webCommand(webRepo(), site, "node --test", ".").findings(), "images"));
            for (String field : List.of("missing node", "missing npm", "expired", "rejected", "missing review",
                    "wrong node", "wrong npm", "wrong typescript", "wrong scope", "missing conditions", "pending", "stale")) {
                test("web forms: refuses " + field + " in " + site, () -> {
                    Repo r = webRepo();
                    Map<String, Object> node = r.entries.stream().filter(e -> e.get("kind").equals("runtime")).findFirst().orElseThrow();
                    Map<String, Object> npm = r.entries.stream().filter(e -> e.get("kind").equals("bundled")).findFirst().orElseThrow();
                    Map<String, Object> ts = r.entries.stream().filter(e -> e.get("coordinate").equals("typescript")).findFirst().orElseThrow();
                    switch (field) {
                        case "missing node" -> r.entries.remove(node);
                        case "missing npm" -> r.entries.remove(npm);
                        case "expired" -> node.put("expiresAt", "2026-11-06");
                        case "rejected" -> node.put("verdict", "REJECT");
                        case "missing review" -> Files.delete(r.root.resolve((String) node.get("reviewRecord")));
                        case "wrong node" -> node.put("version", "26.12.0");
                        case "wrong npm" -> npm.put("version", "12.2.1");
                        case "wrong typescript" -> ts.put("version", "7.0.2");
                        case "wrong scope" -> ts.put("project", "packages/shipping-client");
                        case "missing conditions" -> npm.put("conditions", List.of());
                        case "pending" -> r.acceptance.put("status", "pending");
                        case "stale" -> r.acceptance.put("reviewedSetSha256", SHA_B);
                        default -> throw new AssertionError(field);
                    }
                    if (!field.equals("stale")) r.accept();
                    String cmd = field.equals("wrong typescript") || field.equals("wrong scope")
                            ? "tsc -p apps/shipping-web" : "npm ci --ignore-scripts";
                    expectFinding(webCommand(r, site, cmd, ".").findings(), "images");
                });
            }
        }
        test("web forms: ProcessBuilder npm run plus manifest docker is refused at both sites", () -> {
            Repo r = webCommand(webRepo(), "java", "npm run launch", ".")
                    .write("package.json", "{\"scripts\":{\"launch\":\"docker run alpine:3.17\"}}");
            expectFinding(r.findings(), "images", "Probe.java");
            expectFinding(r.findings(), "images", "package.json");
        });
        for (String manifest : List.of("package.json", "apps/shipping-web/package.json")) {
            for (String body : List.of("docker run alpine:3.17", "node -e code", "tsc -p apps/shipping-web --watch")) {
                test("web forms: unregistered body in " + manifest + " " + body, () -> expectFinding(webRepo()
                        .write(manifest, json(map("scripts", map("launch", body)), false)).findings(), "images", manifest));
            }
            for (String hook : List.of("preinstall", "install", "postinstall", "prepare", "prebuild:web", "posttest:unit")) {
                test("web forms: refuses lifecycle " + manifest + " " + hook, () -> expectFinding(webRepo()
                        .write(manifest, json(map("scripts", map(hook, "node --version")), false)).findings(), "images", manifest));
            }
        }
        for (String source : List.of("new ProcessBuilder(command)", "new ProcessBuilder(\"node\", flags)",
                "new ProcessBuilder(\"node\", \"--\" + \"version\")", "new ProcessBuilder(List.of(\"node\", \"--version\"))",
                "new ProcessBuilder().command(command)", "Runtime.getRuntime().exec(command)",
                "Runtime.getRuntime().exec(\"node -e code\")", "new ProcessBuilder(\"npm\", \"run\", \"launch\")")) {
            test("web forms: unresolved or unreviewed process " + source, () -> expectFinding(webRepo()
                    .write("tools/run/Probe.java", "class Probe { Object p = " + source + "; }").findings(), "images"));
        }
        for (String source : List.of("commandLine(command)", "commandLine(\"node\", flags)",
                "commandLine(listOf(\"node\", \"--version\"))", "executable = \"node\"; args(\"--version\")")) {
            test("web forms: unresolved Kotlin process " + source, () -> expectFinding(webRepo()
                    .write("build.gradle.kts", "tasks.register<Exec>(\"probe\") { " + source + " }").findings(), "images"));
        }
        for (String script : List.of("node --version $EXTRA", "node --version \"$EXTRA\"", "node --version *.ts",
                "cd apps/shipping-web; node --test", "cd \"$TARGET\" && node --test", "NODE_OPTIONS=--import=x node --version",
                "npm_config_ignore_scripts=false npm ci --ignore-scripts", "env -S 'node --version -e code'")) {
            test("web forms: unresolved shell context " + script, () -> expectFinding(webRepo()
                    .write("tools/web.sh", script + "\n").findings(), "images"));
        }
        test("web forms: ordinary node metadata is not an execution", () -> expectClean(webRepo()
                .write("tools/run/Probe.java", "class Probe { String node = \"node\"; String npm = \"npm\"; }").findings()));
        reviewedWebAdversarial();
    }

    static void reviewedWebAdversarial() {
        for (String source : List.of(
                "Runtime runtime = Runtime.getRuntime(); Object p = runtime.exec(new String[] {\"npm\", \"run\", \"launch\"});",
                "Object p = new ProcessBuilder(\"sh\", \"-c\", \"node -e code\");",
                "Object p = new ProcessBuilder(\"node\", \"--test\").directory(new java.io.File(\"apps/shipping-web\")); "
                        + "void change() { p.directory(other); }")) {
            test("web adversarial: source " + source, () -> expectFinding(webRepo()
                    .write("tools/run/Probe.java", "class Probe { " + source + " }").findings(), "images", "Probe.java"));
        }
        test("web adversarial: Gradle workingDir is relative to the project", () -> expectFinding(webRepo()
                .write("apps/demo/build.gradle.kts", "tasks.register<Exec>(\"probe\") { workingDir(\"apps/shipping-web\"); "
                        + "commandLine(\"node\", \"--test\") }").findings(), "images", "build.gradle.kts"));
        for (String script : List.of("cd apps/shipping-web && true\nnode --test", "env -Snode -e code",
                "trap node EXIT", "bash -c 'node -e code'")) {
            test("web adversarial: shell " + show(script), () -> expectFinding(webRepo()
                    .write("tools/web.sh", script).findings(), "images", "tools/web.sh"));
        }
        for (String config : List.of("script-shell=./launch.sh", "ignore-scripts=false")) {
            test("web adversarial: npm configuration " + config, () -> expectFinding(webRepo()
                    .write(".npmrc", config).findings(), "images", ".npmrc"));
        }
        for (String workflow : List.of(
                "jobs:\n  web:\n    defaults:\n      run:\n        working-directory: apps/shipping-web\n"
                        + "    steps:\n      - run: node --test\n",
                "jobs:\n  web:\n    steps:\n      - run: node --test\n        working-directory: ${{ matrix.path }}\n")) {
            test("web adversarial: unresolved workflow cwd " + show(workflow), () -> expectFinding(webRepo()
                    .write(".github/workflows/web.yml", workflow).findings(), "images", "web.yml"));
        }
        for (String body : List.of("tsc -p apps/shipping-web && node -e code", "tsc -p apps/shipping-web ")) {
            test("web adversarial: registered name with different body " + body, () -> expectFinding(webRepo()
                    .edit("package.json", "tsc -p apps/shipping-web", body).findings(), "images", "package.json"));
        }
        test("web adversarial: Runtime literal argv gets the same positive decision", () -> expectClean(webRepo()
                .write("tools/run/Probe.java", "class Probe { Object p = Runtime.getRuntime().exec(new String[] "
                        + "{\"npm\", \"ci\", \"--ignore-scripts\"}); }").findings()));
        for (String source : List.of("new ProcessBuilder(\"/bin/bash\", \"-c\", \"node -e code\")",
                "getRuntime().exec(\"npm run launch\")")) {
            test("web adversarial: alternate API spelling " + source, () -> expectFinding(webRepo()
                    .write("tools/run/Probe.java", "import static java.lang.Runtime.getRuntime; class Probe { Object p = "
                            + source + "; }").findings(), "images", "Probe.java"));
        }
        for (String source : List.of("commandLine = listOf(\"npm\", \"run\", \"launch\")",
                "setCommandLine(\"npm\", \"run\", \"launch\")", "val start = ::ProcessBuilder")) {
            test("web adversarial: alternate Kotlin API " + source, () -> expectFinding(webRepo()
                    .write("build.gradle.kts", "tasks.register<Exec>(\"probe\") { " + source + " }").findings(), "images"));
        }
        test("web adversarial: local review permits local script", () -> expectClean(
                webCommand(webRepo("local"), "shell", "npm ci --ignore-scripts", ".").findings()));
        test("web adversarial: local review cannot authorize a workflow", () -> expectFinding(
                webCommand(webRepo("local"), "workflow", "npm ci --ignore-scripts", ".").findings(), "images", "web.yml"));
        for (String body : List.of(
                "if (enabled) { workingDir(\"apps/shipping-web\") }; commandLine(\"node\", \"--test\")",
                "if (enabled) workingDir(\"apps/shipping-web\"); commandLine(\"node\", \"--test\")",
                "workingDir(\"apps/shipping-web\"); workingDir = file(other); commandLine(\"node\", \"--test\")",
                "setWorkingDir(other); commandLine(\"npm\", \"ci\", \"--ignore-scripts\")")) {
            test("web adversarial: conditional or overridden cwd " + body, () -> expectFinding(webRepo()
                    .write("build.gradle.kts", "tasks.register<Exec>(\"probe\") { " + body + " }").findings(), "images", "build.gradle.kts"));
        }
    }

    static String webCwd(String command) {
        return command.startsWith("node --test") || command.startsWith("npm run test:unit") ? "apps/shipping-web" : ".";
    }

    static Repo webCommand(Repo r, String site, String command, String cwd) throws IOException {
        String vector = Stream.of(command.split(" ")).map(s -> "\"" + s + "\"").collect(Collectors.joining(", "));
        return switch (site) {
            case "shell" -> r.write("tools/web.sh", (cwd.equals(".") ? "" : "cd " + cwd + " && ") + command + "\n");
            case "workflow" -> r.write(".github/workflows/web.yml", "jobs:\n  web:\n    steps:\n      - run: "
                    + command + "\n        working-directory: " + cwd + "\n");
            case "java" -> r.write("tools/run/Probe.java", "class Probe { Object p = new ProcessBuilder(" + vector
                    + ").directory(new java.io.File(\"" + cwd + "\")); }");
            case "kotlin" -> r.write("build.gradle.kts", "tasks.register<Exec>(\"probe\") { workingDir(\"" + cwd
                    + "\"); commandLine(" + vector + ") }\n");
            default -> throw new AssertionError(site);
        };
    }

    static Repo webRepo() throws Exception {
        return webRepo("ci");
    }

    static Repo webRepo(String platform) throws Exception {
        Repo r = Repo.clean();
        // Real entry identities and conditions; synthetic reviews/acceptance, never real tools or payloads executed.
        PolicyCheck.Json.JObject real = parse(Files.readString(checker().getParent().getParent().getParent()
                .resolve("security/allowlist.json"))).asObject();
        for (PolicyCheck.Json value : real.get("entries").asArray().items()) {
            PolicyCheck.Json.JObject e = value.asObject();
            if (!(e.text("coordinate").equals(platform.equals("ci") ? "nodejs.org/dist node" : "Node.js")
                    && !e.text("project").contains("maintenance")
                    || e.text("coordinate").equals("npm") && e.text("kind").equals("bundled")
                    && (platform.equals("ci") ? e.text("project").equals("ci") : e.text("project").contains("local build hosts"))
                    || e.text("coordinate").equals("typescript") && e.text("version").equals("5.9.3"))) continue;
            Map<String, Object> entry = r.entry(e.text("kind"), e.text("coordinate"), e.text("version"), e.text("artifact"), SHA_A);
            entry.put("project", e.text("project"));
            entry.put("conditions", e.get("conditions").asArray().stream().map(v -> ((PolicyCheck.Json.JString) v).value()).toList());
        }
        r.write("package.json", "{\"workspaces\":[\"apps/shipping-web\"],\"scripts\":{\"build:web\":\"tsc -p apps/shipping-web\"}}");
        r.write("apps/shipping-web/package.json", "{\"scripts\":{\"test:unit\":\"node --test\"}}");
        return r;
    }

    static final class Repo {
        final Path root;
        final List<Map<String, Object>> entries = new ArrayList<>();
        /** The acceptance record, or null for none. Without a digest, save() records the digest of the set as it is. */
        Map<String, Object> acceptance = map("status", "accepted");
        boolean pretty;

        Repo() throws IOException {
            root = temporaryDirectory();
        }

        /** org.example:lib 1.0.0 in a Gradle lockfile and the verification metadata: reviewed, approved and accepted. */
        static Repo clean() throws IOException {
            Repo repo = new Repo().write("gradle.lockfile", """
                    # This is a Gradle generated file for dependency locking.
                    org.example:lib:1.0.0=compileClasspath,runtimeClasspath
                    empty=annotationProcessor
                    """).write("gradle/verification-metadata.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <verification-metadata>
                       <configuration>
                          <verify-metadata>true</verify-metadata>
                       </configuration>
                       <components>
                          <component group="org.example" name="lib" version="1.0.0">
                             <artifact name="lib-1.0.0.jar">
                                <sha256 value="%s"/>
                             </artifact>
                             <artifact name="lib-1.0.0.pom">
                                <sha256 value="%s"/>
                             </artifact>
                          </component>
                       </components>
                    </verification-metadata>
                    """.formatted(SHA_A, SHA_B));
            repo.entry("maven", "org.example:lib", "1.0.0", "lib-1.0.0.jar", SHA_A);
            return repo;
        }

        Map<String, Object> lib() {
            return entries.getFirst();
        }

        /** Adds an approved, unexpired entry and writes its review record. */
        Map<String, Object> entry(String kind, String coordinate, String version, String artifact, String sha256)
                throws IOException {
            String review = "security/reviews/%s--%s--%s.md".formatted(kind, coordinate.replaceAll("[^A-Za-z0-9.]+", "-"),
                    version);
            write(review, "# %s %s\n\nVerdict: APPROVE. A synthetic review record.\n".formatted(coordinate, version));
            Map<String, Object> entry = map("kind", kind, "coordinate", coordinate, "version", version, "artifact", artifact,
                    "sha256", sha256, "verdict", "APPROVE", "conditions", List.of(), "reviewRecord", review,
                    "reviewedAt", "2026-11-01", "expiresAt", LATER);
            entries.add(entry);
            return entry;
        }

        /** Records the owner's acceptance of the set as it is now: the digest the checker computes for it. */
        Repo accept() throws IOException {
            acceptance.put("reviewedSetSha256", "");
            save();
            acceptance.put("reviewedSetSha256", new PolicyCheck(root, TODAY).entriesDigest());
            return save();
        }

        /** Writes security/allowlist.json, after recording the acceptance of the set as it is if none is recorded. */
        Repo save() throws IOException {
            if (acceptance != null && !acceptance.containsKey("reviewedSetSha256")) accept();
            Map<String, Object> allowlist = map("schemaVersion", 1, "acceptance", acceptance, "entries", entries);
            if (acceptance == null) allowlist.remove("acceptance");
            return write("security/allowlist.json", json(allowlist, pretty) + "\n");
        }

        PolicyCheck check() throws IOException {
            save();
            return new PolicyCheck(root, TODAY);
        }

        List<String> findings() throws IOException {
            return check().run().stream().map(f -> f.area() + ": " + f.message()).toList();
        }

        Repo write(String path, String text) throws IOException {
            return write(path, utf8(text));
        }

        Repo write(String path, byte[] bytes) throws IOException {
            Path file = root.resolve(path);
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
            return this;
        }

        /** A symbolic link at the path to the target, as given: relative to the link's directory, or absolute. */
        Repo link(String path, Path target) throws IOException {
            Path link = root.resolve(path);
            Files.createDirectories(link.getParent());
            Files.createSymbolicLink(link, target);
            return this;
        }

        Repo append(String path, String text) throws IOException {
            Files.writeString(root.resolve(path), text, StandardOpenOption.APPEND);
            return this;
        }

        /** Replaces text in a file; a case whose text is not there fails, so that no case passes without its change. */
        Repo edit(String path, String from, String to) throws IOException {
            String text = Files.readString(root.resolve(path));
            expect(text.contains(from), path + " does not contain " + show(from));
            return write(path, text.replace(from, to));
        }
    }

    /** Fixture JSON, written without the checker's code: strings that need no escape, numbers, booleans, null, lists, maps. */
    static String json(Object value, boolean pretty) {
        String comma = pretty ? ",\n  " : ",";
        return switch (value) {
            case null -> "null";
            case String s when s.chars().allMatch(c -> c >= 0x20 && c != '"' && c != '\\') -> "\"" + s + "\"";
            case String s -> throw new IllegalArgumentException("a fixture string that needs an escape: " + show(s));
            case Map<?, ?> m -> m.entrySet().stream()
                    .map(e -> json(e.getKey(), pretty) + (pretty ? " : " : ":") + json(e.getValue(), pretty))
                    .collect(Collectors.joining(comma, pretty ? "{\n  " : "{", pretty ? "\n}" : "}"));
            case List<?> l -> l.stream().map(v -> json(v, pretty)).collect(Collectors.joining(comma, "[", "]"));
            default -> value.toString();
        };
    }

    /** A map in the order given: key, value, key, value. Values may be null. */
    static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return map;
    }

    static Map<String, Object> reversedKeys(Map<String, Object> entry) {
        List<String> keys = new ArrayList<>(entry.keySet());
        Collections.reverse(keys);
        Map<String, Object> reversed = new LinkedHashMap<>();
        keys.forEach(key -> reversed.put(key, entry.get(key)));
        return reversed;
    }

    // ---- assertions and helpers ----

    static void expect(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    static void expectEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + show(String.valueOf(expected)) + " but was " + show(String.valueOf(actual)));
        }
    }

    static <T extends Throwable> T expectThrows(Class<T> type, Case body) {
        try {
            body.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) return type.cast(e);
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + e, e);
        }
        throw new AssertionError("expected " + type.getSimpleName() + ", but nothing was thrown");
    }

    /** Neither the standard output nor the standard error of the run repeats any of these texts. */
    static void expectNoContent(Run run, String... texts) {
        for (String text : texts) {
            expect(!run.output().contains(text) && !run.error().contains(text),
                    "the output repeats " + show(text) + ": " + show(run.toString()));
        }
    }

    static void expectClean(List<String> findings) {
        expect(findings.isEmpty(), "expected no findings, got " + findings);
    }

    /** Exactly one finding: in this area, with every part in its message. */
    static void expectOnly(List<String> findings, String area, String... parts) {
        expect(findings.size() == 1 && findings.getFirst().startsWith(area + ": ")
                && Stream.of(parts).allMatch(findings.getFirst()::contains),
                "expected one " + area + " finding with " + List.of(parts) + ", got " + findings);
    }

    /** At least one finding in this area with every part in its message. */
    static void expectFinding(List<String> findings, String area, String... parts) {
        expect(findings.stream().anyMatch(f -> f.startsWith(area + ": ") && Stream.of(parts).allMatch(f::contains)),
                "expected a " + area + " finding with " + List.of(parts) + ", got " + findings);
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** The text with control and non-ASCII characters escaped, for names and messages. */
    static String show(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c < 0x20 || c > 0x7e) out.append("\\u%04x".formatted((int) c)); else out.append(c);
        }
        return out.isEmpty() ? "(empty)" : out.toString();
    }

    /** A new temporary directory, deleted when the run ends. */
    static Path temporaryDirectory() throws IOException {
        Path dir = Files.createTempDirectory("policy-check-test-");
        temporary.add(dir);
        return dir;
    }

    static void delete(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
