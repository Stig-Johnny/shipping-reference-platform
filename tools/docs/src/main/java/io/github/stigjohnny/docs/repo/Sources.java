package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.model.ElementStatus;
import io.github.stigjohnny.docs.model.ParsedWorkspace;
import io.github.stigjohnny.docs.model.Workspaces;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import io.github.stigjohnny.docs.yaml.YamlInputException;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Every input of the documentation, read once from code-owned repository paths: the model, the documents with their
 * frontmatter, the capabilities, the API contract, the risk policy, the allowlist and its review records, the workflows
 * and the test evidence. Nothing is fetched; every path in a message is repository-relative.
 */
public record Sources(Path repo, String readme, List<DocFile> docs, DocFile securityReadme, Capabilities capabilities,
        ApiContract contract, RiskPolicy risk, Allowlist allowlist, Map<String, ReviewRecord> reviews,
        Map<String, byte[]> reviewBytes, ParsedWorkspace workspace, Map<String, Set<String>> workflowJobs,
        TestEvidence evidence, DependencyUsage dependencyUsage) {

    public static final String WORKSPACE = "architecture/workspace.dsl";
    public static final String SECURITY_README = "security/README.md";
    public static final String README = "README.md";
    public static final String BRAIN = "BRAIN.md";
    /** Directories the generator owns; their files are outputs, never inputs. */
    public static final List<String> GENERATED_DIRS = List.of("docs/architecture/", "docs/brain/");

    public Sources {
        docs = List.copyOf(docs);
        reviews = Map.copyOf(reviews);
        reviewBytes = Map.copyOf(reviewBytes);
        workflowJobs = Map.copyOf(workflowJobs);
    }

    public static Sources read(Path repo, List<Path> testReports) {
        Path root = repo.toAbsolutePath().normalize();
        List<DocFile> docs = new ArrayList<>();
        for (String path : files(root, "docs", ".md")) {
            if (GENERATED_DIRS.stream().noneMatch(path::startsWith)) {
                docs.add(DocFile.parse(path, text(root, path)));
            }
        }
        Allowlist allowlist = Allowlist.parse(text(root, Allowlist.PATH));
        Map<String, ReviewRecord> reviews = new TreeMap<>();
        Map<String, byte[]> reviewBytes = new TreeMap<>();
        for (String record : allowlist.reviewRecords()) {
            reviews.put(record, ReviewRecord.parse(record, text(root, record)));
            reviewBytes.put(record, bytes(root, record));
        }
        Map<String, Set<String>> jobs = new TreeMap<>();
        for (String workflow : files(root, ".github/workflows", ".yml")) {
            try {
                YMap definition = SafeYaml.yaml(text(root, workflow), workflow).asMap(workflow);
                jobs.put(workflow, Set.copyOf(definition.map("jobs").entries().keySet()));
            } catch (YamlInputException e) {
                throw new DocsException(e.getMessage(), e);
            }
        }
        return new Sources(root, text(root, README), docs, DocFile.parse(SECURITY_README, text(root, SECURITY_README)),
                Capabilities.parse(text(root, Capabilities.PATH)), ApiContract.parse(text(root, ApiContract.PATH)),
                RiskPolicy.parse(text(root, RiskPolicy.PATH)), allowlist, reviews, reviewBytes,
                Workspaces.read(root.resolve(WORKSPACE), WORKSPACE), jobs, TestEvidence.read(root, testReports),
                DependencyUsage.read(root));
    }

    public Optional<DocFile> doc(String path) {
        return docs.stream().filter(d -> d.path().equals(path)).findFirst();
    }

    public List<DocFile> docsUnder(String directory) {
        return docs.stream().filter(d -> d.path().startsWith(directory)).toList();
    }

    /** How the diagrams and tables state each element's implementation status. */
    public ElementStatus elementStatus() {
        return new ElementStatus(capabilities.specificationOnly());
    }

    /** The newest {@code updated} date of the hand-written documents: the date the generated files carry. */
    public String documentationDate() {
        return docs.stream().map(DocFile::updated).max(String::compareTo).orElse("");
    }

    /** All job IDs of all workflows. */
    public Set<String> jobs() {
        return Set.copyOf(workflowJobs.values().stream().flatMap(Set::stream).toList());
    }

    /** The repository file as UTF-8 text; malformed UTF-8 fails. */
    public static String text(Path root, String path) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(root.resolve(path)))).toString();
        } catch (CharacterCodingException e) {
            throw new DocsException(path + ": not valid UTF-8", e);
        } catch (IOException e) {
            throw new DocsException(path + ": cannot be read (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    public static byte[] bytes(Path root, String path) {
        try {
            return Files.readAllBytes(root.resolve(path));
        } catch (IOException e) {
            throw new DocsException(path + ": cannot be read (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    /** Repository-relative paths of the files under a directory with a suffix, sorted. */
    public static List<String> files(Path root, String directory, String suffix) {
        Path dir = root.resolve(directory);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> p.endsWith(suffix)).sorted().toList();
        } catch (IOException e) {
            throw new DocsException(directory + ": cannot be listed", e);
        }
    }
}
