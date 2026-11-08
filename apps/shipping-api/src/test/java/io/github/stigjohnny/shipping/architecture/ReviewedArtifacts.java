package io.github.stigjohnny.shipping.architecture;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * What security/allowlist.json approves, as the external-import policy reads it: which Maven artifact may be on which
 * configuration of which project, by which review record.
 *
 * <p>The entries were written at different times, so their scope fields are normalised, in these ways only:
 * <ul>
 *   <li>{@code project} and {@code configuration} are a string or a list of strings. Every value is trimmed: 37
 *       entries carry configuration names with a leading space.
 *   <li>A project written as a path ({@code apps/shipping-api}) is the Gradle path {@code :apps:shipping-api}.
 *   <li>{@code :example-app} is the name the reviews' first design gave the API. The owner confirmed that it is
 *       {@code :apps:shipping-api} ({@code acceptance.projectPathConfirmed}, checked by ExternalImportPolicyTest).
 *   <li>A configuration with a project prefix ({@code :example-app:testCompileClasspath}) applies to that project
 *       only; any other applies to each of the entry's projects.
 * </ul>
 */
final class ReviewedArtifacts {

    static final String API = ":apps:shipping-api";
    static final String FIRST_DESIGN_NAME = ":example-app";
    // Exact existing affirmative statement; changing the owner's record needs a new review of this reading.
    static final String CONFIRMED_ALIAS = "apps/shipping-api (Gradle :apps:shipping-api) is the project that the "
            + "reviews' first design called :example-app.";
    static final Path ALLOWLIST = Path.of("../../security/allowlist.json");
    private static final Pattern QUALIFIED = Pattern.compile("((?::[A-Za-z0-9._-]+)+):([A-Za-z]+)");
    private static final Set<String> APPROVED = Set.of("APPROVE", "APPROVE-WITH-CONDITIONS");

    /** A project and one of its configurations, as an entry grants them. */
    record Grant(String project, String configuration) {
    }

    record Entry(String kind, String coordinate, String version, String artifact, @Nullable String sha256,
                 Set<Grant> grants, String verdict, String reviewRecord, String expiresAt) {
    }

    /** Whether an artifact may be on a configuration of the API, and by which review records. */
    sealed interface Decision permits Approved, Refused {
    }

    record Approved(List<String> reviewRecords) implements Decision {
    }

    record Refused(String reason) implements Decision {
    }

    private final List<Entry> entries;
    private final String projectPathConfirmed;
    private final LocalDate today;

    private ReviewedArtifacts(List<Entry> entries, String projectPathConfirmed, LocalDate today) {
        this.entries = entries;
        this.projectPathConfirmed = projectPathConfirmed;
        this.today = today;
    }

    static ReviewedArtifacts read(Path allowlist, LocalDate today) {
        JsonNode root = ProviderIndex.JSON.readTree(allowlist);
        JsonNode acceptance = root.path("acceptance");
        if (!"accepted".equals(acceptance.path("status").asString(""))
                || !CONFIRMED_ALIAS.equals(acceptance.path("projectPathConfirmed").asString(""))) {
            throw new IllegalStateException(":example-app requires the exact accepted projectPathConfirmed record");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonNode entry : root.get("entries").values()) {
            JsonNode sha256 = entry.path("sha256");
            entries.add(new Entry(entry.get("kind").stringValue(), entry.get("coordinate").stringValue(),
                    entry.get("version").stringValue(), entry.path("artifact").asString(""),
                    sha256.isString() ? sha256.stringValue() : null,
                    grants(values(entry.path("project")), values(entry.path("configuration"))),
                    entry.get("verdict").stringValue(), entry.get("reviewRecord").stringValue(),
                    entry.path("expiresAt").asString("")));
        }
        return new ReviewedArtifacts(List.copyOf(entries),
                root.path("acceptance").path("projectPathConfirmed").asString(""), today);
    }

    /** The owner's recorded confirmation of the project path, which the name {@code :example-app} rests on. */
    String projectPathConfirmed() {
        return projectPathConfirmed;
    }

