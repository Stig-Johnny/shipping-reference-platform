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
//      for byte.
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
     * A value from a file, as a message may print it: ASCII letters and digits and the characters . : _ + - / @ = are
     * kept, every other character becomes '?', and at most {@link #SHOWN} of them are shown. So no Markdown, HTML or
     * control character that a file holds can reach the log or the step summary.
     */
    static String safe(Object value) {
        String text = String.valueOf(value);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(text.length(), SHOWN); i++) {
            char c = text.charAt(i);
            boolean kept = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || ".:_+-/@=".indexOf(c) >= 0;
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
