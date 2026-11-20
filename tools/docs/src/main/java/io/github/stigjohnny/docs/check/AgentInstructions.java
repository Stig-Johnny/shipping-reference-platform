package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.DocsException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The instruction files Claude Code reads (ADR 0022). The repository root has an {@code AGENTS.md}, every
 * {@code AGENTS.md} has a {@code CLAUDE.md} beside it whose whole content is {@code @AGENTS.md} and a line break, and
 * every {@code CLAUDE.md} has an {@code AGENTS.md}. By default Claude Code reads an {@code AGENTS.md} by itself only
 * where no {@code CLAUDE.md} exists in the working directory or above it, and not in every session. With the root
 * {@code CLAUDE.md}, a scoped {@code AGENTS.md} reaches Claude through its own {@code CLAUDE.md}, and a
 * {@code CLAUDE.md} that only imports adds no rules of its own.
 *
 * <p>The walk does not enter build output, dependencies, tool state or Claude Code's worktrees, and follows no link. An
 * instruction file that is a link or not a regular file, that differs from {@code AGENTS.md} or {@code CLAUDE.md} only
 * in letter case, or that lies under {@code .claude/} fails. The check proves which files exist and their bytes, not
 * what a session loads or follows. {@code CLAUDE.local.md}, {@code .claude/rules/} and user, managed or ancestor
 * instructions are outside it.
 */
final class AgentInstructions {

    static final String RULES = "AGENTS.md";
    static final String WRAPPER = "CLAUDE.md";
    static final String IMPORT = "@AGENTS.md\n";
    /** Directory names that are never walked: build output (outside src trees), dependencies and tool state. */
    static final Set<String> SKIPPED = Set.of(".git", ".gradle", "build", "node_modules");
    /** Claude Code's worktrees: separate checkouts, each with its own checks. */
    static final String WORKTREES = ".claude/worktrees";

    private AgentInstructions() {
    }

    /** One file or directory named like an instruction file, repository-relative, and whether it is a regular file. */
    record Candidate(String path, boolean regular) {

        String name() {
            return path.substring(path.lastIndexOf('/') + 1);
        }

        /** The directory, repository-relative with a trailing slash, or empty at the root. */
        String directory() {
            return path.substring(0, path.lastIndexOf('/') + 1);
        }
    }

    static void check(Path repo, Problems problems) {
        List<Candidate> candidates = candidates(repo);
        Set<String> present = new TreeSet<>();
        candidates.forEach(candidate -> present.add(candidate.path()));
        if (!present.contains(RULES)) {
            problems.add(RULES + " is missing: the repository root holds the rules that every agent reads first "
                    + "(ADR 0022)");
        }
        for (Candidate candidate : candidates) {
            String path = candidate.path();
            if (!candidate.regular()) {
                problems.add(path + " is a link or not a regular file; an instruction file is a regular file "
                        + "(ADR 0022)");
            } else if (!candidate.name().equals(RULES) && !candidate.name().equals(WRAPPER)) {
                problems.add(path + " is named like an instruction file in another letter case; name it AGENTS.md or "
                        + "CLAUDE.md (ADR 0022)");
            } else if (path.startsWith(".claude/") || path.contains("/.claude/")) {
                problems.add(path + " is under .claude/; the rules live in AGENTS.md files (ADR 0022)");
            } else if (candidate.name().equals(RULES)) {
                String wrapper = candidate.directory() + WRAPPER;
                if (!present.contains(wrapper)) {
                    problems.add(path + " has no CLAUDE.md beside it: add " + wrapper + " holding exactly "
                            + "'@AGENTS.md' and a line break, so that Claude Code reads these rules wherever it loads "
                            + "CLAUDE.md files (ADR 0022)");
                }
            } else if (!present.contains(candidate.directory() + RULES)) {
                problems.add(path + " has no AGENTS.md beside it: the rules live in an AGENTS.md, which the CLAUDE.md "
                        + "imports (ADR 0022)");
            } else if (!Arrays.equals(bytes(repo, path), IMPORT.getBytes(StandardCharsets.UTF_8))) {
                problems.add(path + " must hold exactly '@AGENTS.md' and a line break, so that it adds no rules of its "
                        + "own (ADR 0022)");
            }
        }
    }

    /**
     * Every file, link or directory whose name is AGENTS.md or CLAUDE.md in any letter case, outside the skipped
     * directories and the worktrees, sorted by path. A listing or reading fault fails the check instead of hiding a
     * file.
     */
    static List<Candidate> candidates(Path repo) {
        List<Candidate> found = new ArrayList<>();
        try {
            Files.walkFileTree(repo, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (directory.equals(repo)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String path = relative(repo, directory);
                    if (skipped(path)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (instructionName(name(directory))) {
                        found.add(new Candidate(path, false));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (instructionName(name(file))) {
                        found.add(new Candidate(relative(repo, file), attributes.isRegularFile()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                    throw e;
                }
            });
        } catch (IOException e) {
            throw new DocsException("the instruction files cannot be listed: " + e.getClass().getSimpleName(), e);
        }
        found.sort(Comparator.comparing(Candidate::path));
        return List.copyOf(found);
    }

    /**
     * Whether a directory, repository-relative, is never walked: build output, dependencies, tool state or Claude Code's
     * worktrees. A directory named build inside a src tree is a Java package, not build output, as the repository's
     * .gitignore also says, so it is walked.
     */
    static boolean skipped(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.equals("build")) {
            return !path.startsWith("src/") && !path.contains("/src/");
        }
        return SKIPPED.contains(name) || path.equals(WORKTREES);
    }

    private static boolean instructionName(String name) {
        return name.equalsIgnoreCase(RULES) || name.equalsIgnoreCase(WRAPPER);
    }

    private static String name(Path path) {
        return String.valueOf(path.getFileName());
    }

    /**
     * The path from the repository, its names joined with '/'. A name keeps every other character, a backslash
     * included, so that a directory named {@code a\b} is never taken for {@code a/b}.
     */
    private static String relative(Path repo, Path path) {
        List<String> names = new ArrayList<>();
        for (Path name : repo.relativize(path)) {
            names.add(name.toString());
        }
        return String.join("/", names);
    }

    /** The file a {@link #relative} path names, resolved one name at a time. */
    private static Path resolve(Path repo, String path) {
        Path resolved = repo;
        for (String name : path.split("/", -1)) {
            resolved = resolved.resolve(name);
        }
        return resolved;
    }

    private static byte[] bytes(Path repo, String path) {
        try {
            return Files.readAllBytes(resolve(repo, path));
        } catch (IOException e) {
            throw new DocsException(path + " cannot be read: " + e.getClass().getSimpleName(), e);
        }
    }
}
