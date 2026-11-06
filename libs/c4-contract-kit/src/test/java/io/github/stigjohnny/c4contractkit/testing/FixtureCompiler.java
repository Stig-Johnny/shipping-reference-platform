package io.github.stigjohnny.c4contractkit.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles fixture classes at test time, with the JDK's own compiler, into a class root of their own.
 *
 * <p>Fixtures are never on the test classpath: the library must read their class files without loading them, and PIT
 * must not mutate them. Compilation is deterministic (fixed options, no annotation processing, an empty class path),
 * and each distinct set of sources is compiled once per JVM.
 */
public final class FixtureCompiler {

    private static final Map<String, Path> COMPILED = new ConcurrentHashMap<>();
    private static final Path WORK = createWorkDirectory();

    private FixtureCompiler() {
    }

    /** Compiles with source file names and line numbers in the class files, as Gradle does by default. */
    public static Path compile(FixtureSource... sources) {
        return compile("-g:source,lines", Optional.empty(), sources);
    }

    /** Compiles without any debug information: no source file names, no line numbers. */
    public static Path compileWithoutDebugInformation(FixtureSource... sources) {
        return compile("-g:none", Optional.empty(), sources);
    }

    /** Compiles against the classes in {@code classRoot}, which stay out of the result. */
    public static Path compileAgainst(Path classRoot, FixtureSource... sources) {
        return compile("-g:source,lines", Optional.of(classRoot), sources);
    }

    /** Compiles against classes of the test class path, found as the class root that holds {@code someClass}. */
    public static Path compileAgainstTestClassesOf(String someClass, FixtureSource... sources) {
        return compile("-g:source,lines", Optional.of(ClassRoots.of(someClass)), sources);
    }

    private static Path compile(String debugOption, Optional<Path> classPath, FixtureSource... sources) {
        List<FixtureSource> sorted = Stream.of(sources).sorted(Comparator.comparing(FixtureSource::path)).toList();
        String key = sha256(debugOption + "\n" + classPath.map(Path::toString).orElse("") + "\n" + sorted.stream()
                .map(source -> source.path() + "\n" + source.code())
                .collect(Collectors.joining("\n\u0000\n")));
        return COMPILED.computeIfAbsent(key, _ -> compileInto(WORK.resolve(key), debugOption, classPath, sorted));
    }

    private static Path compileInto(Path directory, String debugOption, Optional<Path> classPath,
                                    List<FixtureSource> sources) {
        try {
            Path sourceRoot = Files.createDirectories(directory.resolve("src"));
            Path classRoot = Files.createDirectories(directory.resolve("classes"));
            Path fixtureClassPath = classPath.orElse(Files.createDirectories(directory.resolve("empty-class-path")));
            List<Path> files = new ArrayList<>();
            for (FixtureSource source : sources) {
                Path file = sourceRoot.resolve(source.path());
                Files.createDirectories(file.getParent());
                files.add(Files.writeString(file, source.code(), StandardCharsets.UTF_8));
            }
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager =
                         compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
                List<String> options = List.of("--release", "25", "-proc:none", "-implicit:none", "-encoding", "UTF-8",
                        "-nowarn", debugOption, "-classpath", fixtureClassPath.toString(), "-d", classRoot.toString());
                Boolean compiled = compiler.getTask(null, fileManager, diagnostics, options, null,
                        fileManager.getJavaFileObjectsFromPaths(files)).call();
                if (!Boolean.TRUE.equals(compiled)) {
                    throw new IllegalStateException("fixture sources do not compile: " + diagnostics.getDiagnostics());
                }
            }
            return classRoot;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path createWorkDirectory() {
        try {
            Path work = Files.createTempDirectory("c4-contract-kit-fixtures-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(work)));
            return work;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not remove the fixture directory " + root, e);
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
