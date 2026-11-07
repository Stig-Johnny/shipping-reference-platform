// PolicyCheck: the dependency allowlist gate.
//
// Run with the JDK alone, before any build tool executes:   java tools/policy/PolicyCheck.java [--report-only] [repo]
// Print the digest an acceptance must record:               java tools/policy/PolicyCheck.java --print-digest [repo]
//
// It reads the repository as data and never runs anything from it. CI runs the MAIN branch's copy of this file
// against a pull request's files, so a pull request cannot weaken the check that judges it (docs/adr/0007). Before it
// reads anything, it refuses a tree that holds a symbolic link anywhere, the root included, and then reads only regular
// files inside that tree. What it prints never repeats what a file holds: a value it must name (a coordinate, an Action,
// a URL, a path) is printed as safe characters only, and a file it cannot read is named with a fixed reason and, where
// known, a line and column.
//
// What it checks, against security/allowlist.json:
//   1. every Maven coordinate in every Gradle lockfile has an approved, unexpired entry with a review record;
//   2. every jar SHA-256 in gradle/verification-metadata.xml equals the reviewed SHA-256, where the review has one;
//   3. the Gradle wrapper JAR and the distribution checksum equal their reviewed values;
//   4. every GitHub Action is pinned to a full commit SHA that is on the allowlist;
//   5. every npm package in package-lock.json is allowlisted, comes from registry.npmjs.org and carries an integrity hash;
//   6. every file under a vendor/ directory is byte-identical (SHA-256) to an approved, reviewed artifact;
//   7. the owner has accepted exactly this reviewed set: every entry, field by field, and every review record, byte
//      for byte;
//   8. every container image that Testcontainers code, its configuration, a workflow or a script names is an approved
//      container-image entry, by its digest alone, in the only forms the reviews allow, so that nothing else can be
//      pulled: see checkContainerImages.
// Exit codes: 0 = clean, 1 = findings, 2 = the check could not complete (a broken check never reads as a pass).
// With --report-only, findings are printed and the exit code is 0 (new checks start report-only, then block).

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public final class PolicyCheck {

    static final Set<String> APPROVED = Set.of("APPROVE", "APPROVE-WITH-CONDITIONS");
    static final Pattern FULL_SHA = Pattern.compile("[0-9a-f]{40}");
    static final Pattern USES = Pattern.compile("^\\s*-?\\s*uses:\\s*['\"]?([^\\s'\"#]+)");

    /** One reviewed component, as written in security/allowlist.json. */
    record Entry(String kind, String coordinate, String version, String artifact, String sha256, String verdict,
                 String reviewRecord, String expiresAt) {
        boolean approved() { return APPROVED.contains(verdict); }
    }

    /** One problem, sorted by area and then message so the report is stable. */
    record Finding(String area, String message) implements Comparable<Finding> {
        @Override public int compareTo(Finding other) {
            int byArea = area.compareTo(other.area);
            return byArea != 0 ? byArea : message.compareTo(other.message);
        }
    }

    /**
     * The check cannot go on. A Stop's message is fixed text and values passed through {@link #safe}, so it is printed
     * as it is; the message of any other exception may quote a file, and only its type is printed.
     */
    static final class Stop extends IOException {
        Stop(String message) { super(message); }
    }

    static final String ALLOWLIST = "security/allowlist.json";
    /** At most this many characters of a value from a file are printed. */
    static final int SHOWN = 200;

    private final Path repo;
    private final LocalDate today;
    private final List<Json.JObject> rawEntries;
    private final List<Entry> entries;
    private final Json.JObject allowlist;
    /** Every review record as the allowlist writes it, and its normalized path inside the tree. */
    private final Map<String, Path> reviews = new HashMap<>();
    private final Set<Finding> findings = new TreeSet<>();

    PolicyCheck(Path repo, LocalDate today) throws IOException {
        this.repo = withoutLinks(repo);
        this.today = today;
        Path file = this.repo.resolve(ALLOWLIST);
        Json.JObject parsed = jsonObject(file);
        this.allowlist = parsed;
        this.rawEntries = member(file, () -> parsed.get("entries").asArray().stream().map(Json::asObject).toList());
        this.entries = rawEntries.stream().map(PolicyCheck::toEntry).toList();
        // Each review record is normalized and checked once, before any file is touched; every later probe and read
        // uses that same path, so nothing outside the tree, on the way a path is spelled, can change a result.
        for (Entry e : entries) {
            Path review;
            try {
                review = this.repo.resolve(e.reviewRecord()).normalize();
            } catch (InvalidPathException ex) {
                throw new Stop("refused: the review record of %s is not a path".formatted(named(e)));
            }
            if (!review.startsWith(this.repo)) {
                throw new Stop("refused: the review record of %s is outside the checked tree".formatted(named(e)));
            }
            reviews.put(e.reviewRecord(), review);
        }
    }

    /** An entry's coordinate and version, as a message may name them. */
    static String named(Entry e) {
        return safe(e.coordinate()) + " " + safe(e.version());
    }

    static Entry toEntry(Json.JObject o) {
        return new Entry(o.text("kind"), o.text("coordinate"), o.text("version"), o.text("artifact"), o.text("sha256"),
                o.text("verdict"), o.text("reviewRecord"), o.text("expiresAt"));
    }

    public static void main(String[] args) {
        boolean reportOnly = List.of(args).contains("--report-only");
        Path repo = Path.of(Stream.of(args).filter(a -> !a.startsWith("--")).findFirst().orElse("."));
        try {
            PolicyCheck check = new PolicyCheck(repo, LocalDate.now(java.time.ZoneOffset.UTC));
            if (List.of(args).contains("--print-digest")) {
                System.out.println(check.entriesDigest());
                System.exit(0);
            }
            Set<Finding> found = check.run();
            System.out.print(check.report(found, reportOnly));
            System.exit(found.isEmpty() || reportOnly ? 0 : 1);
        } catch (Stop e) {
            System.out.println("## Dependency allowlist: could not complete\n\n" + e.getMessage());
            System.exit(2);
        } catch (IOException | RuntimeException e) {
            System.out.println("## Dependency allowlist: could not complete\n\nan unexpected "
                    + e.getClass().getSimpleName());
            System.exit(2);
        }
    }

    Set<Finding> run() throws IOException {
        checkEntries();
        checkAcceptance();
        checkGradleLockfiles();
        checkVerificationMetadata();
        checkGradleWrapper();
        checkWorkflowActions();
        checkNpmLockfile();
        checkVendoredFiles();
        checkContainerImages();
        return findings;
    }

    // ---- 0. the allowlist itself ----

    void checkEntries() {
        for (Entry e : entries) {
            if (!APPROVED.contains(e.verdict()) && !"REJECT".equals(e.verdict())) {
                add("allowlist", "%s has an unknown verdict '%s'".formatted(named(e), safe(e.verdict())));
            }
            Path review = reviews.get(e.reviewRecord());
            if (e.reviewRecord().isBlank() || !Files.isRegularFile(review, LinkOption.NOFOLLOW_LINKS)) {
                add("allowlist", "%s: review record '%s' does not exist".formatted(named(e), safe(e.reviewRecord())));
            }
            if (e.approved()) {
                try {
                    if (LocalDate.parse(e.expiresAt()).isBefore(today)) {
                        add("allowlist", "%s: its review expired on %s".formatted(named(e), safe(e.expiresAt())));
                    }
                } catch (DateTimeParseException ex) {
                    add("allowlist", "%s: expiresAt '%s' is not a date".formatted(named(e), safe(e.expiresAt())));
                }
            }
        }
    }

    /**
     * The owner's acceptance is bound to the exact reviewed set: it records a digest of every entry and of every review
     * record, so changing, adding or removing an entry, or editing a review's text, scope or conditions afterwards,
     * needs the owner's acceptance again.
     */
    void checkAcceptance() throws IOException {
        Json.JObject acceptance = member(repo.resolve(ALLOWLIST), () -> allowlist.get("acceptance").asObject());
        String status = acceptance.text("status");
        if (!"accepted".equals(status)) {
            add("acceptance", "the owner has not accepted the reviewed set (acceptance.status is '%s')"
                    .formatted(safe(status)));
            return;
        }
        String accepted = acceptance.text("reviewedSetSha256");
        String current = entriesDigest();
        if (!current.equals(accepted)) {
            add("acceptance", "the reviewed set changed after the owner accepted it (reviewed-set digest %s, accepted %s)"
                    .formatted(current, accepted.isBlank() ? "none" : safe(accepted)));
        }
    }

    /**
     * SHA-256 (hex, UTF-8) over one line per entry, sorted and joined with '\n'. A line is the entry as canonical JSON
     * (every field, keys sorted, no whitespace), then '|', then the SHA-256 of its review record's bytes ("" when the
     * record is missing, which {@link #checkEntries} reports). The acceptance record itself is not part of it.
     */
    String entriesDigest() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Json.JObject entry : rawEntries) {
            Path review = reviews.get(entry.text("reviewRecord"));
            boolean present = !entry.text("reviewRecord").isBlank() && Files.isRegularFile(review, LinkOption.NOFOLLOW_LINKS);
            String reviewSha = present ? sha256(read(review)) : "";
            lines.add(Json.canonical(entry) + "|" + reviewSha);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", lines.stream().sorted().toList())
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- 1. Gradle lockfiles ----

    void checkGradleLockfiles() throws IOException {
        for (Path lockfile : files(p -> p.getFileName().toString().endsWith(".lockfile"))) {
            List<String> lines = readText(lockfile).lines().toList();
            for (int n = 0; n < lines.size(); n++) {
                String line = lines.get(n);
                if (line.isBlank() || line.startsWith("#") || line.startsWith("empty=")) continue;
                int equals = line.indexOf('=');
                String[] parts = equals < 0 ? new String[0] : line.substring(0, equals).split(":");
                if (parts.length != 3) {
                    // The file, the line number and a fixed reason: a finding never repeats what a file contains.
                    add("gradle", "%s:%d: unreadable line (expected group:name:version=configurations)"
                            .formatted(relative(lockfile), n + 1));
                    continue;
                }
                String coordinate = parts[0] + ":" + parts[1];
                requireApproved("gradle", "maven", coordinate, parts[2], relative(lockfile));
            }
        }
    }

    // ---- 2. checksums in gradle/verification-metadata.xml ----

    void checkVerificationMetadata() throws IOException {
        Path metadata = repo.resolve("gradle/verification-metadata.xml");
        if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) {
            if (!files(p -> p.getFileName().toString().endsWith(".lockfile")).isEmpty()) {
                add("gradle", "gradle/verification-metadata.xml is missing, so downloads are not checksum-verified");
            }
            return;
        }
        Document doc;
        try {
            doc = parseXmlSafely(readText(metadata));
        } catch (SAXParseException e) {
            throw new Stop("%s:%d:%d: unreadable XML (it must be well-formed, with no doctype declaration)"
                    .formatted(relative(metadata), e.getLineNumber(), e.getColumnNumber()));
        } catch (SAXException e) {
            throw new Stop("%s: unreadable XML (it must be well-formed, with no doctype declaration)"
                    .formatted(relative(metadata)));
        }
        Element config = (Element) doc.getElementsByTagName("configuration").item(0);
        if (config == null || !"true".equals(text(config, "verify-metadata"))) {
            add("gradle", "verification-metadata.xml does not verify metadata (<verify-metadata>true</verify-metadata>)");
        }
        NodeList components = doc.getElementsByTagName("component");
        for (int i = 0; i < components.getLength(); i++) {
            Element component = (Element) components.item(i);
            String coordinate = component.getAttribute("group") + ":" + component.getAttribute("name");
            String version = component.getAttribute("version");
            NodeList artifacts = component.getElementsByTagName("artifact");
            for (int j = 0; j < artifacts.getLength(); j++) {
                Element artifact = (Element) artifacts.item(j);
                String name = artifact.getAttribute("name");
                if (!name.endsWith(".jar")) continue; // POMs and module metadata are checksummed but carry no code
                Element sha = (Element) artifact.getElementsByTagName("sha256").item(0);
                if (sha == null) {
                    add("gradle", "%s:%s %s has no SHA-256 in verification-metadata.xml"
                            .formatted(safe(coordinate), safe(version), safe(name)));
                    continue;
                }
                // Match the artifact itself: a coordinate can have one entry per file (its jar and its POM).
                entries.stream()
                        .filter(e -> e.kind().equals("maven") && e.coordinate().equals(coordinate)
                                && e.version().equals(version) && e.artifact().equals(name) && !e.sha256().isBlank())
                        .filter(e -> !e.sha256().equalsIgnoreCase(sha.getAttribute("value")))
                        .forEach(e -> add("gradle", "%s:%s %s: downloaded SHA-256 %s differs from the reviewed %s".formatted(
                                safe(coordinate), safe(version), safe(name), safe(sha.getAttribute("value")),
                                safe(e.sha256()))));
            }
        }
    }

    // ---- 3. the Gradle wrapper ----

    void checkGradleWrapper() throws IOException {
        Path properties = repo.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (!Files.isRegularFile(properties, LinkOption.NOFOLLOW_LINKS)) return;
        Properties p = new Properties();
        try {
            p.load(new StringReader(readText(properties)));
        } catch (IllegalArgumentException e) {
            throw new Stop("%s: unreadable properties".formatted(relative(properties)));
        }
        String url = p.getProperty("distributionUrl", "");
        String distributionSha = p.getProperty("distributionSha256Sum", "");
        if (distributionSha.isBlank()) add("wrapper", "gradle-wrapper.properties has no distributionSha256Sum");
        String file = url.substring(url.lastIndexOf('/') + 1);
        Optional<Entry> distribution = entries.stream()
                .filter(e -> "gradle-distribution".equals(e.kind()) && e.artifact().equals(file)).findFirst();
        if (distribution.isEmpty() || !distribution.get().approved()) {
            add("wrapper", "%s is not an approved Gradle distribution on the allowlist".formatted(safe(file)));
        } else if (!distribution.get().sha256().equalsIgnoreCase(distributionSha)) {
            add("wrapper", "distributionSha256Sum %s differs from the reviewed %s"
                    .formatted(safe(distributionSha), safe(distribution.get().sha256())));
        }
        Path jar = repo.resolve("gradle/wrapper/gradle-wrapper.jar");
        String jarSha = sha256(read(jar));
        boolean reviewed = entries.stream().filter(e -> "gradle-distribution".equals(e.kind()) && e.approved())
                .anyMatch(e -> e.artifact().contains("gradle-wrapper.jar") && e.sha256().equalsIgnoreCase(jarSha));
        if (!reviewed) add("wrapper", "gradle-wrapper.jar SHA-256 %s matches no approved wrapper review".formatted(jarSha));
    }

    // ---- 4. GitHub Actions ----

    void checkWorkflowActions() throws IOException {
        for (Path workflow : files(p -> p.toString().contains(".github/workflows/")
                && (p.toString().endsWith(".yml") || p.toString().endsWith(".yaml")))) {
            List<String> lines = readText(workflow).lines().toList();
            for (int n = 0; n < lines.size(); n++) {
                Matcher m = USES.matcher(lines.get(n));
                if (!m.find()) continue;
                String uses = m.group(1);
                String where = "%s:%d".formatted(relative(workflow), n + 1);
                if (uses.startsWith("./")) continue; // a local action is this repository's own reviewed code
                if (uses.startsWith("docker://")) {
                    add("actions", "%s: container actions are not allowed (%s)".formatted(where, safe(uses)));
                    continue;
                }
                int at = uses.lastIndexOf('@');
                String action = at < 0 ? uses : uses.substring(0, at);
                String ref = at < 0 ? "" : uses.substring(at + 1);
                String repoName = String.join("/", List.of(action.split("/")).subList(0, Math.min(2, action.split("/").length)));
                if (!FULL_SHA.matcher(ref).matches()) {
                    add("actions", "%s: %s is not pinned to a full commit SHA".formatted(where, safe(uses)));
                } else if (entries.stream().noneMatch(e -> "github-action".equals(e.kind()) && e.approved()
                        && e.coordinate().equals(repoName) && e.artifact().equals(ref))) {
                    add("actions", "%s: %s@%s is not on the allowlist".formatted(where, safe(repoName), safe(ref)));
                }
            }
        }
    }

    // ---- 5. npm ----

    void checkNpmLockfile() throws IOException {
        for (Path lock : files(p -> p.getFileName().toString().equals("package-lock.json"))) {
            Json.JObject lockfile = jsonObject(lock);
            Json.JObject packages = member(lock, () -> lockfile.get("packages").asObject());
            for (Map.Entry<String, Json> pkg : packages.members().entrySet()) {
                String key = pkg.getKey();
                if (!key.contains("node_modules/")) continue; // "" is the workspace root, the rest are local workspaces
                Json.JObject info = member(lock, () -> pkg.getValue().asObject());
                if (info.has("link") && "true".equals(info.text("link"))) continue;
                String name = key.substring(key.lastIndexOf("node_modules/") + "node_modules/".length());
                String version = info.text("version");
                String where = "%s %s@%s".formatted(relative(lock), safe(name), safe(version));
                if (!info.text("resolved").startsWith("https://registry.npmjs.org/")) {
                    add("npm", "%s is not resolved from registry.npmjs.org (%s)"
                            .formatted(where, safe(info.text("resolved"))));
                }
                if (!info.text("integrity").startsWith("sha512-")) add("npm", where + " has no sha512 integrity hash");
                requireApproved("npm", "npm", name, version, relative(lock));
            }
        }
    }

    // ---- 6. vendored files ----

    /** A file someone else wrote, committed under a vendor/ directory, must be byte-identical to a reviewed artifact. */
    void checkVendoredFiles() throws IOException {
        for (Path file : files(p -> repo.relativize(p).toString().contains("vendor/"))) {
            String sha = sha256(read(file));
            boolean reviewed = entries.stream().anyMatch(e -> e.approved() && e.kind().startsWith("vendored")
                    && e.sha256().equalsIgnoreCase(sha));
            if (!reviewed) {
                add("vendored", "%s (SHA-256 %s) matches no approved vendored artifact".formatted(relative(file), sha));
            }
        }
    }

    // ---- 8. container images ----

    /*
     * Fail closed within the syntax it reads: an image may be named only by an approved digest, and only in forms this
     * check can read. It reads the files that can name an image for Testcontainers or for the engine: every file under
     * a project's src/ directory (sources and resources), every Java, Kotlin and Groovy file (the Gradle build scripts
     * included), gradle.properties, the workflows and every shell script (by extension or #! line). It normalises before it matches, as the reader of each file
     * would: a Java source after its Unicode escapes; Java, Kotlin and Groovy without their comments, with their string
     * literals in every form (Java text blocks, Kotlin raw strings, Groovy single, double and triple quotes) and
     * escapes (octal, Unicode, line continuations) decoded; shell text as bash splits it into words, with quotes, backslash
     * escapes and adjacent parts joined, the words inside command substitutions, backquotes, ${...} values, heredocs
     * and strings run by bash -c or eval read as words too. Documents, the review records and this checker with its
     * tests are not read: naming an image there uses nothing. What it refuses:
     *   - a digest reference ([registry/]name@sha256:<digest>) that no approved container-image entry has, a tag next
     *     to the digest, and a digest that is not 64 lowercase hexadecimal digits;
     *   - in sources and build scripts: any reference to Testcontainers or docker-java (an import, a star import or a
     *     fully qualified name) outside the database harness (HARNESS), so that every container is made there, from
     *     the reviewed image, and checked there; DockerImageName.parse of anything but one string literal that holds a
     *     digest, as a method reference or by a static import; a Testcontainers JDBC URL (jdbc:tc:), which names an
     *     image by tag; every API that derives, builds or resolves an image name, pulls or builds an image, runs
     *     another container, copies into one, binds the host, reaches the host, or changes how a container is created
     *     (FORBIDDEN_API: the conditions of the Testcontainers and runner reviews), in every file it reads as code; a container
     *     runtime named in code or in a string, which is how a build script or a test would run one as a command;
     *   - in the harness: any container but org.testcontainers.postgresql.PostgreSQLContainer (the deprecated one in
     *     .containers included), one made from anything but DockerImageName.parse or a DockerImageName that the
     *     harness parses, a subclass and a constructor reference;
     *   - a testcontainers.properties file anywhere and a Testcontainers service file, and any setting that gives
     *     Testcontainers an image (*.container.image, hub.image.name.prefix, image.substitutor, TESTCONTAINERS_*IMAGE*);
     *   - in workflows and scripts: docker as a command word (by its path, after the normalisation above) with anything
     *     but the reads, removals and the one pull that the staging, the checks and the cleanup need (DOCKER_READS), a
     *     pull of anything but one image by digest, docker's global options, docker in a word it cannot read as a
     *     command (a value, an expansion), other container runtimes, $'...' quoting, YAML strings with escapes, and job
     *     or service containers as plain, quoted, flow, escaped or explicit YAML keys.
     * What it cannot read stays outside it: a command or a name assembled from variables or expressions at run time,
     * reflection, and a library that starts a container of its own. A comment in a workflow, a script, a Java or a
     * Kotlin source is not read as code; in a Groovy file, whose slashy strings this check does not parse, the text is
     * read with its comments too. The checker's own directory (CHECKER_DIRECTORY) is not read: its tests hold every
     * refused spelling as data, and a change there is reviewed as a change to the checker.
     */

    /** This checker and its tests, whose fixtures hold every refused spelling as data: reviewed as the checker. */
    static final String CHECKER_DIRECTORY = "tools/policy/";
    /** The database harness: the only source that may use Testcontainers or docker-java. */
    static final String HARNESS = "apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/ReviewedPostgres.java";
    static final String DIGEST = "@sha256:";
    static final Pattern DIGEST_REFERENCE = Pattern.compile("[^\\s\"'`()\\[\\]{}<>,;=|&]*" + DIGEST
            + "[^\\s\"'`()\\[\\]{}<>,;=|&]*");
    static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    static final Pattern CONTAINER_LIBRARY = Pattern.compile(
            "\\b(?:org\\s*\\.\\s*testcontainers|com\\s*\\.\\s*github\\s*\\.\\s*dockerjava)\\s*\\.");
    static final Pattern PARSE_CALL = Pattern.compile("\\bDockerImageName\\s*\\.\\s*parse\\s*\\(");
    static final Pattern ONE_LITERAL = Pattern.compile("\\s*\"([^\"\\\\\\n]*)\"\\s*\\)");
    static final Pattern PARSE_REFERENCE = Pattern.compile("\\bDockerImageName\\s*::\\s*parse\\b");
    static final Pattern PARSE_IMPORT = Pattern.compile("\\bimport\\s+static\\s+[\\w.]*\\bDockerImageName\\s*\\.");
    static final Pattern IMAGE_DECLARATION = Pattern.compile(
            "\\bDockerImageName\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(?:[\\w.]*\\.)?DockerImageName\\s*\\.\\s*parse\\s*\\(");
    static final Pattern NEW_CONTAINER = Pattern.compile(
            "\\bnew\\s+(?:[A-Za-z_$][\\w$]*\\s*\\.\\s*)*([A-Za-z_$][\\w$]*Container)\\b\\s*(?:<[^<>()]*>)?\\s*\\(");
    static final Pattern CONTAINER_REFERENCE = Pattern.compile("\\b([A-Za-z_$][\\w$]*Container)\\s*::\\s*new\\b");
    static final Pattern EXTENDS_CONTAINER = Pattern.compile(
            "\\bextends\\s+(?:[A-Za-z_$][\\w$]*\\s*\\.\\s*)*([A-Za-z_$][\\w$]*Container)\\b");
    static final Pattern DEPRECATED_CONTAINER = Pattern.compile(
            "\\borg\\s*\\.\\s*testcontainers\\s*\\.\\s*containers\\s*\\.\\s*PostgreSQLContainer\\b");
    static final Pattern NEW_IMAGE_NAME = Pattern.compile("\\bnew\\s+(?:[A-Za-z_$][\\w$]*\\s*\\.\\s*)*DockerImageName\\b");
    static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");
    static final String REVIEWED_CONTAINER = "PostgreSQLContainer";
    static final Pattern FORBIDDEN_API = Pattern.compile("\\b(withTag|withRegistry|withRepository|"
            + "asCompatibleSubstituteFor|ImageFromDockerfile|RemoteDockerImage|setDockerImageName|DockerComposeContainer|"
            + "ComposeContainer|withFileSystemBind|addFileSystemBind|withClasspathResourceMapping|withCopyToContainer|"
            + "withCopyFileToContainer|copyFileToContainer|exposeHostPorts|withAccessToHost|withPrivilegedMode|withReuse|"
            + "withNetworkMode|withCreateContainerCmdModifier|withVolumesFrom|createContainerCmd|pullImageCmd|"
            + "buildImageCmd|loadImageCmd|createImageCmd|copyArchiveToContainerCmd|pushImageCmd|tagImageCmd|"
            + "runInsideDocker|checkAndPullImage)\\b");
    static final Pattern TC_JDBC_URL = Pattern.compile("jdbc:tc:");
    static final Pattern IMAGE_SETTING = Pattern.compile("TESTCONTAINERS_[A-Z0-9_]*IMAGE[A-Z0-9_]*"
            + "|\\b[a-z0-9]+\\.container\\.image\\b|\\bhub\\.image\\.name\\.prefix\\b|\\bimage\\.substitutor\\b");
    /** A container runtime in code: alone, by its path, or at the start of a command. */
    static final Pattern RUNTIME_WORD = Pattern.compile(
            "(?<![\\w.-])(?:[\\w./-]*/)?(docker|docker-compose|podman|nerdctl|buildah)(?![\\w./-])");
    /** A normalised shell word that is a container runtime, by its name or its path. */
    static final Pattern RUNTIME_NAME = Pattern.compile("(?:.*/)?(docker|docker-compose|podman|nerdctl|buildah)");
    /** A container runtime inside a normalised word: not a path component, a file name, a registry or another name. */
    static final Pattern BOUNDED_RUNTIME = Pattern.compile(
            "(?<![A-Za-z0-9_.-])(docker-compose|docker|podman|nerdctl|buildah)(?![A-Za-z0-9_./-])(?!://)");
    /** In Groovy, whose slashy strings (/.../, $/.../$) this check does not parse: a runtime between any delimiters. */
    static final Pattern GROOVY_RUNTIME = Pattern.compile(
            "(?<![A-Za-z0-9_])(docker-compose|docker|podman|nerdctl|buildah)(?![A-Za-z0-9_])");
    /** A Kotlin or Groovy string template whose expression holds a string literal: text assembled in the string. */
    static final Pattern LITERAL_TEMPLATE = Pattern.compile("\\$\\{[^}\\n]*[\"']");
    /** A GitHub expression function that builds text: the shell would run text that this check does not see. */
    static final Pattern EXPRESSION_TEXT = Pattern.compile("\\$\\{\\{[^}]*\\b(format|join|toJSON|fromJSON)\\s*\\(");
    static final Pattern ASSIGNMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*\\+?=.*", Pattern.DOTALL);
    /** Words after which the next word is still the command: assignments and the wrappers that run their arguments. */
    static final Set<String> WRAPPERS = Set.of("sudo", "exec", "env", "command", "builtin", "nohup", "time", "nice",
            "xargs", "eval", "then", "do", "else", "elif", "if", "while", "until", "!", "{");
    static final Pattern JOB_CONTAINER = Pattern.compile(
            "(?:^|[\\s{,\\[])(?:\"(?:services|container)\"|'(?:services|container)'|services|container)\\s*:");
    static final Pattern ESCAPED_KEY = Pattern.compile("\"[^\"\\n]*\\\\[^\"\\n]*\"\\s*:");
    static final Pattern EXPLICIT_KEY = Pattern.compile("^\\s*(?:-\\s+)?\\?(?:\\s|$)");
    static final Pattern COMMAND_WORD = Pattern.compile("[a-z][a-z-]*");
    /** docker's management commands, whose second word is the command. */
    static final Set<String> DOCKER_GROUPS = Set.of("builder", "buildx", "checkpoint", "compose", "config", "container",
            "context", "image", "manifest", "network", "node", "plugin", "secret", "service", "stack", "swarm", "system",
            "trust", "volume");
    /** What the staging, the checks and the cleanup need; a pull is checked on its own. */
    static final Set<String> DOCKER_READS = Set.of("version", "info", "images", "inspect", "ps", "rm", "events",
            "image ls", "image inspect", "container ls", "container inspect", "container rm", "network ls",
            "network inspect", "network rm", "volume ls", "volume inspect", "volume rm", "manifest inspect");
    static final Set<String> TEXT_RESOURCES = Set.of(".java", ".kt", ".kts", ".groovy", ".properties", ".yml", ".yaml",
            ".xml", ".json", ".sql", ".txt", ".conf", ".cfg", ".sh", ".env");

    void checkContainerImages() throws IOException {
        for (Path file : files(this::namesImages)) {
            String rel = repo.relativize(file).toString();
            String name = file.getFileName().toString();
            if (name.equals("testcontainers.properties") || name.equals(".testcontainers.properties")
                    || rel.contains("META-INF/services/org.testcontainers.")) {
                add("images", "%s: a Testcontainers configuration file; the reviews allow none (no "
                        .formatted(relative(file)) + "testcontainers.properties, no service file)");
                continue;
            }
            if (!readable(rel, name)) continue;
            Optional<Language> language = Language.of(name);
            String text = language.orElse(null) == Language.JAVA ? unicodeEscapesDecoded(readText(file)) : readText(file);
            SourceViews views = language.map(l -> SourceViews.of(text, l)).orElse(null);
            List<String> texts = views == null ? List.of(text) : List.of(text, views.code());
            for (String view : texts) imageNames(file, text, view, 0, 0);
            if (views != null) {
                for (Literal literal : views.literals()) imageNames(file, text, literal.value(), literal.offset(), 1);
            }
            boolean workflow = rel.startsWith(".github/workflows/");
            boolean script = workflow || name.endsWith(".sh") || text.startsWith("#!");
            if (script) checkShell(file, text, workflow);
            if (views != null) checkImageCode(file, text, views, language.orElseThrow(), rel.equals(HARNESS));
            if (views == null && !script && ("/" + rel).contains("/src/")) {
                // A resource is data that code can read and run: no container runtime in it either.
                for (Matcher m = RUNTIME_WORD.matcher(text); m.find(); ) {
                    add("images", "%s: names a container runtime (%s) in a resource, which code can read and run"
                            .formatted(at(file, text, m.start()), m.group(1)));
                }
            }
        }
    }

    /**
     * Digest references, Testcontainers JDBC URLs and image settings in a view of a file: the text itself, its code
     * without comments, or a string literal's decoded value starting at an offset of the text (literal = 1, so that a
     * line of the value counts from the literal's own).
     */
    void imageNames(Path file, String text, String view, int start, int literal) {
        for (Matcher m = DIGEST_REFERENCE.matcher(view); m.find(); ) {
            String where = place(file, text, view, start, literal, m.start());
            imageProblem(m.group()).ifPresent(problem ->
                    add("images", "%s: %s %s".formatted(where, safe(m.group()), problem)));
        }
        for (Matcher m = TC_JDBC_URL.matcher(view); m.find(); ) {
            add("images", place(file, text, view, start, literal, m.start())
                    + ": a Testcontainers JDBC URL (jdbc:tc:) names an image by tag");
        }
        for (Matcher m = IMAGE_SETTING.matcher(view); m.find(); ) {
            add("images", "%s: %s configures an image for Testcontainers".formatted(
                    place(file, text, view, start, literal, m.start()), safe(m.group())));
        }
    }

    /** The place of an offset of a view: in the text, or within a literal's value that starts at an offset of it. */
    String place(Path file, String text, String view, int start, int literal, int offset) {
        if (literal == 0) return at(file, text, offset);
        int line = lineOf(text, start) + (int) view.substring(0, offset).chars().filter(c -> c == '\n').count();
        return relative(file) + ":" + line;
    }

    static int lineOf(String text, int offset) {
        return 1 + (int) text.substring(0, Math.min(offset, text.length())).chars().filter(c -> c == '\n').count();
    }

    /**
     * The files that can name an image: sources and resources, build scripts, workflows and shell scripts, those
     * without the .sh extension included, by their #! line.
     */
    boolean namesImages(Path p) {
        String rel = repo.relativize(p).toString();
        String name = p.getFileName().toString();
        return ("/" + rel).contains("/src/") || name.endsWith(".sh") || name.equals("gradle.properties")
                || name.endsWith("testcontainers.properties")
                || Language.of(name).isPresent() && !rel.startsWith(CHECKER_DIRECTORY)
                || rel.startsWith(".github/workflows/") && (name.endsWith(".yml") || name.endsWith(".yaml"))
                || !rel.startsWith(CHECKER_DIRECTORY) && startsWithShebang(p);
    }

    /** Whether a file starts with #!; one that cannot be opened is read, so that the read stops the check. */
    static boolean startsWithShebang(Path p) {
        try (InputStream in = Files.newInputStream(p, LinkOption.NOFOLLOW_LINKS)) {
            byte[] head = in.readNBytes(2);
            return head.length == 2 && head[0] == '#' && head[1] == '!';
        } catch (IOException e) {
            return true;
        }
    }

    /** A file read as text: a known text format; under src/, a binary file such as an image names no container. */
    static boolean readable(String rel, String name) {
        return !("/" + rel).contains("/src/") || TEXT_RESOURCES.stream().anyMatch(name::endsWith);
    }

    /**
     * Why a digest reference is not an approved image, or empty when it is one: [registry/]name@sha256:<digest>,
     * where the name with its registry is the coordinate of an approved container-image entry and the digest is its
     * sha256. Docker Hub's spellings are one registry: docker.io, index.docker.io and none.
     */
    Optional<String> imageProblem(String reference) {
        int at = reference.indexOf(DIGEST);
        String name = reference.substring(0, at);
        String digest = reference.substring(at + DIGEST.length());
        if (!SHA256_HEX.matcher(digest).matches()) {
            return Optional.of("is not a sha256 digest of 64 lowercase hexadecimal digits");
        }
        if (name.indexOf(':', name.lastIndexOf('/') + 1) >= 0) {
            return Optional.of("names a tag next to its digest; an image is named by its digest alone");
        }
        String coordinate = imageCoordinate(name);
        List<Entry> matching = entries.stream().filter(e -> "container-image".equals(e.kind())
                && e.coordinate().equals(coordinate) && e.sha256().equals(digest)).toList();
        if (matching.stream().anyMatch(Entry::approved)) return Optional.empty();
        return Optional.of(matching.isEmpty() ? "has no review on the allowlist"
                : "was REJECTED in " + safe(matching.getFirst().reviewRecord()));
    }

    /** An image name as the allowlist writes it: registry/path, with Docker Hub as docker.io and its library/. */
    static String imageCoordinate(String name) {
        int slash = name.indexOf('/');
        String first = slash < 0 ? "" : name.substring(0, slash);
        boolean registry = first.contains(".") || first.contains(":") || first.equals("localhost");
        String host = registry ? first : "docker.io";
        String path = registry ? name.substring(slash + 1) : name;
        if (host.equals("index.docker.io") || host.equals("registry-1.docker.io")) host = "docker.io";
        if (host.equals("docker.io") && !path.contains("/")) path = "library/" + path;
        return host + "/" + path;
    }

    /**
     * A source or build script, in its text and its code without comments: image names, the forbidden APIs and
     * container runtimes in all of it, Testcontainers and docker-java nowhere but in the harness, and in the harness the
     * container it makes. Its string literals, decoded, are read as shell words for a container runtime.
     */
    void checkImageCode(Path file, String text, SourceViews views, Language language, boolean harness) {
        for (String view : List.of(text, views.code())) {
            codeRules(file, text, view, harness);
        }
        // A runtime in code: in the code without comments, and for Kotlin and Groovy, whose string templates and slashy
        // strings this check does not parse, in the text with its comments too.
        List<String> runtimeViews = language == Language.JAVA ? List.of(views.code()) : List.of(text, views.code());
        for (String view : runtimeViews) {
            for (Matcher m = RUNTIME_WORD.matcher(view); m.find(); ) runtime(file, lineOf(text, m.start()), m.group(1));
        }
        if (language == Language.GROOVY) {
            for (Matcher m = GROOVY_RUNTIME.matcher(text); m.find(); ) runtime(file, lineOf(text, m.start()), m.group(1));
        }
        if (language != Language.JAVA) {
            find(file, text, text, LITERAL_TEMPLATE, "a string template that holds a string literal assembles text this "
                    + "check cannot read; write the text plainly");
        }
        for (Literal literal : views.literals()) {
            for (Shell.Word word : Shell.flatten(Shell.read(literal.value(), lineOf(text, literal.offset())), 0)) {
                if (word.operator()) continue;
                Matcher name = RUNTIME_NAME.matcher(word.text());
                Matcher bounded = BOUNDED_RUNTIME.matcher(word.text());
                if (name.matches()) runtime(file, word.line(), name.group(1));
                else if (bounded.find()) runtime(file, word.line(), bounded.group(1));
            }
        }
    }

    void runtime(Path file, int line, String runtime) {
        add("images", "%s:%d: runs a container runtime as a command (%s)".formatted(relative(file), line, runtime));
    }

    /** The rules on code, applied to the text and to the code without comments alike. */
    void codeRules(Path file, String text, String view, boolean harness) {
        for (Matcher m = PARSE_CALL.matcher(view); m.find(); ) {
            Matcher literal = ONE_LITERAL.matcher(view).region(m.end(), view.length());
            if (!literal.lookingAt()) {
                add("images", "%s: DockerImageName.parse takes something other than one string literal"
                        .formatted(at(file, text, m.start())));
            } else if (!literal.group(1).contains(DIGEST)) {
                add("images", "%s: %s is not pinned by digest".formatted(at(file, text, m.start()), safe(literal.group(1))));
            }
        }
        find(file, text, view, PARSE_REFERENCE, "DockerImageName::parse makes image names from values this check cannot read");
        find(file, text, view, PARSE_IMPORT, "a static import of DockerImageName hides the calls this check reads");
        find(file, text, view, NEW_IMAGE_NAME, "new DockerImageName builds an image name that this check cannot read");
        find(file, text, view, DEPRECATED_CONTAINER, "org.testcontainers.containers.PostgreSQLContainer is the deprecated "
                + "container; the review approves org.testcontainers.postgresql.PostgreSQLContainer");
        for (Matcher m = FORBIDDEN_API.matcher(view); m.find(); ) {
            add("images", "%s: %s is forbidden by the Testcontainers and runner reviews".formatted(
                    at(file, text, m.start()), m.group(1)));
        }
        if (!harness) {
            find(file, text, view, CONTAINER_LIBRARY, "uses Testcontainers or docker-java; only ReviewedPostgres may ("
                    + HARNESS + "), so that every container is made and checked there");
            for (Matcher m = NEW_CONTAINER.matcher(view); m.find(); ) {
                if (REVIEWED_CONTAINER.equals(m.group(1))) {
                    add("images", "%s: only ReviewedPostgres makes the PostgreSQLContainer".formatted(
                            at(file, text, m.start())));
                }
            }
            return;
        }
        Set<String> parsedHere = new TreeSet<>();
        for (Matcher m = IMAGE_DECLARATION.matcher(view); m.find(); ) parsedHere.add(m.group(1));
        for (Matcher m = NEW_CONTAINER.matcher(view); m.find(); ) {
            String where = at(file, text, m.start());
            if (!REVIEWED_CONTAINER.equals(m.group(1))) {
                add("images", "%s: %s is not the reviewed org.testcontainers.postgresql.PostgreSQLContainer"
                        .formatted(where, safe(m.group(1))));
                continue;
            }
            int close = closing(view, m.end() - 1);
            String argument = close < 0 ? "" : view.substring(m.end(), close).strip();
            boolean parsed = PARSE_CALL.matcher(argument).lookingAt()
                    || argument.startsWith("org.testcontainers.utility.") && PARSE_CALL.matcher(argument).find();
            boolean declared = IDENTIFIER.matcher(argument).matches() && parsedHere.contains(argument);
            if (!parsed && !declared) {
                add("images", "%s: PostgreSQLContainer is made from something other than DockerImageName.parse, or a "
                        .formatted(where) + "DockerImageName this file parses");
            } else if (close >= 0 && view.substring(close + 1).stripLeading().startsWith("{")) {
                add("images", "%s: an anonymous subclass of PostgreSQLContainer".formatted(where));
            }
        }
        for (Matcher m = CONTAINER_REFERENCE.matcher(view); m.find(); ) {
            add("images", "%s: %s::new makes containers from values this check cannot read"
                    .formatted(at(file, text, m.start()), safe(m.group(1))));
        }
        for (Matcher m = EXTENDS_CONTAINER.matcher(view); m.find(); ) {
            add("images", "%s: extends %s; a test may not subclass a container".formatted(at(file, text, m.start()),
                    safe(m.group(1))));
        }
    }

    /**
     * A workflow or a shell script: its YAML keys and strings line by line, and its shell words as bash reads them. docker
     * as a command word may only read, remove and pull by digest; any other runtime, and docker in a word that is not a
     * command, is refused.
     */
    void checkShell(Path file, String text, boolean workflow) {
        if (workflow) {
            List<String> lines = text.lines().toList();
            for (int n = 0; n < lines.size(); n++) {
                String line = withoutComment(lines.get(n));
                String where = "%s:%d".formatted(relative(file), n + 1);
                if (JOB_CONTAINER.matcher(line).find()) {
                    add("images", where + ": a job or service container; the runner review allows Testcontainers only");
                }
                if (ESCAPED_KEY.matcher(line).find() || EXPLICIT_KEY.matcher(line).find()) {
                    add("images", where + ": a workflow key that is escaped or explicit (?); write keys plainly");
                }
                if (yamlStringWithEscape(line)) {
                    add("images", where + ": a double-quoted YAML string with an escape; write it plainly, so that this "
                            + "check reads what the runner runs");
                }
            }
            find(file, text, text, EXPRESSION_TEXT, "a GitHub expression that builds text the runner runs; write the text "
                    + "plainly, so that this check reads it");
        }
        List<Shell.Word> read = Shell.read(text, 1, workflow);
        commandSubstitutions(file, read);
        List<Shell.Word> words = Shell.flatten(read, 0);
        for (int k = 0; k < words.size(); k++) {
            Shell.Word word = words.get(k);
            String where = "%s:%d".formatted(relative(file), word.line());
            if (word.operator()) {
                if (word.text().equals(Shell.ANSI)) {
                    add("images", where + ": $'...' quoting; write the text plainly, so that this check reads it");
                } else if (word.text().equals(Shell.TOO_DEEP)) {
                    add("images", where + ": shell text nested too deeply for this check to read");
                }
                continue;
            }
            Matcher name = RUNTIME_NAME.matcher(word.text());
            if (!name.matches()) {
                Matcher bounded = BOUNDED_RUNTIME.matcher(word.text());
                if (bounded.find()) {
                    add("images", "%s: %s in a word this check cannot read as a command (a value, an expansion or part "
                            .formatted(where, bounded.group(1)) + "of a word)");
                }
                continue;
            }
            if (!name.group(1).equals("docker")) {
                add("images", "%s: %s is not the reviewed engine".formatted(where, name.group(1)));
                continue;
            }
            if (serviceUnit(words, k)) continue;
            List<String> rest = new ArrayList<>();
            for (int j = k + 1; j < words.size() && !words.get(j).operator(); j++) rest.add(words.get(j).text());
            String command = rest.isEmpty() ? "" : commandOf(rest);
            if (command.equals("pull") || command.equals("image pull")) {
                if (!pullsOneDigest(rest.subList(command.split(" ").length, rest.size()))) {
                    add("images", where + ": docker pull of something other than one image by digest");
                }
            } else if (!DOCKER_READS.contains(command)) {
                add("images", "%s: docker %s is not an allowed use of the reviewed engine (allowed: pull by "
                        .formatted(where, command.isEmpty() ? "without a command this check can read" : safe(command))
                        + "digest, and reads and removals)");
            }
        }
    }

    /**
     * A command that a command substitution names, as in $(printf ...) run: the shell runs text that this check cannot
     * read. Within a substitution its own first word is a command; after it, the outer command goes on.
     */
    void commandSubstitutions(Path file, List<Shell.Word> words) {
        ArrayDeque<Boolean> outer = new ArrayDeque<>();
        boolean commandNext = true;
        for (Shell.Word w : words) {
            if (w.operator()) {
                switch (w.text()) {
                    case "$(", "(" -> {
                        outer.push(commandNext);
                        commandNext = true;
                    }
                    case ")" -> commandNext = outer.isEmpty() || outer.pop();
                    default -> commandNext = true;
                }
                continue;
            }
            if (!commandNext) continue;
            if (ASSIGNMENT.matcher(w.text()).matches() || WRAPPERS.contains(w.text())) continue;
            if (w.text().startsWith("$()")) {
                add("images", "%s:%d: a command that a command substitution names; this check cannot read what it runs"
                        .formatted(relative(file), w.line()));
            }
            commandNext = false;
        }
    }

    /** docker as the unit that systemctl manages (systemctl restart docker), not as a command. */
    static boolean serviceUnit(List<Shell.Word> words, int k) {
        return k >= 2 && !words.get(k - 1).operator() && !words.get(k - 2).operator()
                && words.get(k - 2).text().equals("systemctl") && COMMAND_WORD.matcher(words.get(k - 1).text()).matches();
    }

    /** Whether a line holds a double-quoted YAML scalar (one that starts a value) with a backslash in it. */
    static boolean yamlStringWithEscape(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != '"') continue;
            String before = line.substring(0, i).stripTrailing();
            boolean startsValue = before.isEmpty() || before.endsWith(":") || before.endsWith("-") || before.endsWith("[")
                    || before.endsWith("{") || before.endsWith(",") || before.endsWith("?");
            int end = i + 1;
            boolean escaped = false;
            while (end < line.length() && line.charAt(end) != '"') {
                if (line.charAt(end) == '\\') {
                    escaped = true;
                    end++;
                }
                end++;
            }
            if (startsValue && escaped) return true;
            i = end;
        }
        return false;
    }

    /** The command a docker invocation names: its first word, or two for a management command; "" when unreadable. */
    static String commandOf(List<String> words) {
        String first = words.getFirst();
        if (!COMMAND_WORD.matcher(first).matches()) return "";
        if (!DOCKER_GROUPS.contains(first)) return first;
        return words.size() > 1 && COMMAND_WORD.matcher(words.get(1)).matches() ? first + " " + words.get(1) : "";
    }

    /** The arguments of a pull, up to the end of its command: only --quiet and --platform, then one digest reference. */
    static boolean pullsOneDigest(List<String> arguments) {
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String word = arguments.get(i);
            if (word.equals("--platform")) {
                i++;
            } else if (word.startsWith("--platform=") || word.equals("--quiet") || word.equals("-q")) {
                continue;
            } else if (word.startsWith("-")) {
                return false;
            } else {
                positional.add(word);
            }
        }
        return positional.size() == 1 && positional.getFirst().contains(DIGEST);
    }

    /** A line of a workflow or a script without its comment: from an unquoted # at its start or after whitespace. */
    static String withoutComment(String line) {
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !dbl) single = !single;
            else if (c == '"' && !single) dbl = !dbl;
            else if (c == '#' && !single && !dbl && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    /**
     * Shell text as bash splits it into words, as far as check 8 needs it: quotes removed and adjacent parts joined,
     * backslash escapes and line continuations applied, comments dropped. Command substitutions ($(...), backquotes),
     * also inside double quotes, give their words as words of their own between the operators $( and ), and $() in the
     * word; a ${...} gives the value it may expand to (what follows its name and operator) as a word, and ${} in the
     * word. $'...' gives the operator ANSI.
     * Operators (; & | ( ) < > and line ends) are words of their own, so that a command ends at one.
     */
    static final class Shell {
        /** A word or an operator, and the line it starts on. */
        record Word(String text, int line, boolean operator) {}

        static final String ANSI = "$'";
        static final String TOO_DEEP = "...";
        static final int DEEPEST = 6;

        private final String s;
        private final List<Word> words = new ArrayList<>();
        private final int depth;
        /** A workflow: YAML strips a block's indentation, so a line continuation joins the next line's text. */
        private final boolean yaml;
        private StringBuilder word;
        private int wordLine;
        private int line;
        private int i;

        private Shell(String s, int line, int depth, boolean yaml) {
            this.s = s;
            this.line = line;
            this.depth = depth;
            this.yaml = yaml;
        }

        static List<Word> read(String text, int line) {
            return read(text, line, 0, false);
        }

        static List<Word> read(String text, int line, boolean yaml) {
            return read(text, line, 0, yaml);
        }

        private static List<Word> read(String text, int line, int depth, boolean yaml) {
            Shell shell = new Shell(text, line, depth, yaml);
            if (depth > DEEPEST) {
                shell.words.add(new Word(TOO_DEEP, line, true));
                return shell.words;
            }
            shell.read();
            return shell.words;
        }

        /**
         * The words, with every word that holds quotes, escapes or blanks (a string that bash -c or eval would run, or
         * that a quote joined) read again as shell text, between the operators ( and ).
         */
        static List<Word> flatten(List<Word> words, int depth) {
            List<Word> flat = new ArrayList<>();
            for (Word w : words) {
                boolean again = !w.operator() && w.text().chars().anyMatch(c -> " \t\n'\"\\`".indexOf(c) >= 0);
                if (!again) {
                    flat.add(w);
                } else if (depth >= DEEPEST) {
                    flat.add(new Word(TOO_DEEP, w.line(), true));
                } else {
                    flat.add(new Word("(", w.line(), true));
                    flat.addAll(flatten(read(w.text(), w.line()), depth + 1));
                    flat.add(new Word(")", w.line(), true));
                }
            }
            return flat;
        }

        private void read() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '\\') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '\n') {
                        line++;
                        if (yaml) {
                            while (i + 2 < s.length() && (s.charAt(i + 2) == ' ' || s.charAt(i + 2) == '\t')) i++;
                        }
                    } else if (i + 1 < s.length()) {
                        append(s.charAt(i + 1));
                    }
                    i += 2;
                } else if (c == '\n') {
                    flush();
                    operator("\n");
                    line++;
                    i++;
                } else if (c == ' ' || c == '\t' || c == '\r') {
                    flush();
                    i++;
                } else if (c == '#' && word == null) {
                    while (i < s.length() && s.charAt(i) != '\n') i++;
                } else if (c == '\'') {
                    int end = s.indexOf('\'', i + 1);
                    end = end < 0 ? s.length() : end;
                    appendCounting(s.substring(i + 1, end));
                    i = end + 1;
                } else if (c == '$' && next('\'')) {
                    operator(ANSI);
                    int end = i + 2;
                    while (end < s.length() && s.charAt(end) != '\'') end += s.charAt(end) == '\\' ? 2 : 1;
                    appendCounting(s.substring(i + 2, Math.min(end, s.length())));
                    i = end + 1;
                } else if (c == '"') {
                    doubleQuoted();
                } else if (c == '$' && next('(')) {
                    substitution(i + 1);
                } else if (c == '$' && next('{')) {
                    parameter();
                } else if (c == '`') {
                    backquote();
                } else if (";&|()<>".indexOf(c) >= 0) {
                    flush();
                    operator(String.valueOf(c));
                    i++;
                } else {
                    append(c);
                    i++;
                }
            }
            flush();
        }

        private void doubleQuoted() {
            start();
            i++;
            while (i < s.length() && s.charAt(i) != '"') {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length() && "$`\"\\\n".indexOf(s.charAt(i + 1)) >= 0) {
                    if (s.charAt(i + 1) == '\n') line++; else word.append(s.charAt(i + 1));
                    i += 2;
                } else if (c == '$' && next('(')) {
                    substitution(i + 1);
                } else if (c == '$' && next('{')) {
                    parameter();
                } else if (c == '`') {
                    backquote();
                } else {
                    if (c == '\n') line++;
                    word.append(c);
                    i++;
                }
            }
            i++;
        }

        /** $( at i - 1 and ( at open: the words inside, between the operators $( and ), and $() in the word. */
        private void substitution(int open) {
            int close = matching(s, open);
            String inside = s.substring(open + 1, Math.min(close, s.length()));
            operator("$(");
            words.addAll(read(inside, line, depth + 1, yaml));
            operator(")");
            line += (int) inside.chars().filter(c -> c == '\n').count();
            start();
            word.append("$()");
            i = close + 1;
        }

        private void backquote() {
            int end = i + 1;
            while (end < s.length() && s.charAt(end) != '`') end += s.charAt(end) == '\\' ? 2 : 1;
            String inside = s.substring(i + 1, Math.min(end, s.length()));
            operator("$(");
            words.addAll(read(inside, line, depth + 1, yaml));
            operator(")");
            line += (int) inside.chars().filter(c -> c == '\n').count();
            start();
            word.append("$()");
            i = end + 1;
        }

        /** ${...}: ${} in the word, and the value it may expand to as a word of its own. */
        private void parameter() {
            int nesting = 0;
            int end = i + 1;
            for (; end < s.length(); end++) {
                if (s.charAt(end) == '{') nesting++;
                if (s.charAt(end) == '}' && --nesting == 0) break;
            }
            String inside = s.substring(i + 2, Math.min(end, s.length()));
            String value = inside.replaceFirst("^[#!]?(?:[A-Za-z_][A-Za-z0-9_]*|[0-9]+|[@*#?$!-])?(?:\\[[^\\]]*\\])?", "")
                    .replaceFirst("^[:\\-=+?#%/^,@]+", "");
            if (!value.isEmpty()) words.add(new Word(value, line, false));
            start();
            word.append("${}");
            i = end + 1;
        }

        /** The index of the parenthesis that closes the one at open, across quotes and nested parentheses. */
        static int matching(String s, int open) {
            int nesting = 0;
            for (int k = open; k < s.length(); k++) {
                char c = s.charAt(k);
                if (c == '\\') {
                    k++;
                } else if (c == '\'') {
                    int end = s.indexOf('\'', k + 1);
                    k = end < 0 ? s.length() : end;
                } else if (c == '"') {
                    k++;
                    while (k < s.length() && s.charAt(k) != '"') k += s.charAt(k) == '\\' ? 2 : 1;
                } else if (c == '(') {
                    nesting++;
                } else if (c == ')' && --nesting == 0) {
                    return k;
                }
            }
            return s.length();
        }

        private boolean next(char c) {
            return i + 1 < s.length() && s.charAt(i + 1) == c;
        }

        private void start() {
            if (word == null) {
                word = new StringBuilder();
                wordLine = line;
            }
        }

        private void append(char c) {
            start();
            word.append(c);
        }

        private void appendCounting(String text) {
            start();
            word.append(text);
            line += (int) text.chars().filter(c -> c == '\n').count();
        }

        private void flush() {
            if (word != null) {
                words.add(new Word(word.toString(), wordLine, false));
                word = null;
            }
        }

        private void operator(String text) {
            words.add(new Word(text, line, true));
        }
    }

    /** The languages whose sources check 8 reads as code; their comments and string literals differ. */
    enum Language {
        JAVA, KOTLIN, GROOVY;

        static Optional<Language> of(String name) {
            if (name.endsWith(".java")) return Optional.of(JAVA);
            if (name.endsWith(".kt") || name.endsWith(".kts")) return Optional.of(KOTLIN);
            if (name.endsWith(".groovy") || name.endsWith(".gradle")) return Optional.of(GROOVY);
            return Optional.empty();
        }
    }

    /** A string literal's value, its escapes decoded the way its language decodes them, and where its text starts. */
    record Literal(String value, int offset) {}

    /**
     * A source without its comments (blanked, its lines kept, so that offsets and lines stay) and its string literals:
     * plain and character literals, Java text blocks (incidental indentation removed before the escapes, as javac does),
     * Kotlin raw strings (no escapes) and Groovy single, double and triple quotes. Groovy slashy strings are not parsed.
     */
    record SourceViews(String code, List<Literal> literals) {

        static SourceViews of(String text, Language language) {
            StringBuilder code = new StringBuilder(text);
            List<Literal> literals = new ArrayList<>();
            int i = 0;
            int n = text.length();
            while (i < n) {
                if (text.startsWith("//", i)) {
                    int end = text.indexOf('\n', i);
                    end = end < 0 ? n : end;
                    blank(code, i, end);
                    i = end;
                } else if (text.startsWith("/*", i)) {
                    int end = text.indexOf("*/", i + 2);
                    end = end < 0 ? n : end + 2;
                    blank(code, i, end);
                    i = end;
                } else if (text.startsWith("\"\"\"", i) || language == Language.GROOVY && text.startsWith("'''", i)) {
                    String delimiter = text.substring(i, i + 3);
                    boolean escapes = language != Language.KOTLIN;
                    int j = i + 3;
                    while (j < n && !text.startsWith(delimiter, j)) j += escapes && text.charAt(j) == '\\' ? 2 : 1;
                    String content = text.substring(i + 3, Math.min(j, n));
                    if (language == Language.JAVA) content = withoutIncidentalIndentation(content);
                    literals.add(new Literal(escapes ? decoded(content, language) : content, i + 3));
                    i = Math.min(n, j + 3);
                } else if (text.charAt(i) == '"' || text.charAt(i) == '\'') {
                    char quote = text.charAt(i);
                    int j = i + 1;
                    while (j < n && text.charAt(j) != quote && text.charAt(j) != '\n') j += text.charAt(j) == '\\' ? 2 : 1;
                    literals.add(new Literal(decoded(text.substring(i + 1, Math.min(j, n)), language), i + 1));
                    i = Math.min(n, j + 1);
                } else {
                    i++;
                }
            }
            return new SourceViews(code.toString(), List.copyOf(literals));
        }

        private static void blank(StringBuilder code, int from, int to) {
            for (int k = from; k < to; k++) {
                if (code.charAt(k) != '\n') code.setCharAt(k, ' ');
            }
        }

        /** A Java text block's content without the indentation that javac strips, before its escapes are decoded. */
        static String withoutIncidentalIndentation(String content) {
            List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
            lines.removeFirst(); // the rest of the opening line, which javac requires to be blank
            int indentation = Integer.MAX_VALUE;
            for (int k = 0; k < lines.size(); k++) {
                String l = lines.get(k);
                boolean last = k == lines.size() - 1;
                if (l.isBlank() && !last) continue;
                int leading = 0;
                while (leading < l.length() && (l.charAt(leading) == ' ' || l.charAt(leading) == '\t')) leading++;
                indentation = Math.min(indentation, leading);
            }
            StringBuilder out = new StringBuilder();
            for (int k = 0; k < lines.size(); k++) {
                String l = lines.get(k);
                out.append(l.length() >= indentation ? l.substring(indentation) : "").append(k < lines.size() - 1 ? "\n" : "");
            }
            return out.toString();
        }

        /** A literal's text with its escapes decoded: octal (Java, Groovy), Unicode (Kotlin, Groovy), line continuations. */
        static String decoded(String text, Language language) {
            StringBuilder out = new StringBuilder();
            for (int k = 0; k < text.length(); k++) {
                char c = text.charAt(k);
                if (c != '\\' || k + 1 >= text.length()) {
                    out.append(c);
                    continue;
                }
                char e = text.charAt(++k);
                switch (e) {
                    case 'b' -> out.append('\b');
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'f' -> out.append('\f');
                    case 'r' -> out.append('\r');
                    case 's' -> out.append(' ');
                    case '\n' -> { }
                    case 'u' -> {
                        int h = k;
                        while (h < text.length() && text.charAt(h) == 'u') h++;
                        if (h + 4 <= text.length() && text.substring(h, h + 4).matches("[0-9a-fA-F]{4}")) {
                            out.append((char) Integer.parseInt(text.substring(h, h + 4), 16));
                            k = h + 3;
                        } else {
                            out.append(e);
                        }
                    }
                    default -> {
                        if (e >= '0' && e <= '7' && language != Language.KOTLIN) {
                            int end = k;
                            int digits = e <= '3' ? 3 : 2;
                            while (end < text.length() && end - k < digits && text.charAt(end) >= '0' && text.charAt(end) <= '7') {
                                end++;
                            }
                            out.append((char) Integer.parseInt(text.substring(k, end), 8));
                            k = end - 1;
                        } else {
                            out.append(e);
                        }
                    }
                }
            }
            return out.toString();
        }
    }

    /**
     * A Java source as javac reads it (JLS 3.3): a backslash that an even number of backslashes precede, then one or
     * more u and four hexadecimal digits, is the character they encode, so that no escape can hide a name.
     */
    static String unicodeEscapesDecoded(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int backslashes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && backslashes % 2 == 0) {
                int j = i + 1;
                while (j < text.length() && text.charAt(j) == 'u') j++;
                if (j > i + 1 && j + 4 <= text.length() && text.substring(j, j + 4).matches("[0-9a-fA-F]{4}")) {
                    out.append((char) Integer.parseInt(text.substring(j, j + 4), 16));
                    i = j + 3;
                    backslashes = 0;
                    continue;
                }
            }
            backslashes = c == '\\' ? backslashes + 1 : 0;
            out.append(c);
        }
        return out.toString();
    }

    /** The index of the parenthesis that closes the one at open, skipping string and character literals; -1 if none. */
    static int closing(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                for (i++; i < text.length() && text.charAt(i) != c; i++) {
                    if (text.charAt(i) == '\\') i++;
                }
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Each match of a pattern in a view of a file's text, at its line. */
    void find(Path file, String text, String view, Pattern pattern, String problem) {
        for (Matcher m = pattern.matcher(view); m.find(); ) add("images", at(file, text, m.start()) + ": " + problem);
    }

    /** A place in a file for a message: its path and line number. */
    String at(Path file, String text, int offset) {
        return relative(file) + ":" + lineOf(text, offset);
    }

    // ---- shared ----

    void requireApproved(String area, String kind, String coordinate, String version, String where) {
        Optional<Entry> entry = find(kind, coordinate, version);
        if (entry.isEmpty()) {
            add(area, "%s %s (in %s) has no review on the allowlist"
                    .formatted(safe(coordinate), safe(version), safe(where)));
        } else if ("REJECT".equals(entry.get().verdict())) {
            add(area, "%s %s (in %s) was REJECTED in %s"
                    .formatted(safe(coordinate), safe(version), safe(where), safe(entry.get().reviewRecord())));
        }
    }

    Optional<Entry> find(String kind, String coordinate, String version) {
        return entries.stream().filter(e -> e.kind().equals(kind) && e.coordinate().equals(coordinate)
                && e.version().equals(version)).findFirst();
    }

    void add(String area, String message) { findings.add(new Finding(area, message)); }

    List<Path> files(java.util.function.Predicate<Path> wanted) throws IOException {
        try (Stream<Path> walk = Files.walk(repo)) {
            return walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> Stream.of(".git", "build", "node_modules", ".gradle").noneMatch(skip ->
                            repo.relativize(p).toString().startsWith(skip + "/") || repo.relativize(p).toString().contains("/" + skip + "/")))
                    .filter(wanted).sorted().toList();
        } catch (IOException | UncheckedIOException e) {
            throw new Stop("the checked tree changed while it was read");
        }
    }

    /** A path for a message: relative to the tree, and passed through {@link #safe}. */
    String relative(Path p) { return safe(repo.relativize(p)); }

    /**
     * A value from a file, as a message may print it: ASCII letters and digits and the characters . : + - / @ = are
     * kept, every other character becomes '?', and at most {@link #SHOWN} of them are shown. So no Markdown emphasis
     * or link syntax, HTML or control character that a file holds can reach the log or the step summary; GitHub may
     * still turn a printed URL or @name into a link.
     */
    static String safe(Object value) {
        String text = String.valueOf(value);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(text.length(), SHOWN); i++) {
            char c = text.charAt(i);
            boolean kept = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || ".:+-/@=".indexOf(c) >= 0;
            out.append(kept ? c : '?');
        }
        return text.length() > SHOWN ? out + "..." : out.toString();
    }

    /**
     * The tree to check, as its real path, once a walk of all of it has found no symbolic link: not the root, and
     * nothing below it, the directories the checks skip included. The walk follows no link, and a file or directory it
     * cannot inspect stops the check, so no link in the tree can lead a read elsewhere.
     */
    static Path withoutLinks(Path tree) throws IOException {
        Path root = tree.toAbsolutePath().normalize();
        Path real;
        try {
            if (Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isSymbolicLink()) {
                throw new Stop("refused: the checked tree is a symbolic link");
            }
            real = root.toRealPath();
        } catch (Stop e) {
            throw e;
        } catch (IOException e) {
            throw new Stop("the checked tree cannot be inspected");
        }
        List<String> links = new ArrayList<>();
        Files.walkFileTree(real, Set.of(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isSymbolicLink()) links.add(safe(real.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                throw uninspectable(file);
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e != null) throw uninspectable(dir);
                return FileVisitResult.CONTINUE;
            }
            private Stop uninspectable(Path path) {
                return new Stop("%s: cannot be inspected".formatted(path.equals(real) ? "the checked tree"
                        : safe(real.relativize(path))));
            }
        });
        if (!links.isEmpty()) {
            links.sort(null);
            throw new Stop("refused: symbolic link in the checked tree: " + String.join(", ", links));
        }
        return real;
    }

    /** A regular file whose real path is inside the checked tree, opened without following a link; else it stops. */
    byte[] read(Path file) throws IOException {
        if (!file.normalize().startsWith(repo)) throw notInTree(file); // before the file system is asked anything
        try {
            Path real = file.toRealPath();
            if (!real.startsWith(repo) || !Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) throw notInTree(file);
            try (InputStream in = Files.newInputStream(real, LinkOption.NOFOLLOW_LINKS)) {
                return in.readAllBytes();
            }
        } catch (NoSuchFileException e) {
            throw new Stop("%s: not found".formatted(relative(file)));
        } catch (Stop e) {
            throw e;
        } catch (IOException e) {
            throw new Stop("%s: cannot be read".formatted(relative(file)));
        }
    }

    Stop notInTree(Path file) {
        return new Stop("refused: %s is not a regular file inside the checked tree".formatted(relative(file)));
    }

    /** A file's text, as strict UTF-8 the way Files.readString reads it. */
    String readText(Path file) throws IOException {
        byte[] bytes = read(file);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new Stop("%s: not UTF-8 text".formatted(relative(file)));
        }
    }

    /** A JSON file's top-level object. Text that is not JSON stops the check at its line and column, for a fixed reason. */
    Json.JObject jsonObject(Path file) throws IOException {
        String text = readText(file);
        try {
            return Json.parse(text).asObject();
        } catch (Json.Malformed e) {
            throw new Stop("%s:%s: JSON %s".formatted(relative(file), location(text, e.offset), e.reason));
        } catch (IllegalArgumentException e) {
            throw new Stop("%s: %s".formatted(relative(file), e.getMessage()));
        }
    }

    /**
     * A member of a JSON structure. One that is missing or of another type stops the check: the message names the file
     * and the member as this code asks for it, never what the file holds.
     */
    <T> T member(Path file, Supplier<T> member) throws Stop {
        try {
            return member.get();
        } catch (IllegalArgumentException e) {
            throw new Stop("%s: %s".formatted(relative(file), e.getMessage()));
        }
    }

    /** The line and column, both counted from 1, of an offset in a text. */
    static String location(String text, int offset) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return line + ":" + (offset - lineStart + 1);
    }

    String report(Set<Finding> found, boolean reportOnly) {
        StringBuilder out = new StringBuilder("## Dependency allowlist: ");
        out.append(found.isEmpty() ? "clean" : "%d finding(s)%s".formatted(found.size(), reportOnly ? " (report-only)" : ""));
        out.append("\n\nChecked against `security/allowlist.json` (%d entries) on %s.\n".formatted(entries.size(), today));
        String area = "";
        for (Finding f : found) {
            if (!f.area().equals(area)) {
                area = f.area();
                out.append("\n### ").append(area).append("\n\n");
            }
            out.append("- ").append(f.message()).append('\n');
        }
        return out.toString();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    /** An XML parser with doctype declarations, external entities and XInclude switched off, that prints nothing. */
    static Document parseXmlSafely(String xml) throws IOException, SAXException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(SILENT);
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Stops at the first error and prints nothing; the parser's default handler writes every error to stderr. */
    static final ErrorHandler SILENT = new ErrorHandler() {
        @Override public void warning(SAXParseException e) { }
        @Override public void error(SAXParseException e) throws SAXParseException { throw e; }
        @Override public void fatalError(SAXParseException e) throws SAXParseException { throw e; }
    };

    /** A small, strict JSON reader (RFC 8259): the JDK has none, and this tool may depend on nothing but the JDK. */
    sealed interface Json {
        record JObject(Map<String, Json> members) implements Json {
            Json get(String key) {
                Json value = members.get(key);
                if (value == null) throw new IllegalArgumentException("missing JSON member '" + key + "'");
                return value;
            }
            boolean has(String key) { return members.containsKey(key); }
            /** A string, number or boolean member as text; "" when absent or null. */
            String text(String key) {
                return switch (members.get(key)) {
                    case null -> "";
                    case JString s -> s.value();
                    case JNumber n -> n.value();
                    case JBool b -> Boolean.toString(b.value());
                    case JNull ignored -> "";
                    case JArray ignored -> "";
                    case JObject ignored -> "";
                };
            }
        }
        record JArray(List<Json> items) implements Json {
            Stream<Json> stream() { return items.stream(); }
        }
        record JString(String value) implements Json {}
        record JNumber(String value) implements Json {}
        record JBool(boolean value) implements Json {}
        record JNull() implements Json {}

        default JObject asObject() {
            if (this instanceof JObject o) return o;
            throw new IllegalArgumentException("expected a JSON object, got " + getClass().getSimpleName());
        }
        default JArray asArray() {
            if (this instanceof JArray a) return a;
            throw new IllegalArgumentException("expected a JSON array, got " + getClass().getSimpleName());
        }

        /** The value as compact JSON with object keys sorted, so equal data always gives equal text. */
        static String canonical(Json value) {
            return switch (value) {
                case JObject o -> o.members().keySet().stream().sorted()
                        .map(k -> quote(k) + ":" + canonical(o.members().get(k)))
                        .collect(java.util.stream.Collectors.joining(",", "{", "}"));
                case JArray a -> a.items().stream().map(Json::canonical)
                        .collect(java.util.stream.Collectors.joining(",", "[", "]"));
                case JString s -> quote(s.value());
                case JNumber n -> n.value();
                case JBool b -> Boolean.toString(b.value());
                case JNull ignored -> "null";
            };
        }

        static String quote(String text) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : text.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) out.append("\\u%04x".formatted((int) c)); else out.append(c);
                    }
                }
            }
            return out.append('"').toString();
        }

        static Json parse(String text) {
            Parser parser = new Parser(text);
            Json value = parser.value();
            parser.skipWhitespace();
            if (parser.pos != text.length()) throw parser.error("trailing characters");
            return value;
        }

        /** Text that is not JSON: a fixed reason and the offset where it was found, never the text itself. */
        final class Malformed extends IllegalArgumentException {
            final String reason;
            final int offset;

            Malformed(String reason, int offset) {
                super("JSON " + reason + " at offset " + offset);
                this.reason = reason;
                this.offset = offset;
            }
        }

        final class Parser {
            private static final Pattern HEX4 = Pattern.compile("[0-9a-fA-F]{4}");
            private final String s;
            private int pos;

            Parser(String s) { this.s = s; }

            Json value() {
                skipWhitespace();
                if (pos >= s.length()) throw error("unexpected end");
                return switch (s.charAt(pos)) {
                    case '{' -> object();
                    case '[' -> array();
                    case '"' -> new JString(string());
                    case 't' -> literal("true", new JBool(true));
                    case 'f' -> literal("false", new JBool(false));
                    case 'n' -> literal("null", new JNull());
                    default -> number();
                };
            }

            JObject object() {
                Map<String, Json> members = new LinkedHashMap<>();
                pos++;
                skipWhitespace();
                if (peek('}')) { pos++; return new JObject(members); }
                while (true) {
                    skipWhitespace();
                    int keyStart = pos;
                    String key = string();
                    skipWhitespace();
                    expect(':');
                    if (members.put(key, value()) != null) throw new Malformed("duplicate key", keyStart);
                    skipWhitespace();
                    if (peek(',')) { pos++; continue; }
                    expect('}');
                    return new JObject(members);
                }
            }

            JArray array() {
                List<Json> items = new ArrayList<>();
                pos++;
                skipWhitespace();
                if (peek(']')) { pos++; return new JArray(items); }
                while (true) {
                    items.add(value());
                    skipWhitespace();
                    if (peek(',')) { pos++; continue; }
                    expect(']');
                    return new JArray(items);
                }
            }

            String string() {
                expect('"');
                StringBuilder out = new StringBuilder();
                while (true) {
                    if (pos >= s.length()) throw error("unterminated string");
                    char c = s.charAt(pos++);
                    if (c == '"') return out.toString();
                    if (c < 0x20) throw error("control character in string");
                    if (c != '\\') { out.append(c); continue; }
                    if (pos >= s.length()) throw error("unterminated string");
                    char escape = s.charAt(pos++);
                    switch (escape) {
                        case '"', '\\', '/' -> out.append(escape);
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            String hex = s.substring(pos, Math.min(pos + 4, s.length()));
                            if (!HEX4.matcher(hex).matches()) throw error("bad escape");
                            out.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw error("bad escape");
                    }
                }
            }

            /** A number as RFC 8259 defines it, -?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][-+]?[0-9]+)?, kept as written. */
            Json number() {
                int start = pos;
                if (peek('-')) pos++;
                int integerStart = pos;
                int integerDigits = digits();
                if (integerDigits == 0 && pos == start) throw error("unexpected character");
                if (integerDigits == 0) throw error("malformed number");
                if (integerDigits > 1 && s.charAt(integerStart) == '0') throw error("leading zero in number");
                if (peek('.')) {
                    pos++;
                    if (digits() == 0) throw error("malformed number");
                }
                if (peek('e') || peek('E')) {
                    pos++;
                    if (peek('+') || peek('-')) pos++;
                    if (digits() == 0) throw error("malformed number");
                }
                return new JNumber(s.substring(start, pos));
            }

            /** Skips ASCII digits and returns how many there were. */
            int digits() {
                int start = pos;
                while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') pos++;
                return pos - start;
            }

            Json literal(String word, Json value) {
                if (!s.startsWith(word, pos)) throw error("expected " + word);
                pos += word.length();
                return value;
            }

            /** JSON's whitespace only: space, tab, line feed and carriage return. */
            void skipWhitespace() { while (pos < s.length() && " \t\n\r".indexOf(s.charAt(pos)) >= 0) pos++; }
            boolean peek(char c) { return pos < s.length() && s.charAt(pos) == c; }
            void expect(char c) {
                if (!peek(c)) throw error("expected '" + c + "'");
                pos++;
            }
            /** The reason is fixed text of this parser; it never quotes the text it reads. */
            Malformed error(String what) {
                return new Malformed(what, pos);
            }
        }
    }

}
