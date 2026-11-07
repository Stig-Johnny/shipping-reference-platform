package io.github.stigjohnny.c4contractkit.sources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads a web root as data: classifies every regular file by its name and the kind of root, scans the code files, the
 * tests and the style sheets, and resolves each import against the files found. Nothing is executed, compiled or
 * loaded, and the reader resolves no path alias: a specifier that relies on one is unsupported or names a package.
 *
 * <p>Files and directories whose names start with a dot are not part of the web root's source set; an import of one
 * is therefore unresolved. A symbolic link anywhere under the root, hidden ones included, or as the root, is an error:
 * links are checked before anything is left out, and hidden directories are searched for them too.
 */
public final class SourceReader {

    private SourceReader() {
    }

    /**
     * Reads one web root.
     *
     * @param directory the web root on disk; a root that does not exist has no files
     * @param root how reports name it, for example {@code apps/shipping-web/src}
     * @throws IllegalArgumentException if the root is not a directory, is or holds a symbolic link, or holds a file
     *     that is neither regular nor a directory
     * @throws UncheckedIOException if a file cannot be read
     */
    public static SourceSet read(Path directory, String root) {
        return read(directory, root, RootKind.SOURCE);
    }

    /**
     * Reads one root of the given kind; see {@link #read(Path, String)}. In a test root every code file is a test, and
     * a public root may hold style sheets, images and fonts only: any other file there is unsupported.
     */
    public static SourceSet read(Path directory, String root, RootKind kind) {
        if (Files.isSymbolicLink(directory)) {
            throw new IllegalArgumentException("The web root " + root
                    + " is a symbolic link: the check reads only regular files and directories");
        }
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return new SourceSet(root, List.of(), List.of(), List.of());
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("The web root " + root + " is not a directory");
        }
        List<SourceFile> files = walk(directory, root, kind);
        Set<String> paths = files.stream().map(SourceFile::path).collect(Collectors.toUnmodifiableSet());
        List<ResolvedImport> imports = new ArrayList<>();
        List<FileRefusal> refusals = new ArrayList<>();
        for (SourceFile file : files) {
            if (file.kind() != FileKind.CODE && file.kind() != FileKind.TEST && file.kind() != FileKind.STYLE) {
                continue;
            }
            Optional<String> text = text(directory.resolve(file.path()), root + "/" + file.path());
            if (text.isEmpty()) {
                refusals.add(new FileRefusal(file.path(), 1, "the file is not valid UTF-8"));
            } else if (file.kind() == FileKind.STYLE) {
                ImportScanner.scanStyle(text.get()).forEach(refusal ->
                        refusals.add(new FileRefusal(file.path(), refusal.line(), refusal.reason())));
            } else {
                ImportScanner.Scan scan = ImportScanner.scan(text.get(), file.path().endsWith(".tsx"));
                for (SourceImport found : scan.imports()) {
                    imports.add(new ResolvedImport(file.path(), found.line(), found.specifier(), found.kind(),
                            Specifiers.resolve(file.path(), found.specifier(), paths)));
                }
                scan.refusals().forEach(refusal ->
                        refusals.add(new FileRefusal(file.path(), refusal.line(), refusal.reason())));
            }
        }
        return new SourceSet(root, files, imports, refusals);
    }

    /** What a file of a source root is, by its name. */
    static FileKind kind(String name) {
        return kind(name, RootKind.SOURCE);
    }

    /** What a file is, by its name and the kind of root it is in. */
    static FileKind kind(String name, RootKind root) {
        boolean code = name.endsWith(".ts") || name.endsWith(".tsx");
        if (root == RootKind.PUBLIC && code) {
            return FileKind.UNSUPPORTED;
        }
        if (code && (root == RootKind.TEST || name.endsWith(".test.ts") || name.endsWith(".test.tsx"))) {
            return FileKind.TEST;
        }
        if (code) {
            return FileKind.CODE;
        }
        if (name.endsWith(".css")) {
            return FileKind.STYLE;
        }
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1);
        return Specifiers.FILE_EXTENSIONS.contains(extension) ? FileKind.ASSET : FileKind.UNSUPPORTED;
    }

    private static List<SourceFile> walk(Path directory, String root, RootKind kind) {
        List<SourceFile> files = new ArrayList<>();
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    // Links and special files are refused first, hidden ones included; only then is anything left out.
                    String path = relative(directory, file);
                    if (attributes.isSymbolicLink()) {
                        throw new IllegalArgumentException("The web root " + root + " holds a symbolic link, " + path
                                + ": the check reads only regular files and directories");
                    }
                    if (!attributes.isRegularFile()) {
                        throw new IllegalArgumentException("The web root " + root + " holds " + path
                                + ", which is neither a regular file nor a directory");
                    }
                    if (!hidden(path)) {
                        files.add(new SourceFile(path, kind(file.getFileName().toString(), kind)));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("The web root " + root + " could not be read", e);
        }
        return files.stream().sorted(Comparator.comparing(SourceFile::path)).toList();
    }

    /** Whether a name on the path, relative to the root, starts with a dot. */
    private static boolean hidden(String path) {
        for (String name : path.split("/", -1)) {
            if (name.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static String relative(Path directory, Path file) {
        List<String> names = new ArrayList<>();
        directory.relativize(file).forEach(name -> names.add(name.toString()));
        return String.join("/", names);
    }

    /** The file as UTF-8, or empty when it is not valid UTF-8. */
    private static Optional<String> text(Path file, String shown) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("The file " + shown + " could not be read", e);
        }
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }
}