    /** Whether the allowlist approves the Maven artifact for the API's configuration, and by which reviews. */
    Decision decide(ProviderIndex.Provider provider, String configuration) {
        String artifact = provider.label();
        List<Entry> candidates = entries.stream().filter(entry -> entry.kind().equals("maven")
                && entry.coordinate().equals(provider.coordinate()) && entry.version().equals(provider.version()))
                .toList();
        if (candidates.isEmpty()) {
            return new Refused(artifact + " has no entry in security/allowlist.json");
        }
        for (Entry entry : candidates) {
            if (entry.verdict().equals("REJECT")) {
                return new Refused(artifact + " was rejected in " + entry.reviewRecord());
            }
        }
        Grant wanted = new Grant(API, configuration);
        List<Entry> granting = candidates.stream().filter(entry -> APPROVED.contains(entry.verdict())
                && entry.grants().contains(wanted)).toList();
        if (granting.isEmpty()) {
            return new Refused(artifact + " is not approved for " + API + " " + configuration + ": its entries "
                    + "approve it for " + scopes(candidates));
        }
        List<Entry> forFile = granting.stream().filter(entry -> entry.artifact().equals(provider.file())).toList();
        if (forFile.isEmpty()) {
            return new Refused(artifact + ": no approved entry names the file " + provider.file());
        }
        List<String> reviews = new ArrayList<>();
        for (Entry entry : forFile) {
            if (LocalDate.parse(entry.expiresAt()).isBefore(today)) {
                return new Refused(artifact + ": its review " + entry.reviewRecord() + " expired on "
                        + entry.expiresAt());
            }
            if (entry.sha256() != null && !entry.sha256().equalsIgnoreCase(provider.sha256())) {
                return new Refused(artifact + ": " + provider.file() + " has the SHA-256 " + provider.sha256()
                        + ", not the reviewed " + entry.sha256());
            }
            reviews.add(entry.reviewRecord());
        }
        return new Approved(reviews.stream().distinct().sorted().toList());
    }

    /** The review records of the allowlisted JDK of this exact version, sorted; empty when it has none. */
    List<String> jdkReviews(String version) {
        return entries.stream().filter(entry -> entry.kind().equals("jdk") && entry.version().equals(version))
                .map(Entry::reviewRecord).distinct().sorted().toList();
    }

    /** The projects and configurations that the entries grant, as "project (configuration, ...)", sorted. */
    private static String scopes(List<Entry> entries) {
        Map<String, Set<String>> byProject = new TreeMap<>();
        entries.stream().flatMap(entry -> entry.grants().stream()).forEach(grant -> byProject
                .computeIfAbsent(grant.project(), key -> new TreeSet<>()).add(grant.configuration()));
        return byProject.isEmpty() ? "no configuration of any project" : byProject.entrySet().stream()
                .map(entry -> entry.getKey() + " (" + String.join(", ", entry.getValue()) + ")")
                .collect(Collectors.joining("; "));
    }

    static Set<Grant> grants(List<String> projects, List<String> configurations) {
        Set<Grant> grants = new HashSet<>();
        for (String configuration : configurations) {
            Matcher qualified = QUALIFIED.matcher(configuration);
            if (qualified.matches()) {
                grants.add(new Grant(project(qualified.group(1)), qualified.group(2)));
            } else {
                projects.forEach(project -> grants.add(new Grant(project(project), configuration)));
            }
        }
        return Set.copyOf(grants);
    }

    /** One project name as a Gradle path, with the reviews' first-design name of the API resolved. */
    static String project(String name) {
        String path = name.startsWith(":") ? name : ":" + name.replace('/', ':');
        return path.equals(FIRST_DESIGN_NAME) ? API : path;
    }

    /** A string, or the strings of a list, each trimmed. */
    private static List<String> values(JsonNode node) {
        if (node.isString()) {
            return List.of(node.stringValue().trim());
        }
        return node.values().stream().map(value -> value.stringValue().trim()).toList();
    }
}
