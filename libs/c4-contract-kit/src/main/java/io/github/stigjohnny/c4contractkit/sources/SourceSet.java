package io.github.stigjohnny.c4contractkit.sources;

import java.util.List;

/**
 * Everything read from one web root: its files, the imports of its code files and what the grammar refused. Every
 * list is sorted: files by path, imports and refusals by file and line.
 *
 * @param root how reports name the web root, for example {@code apps/shipping-web/src}
 */
public record SourceSet(String root, List<SourceFile> files, List<ResolvedImport> imports, List<FileRefusal> refusals) {

    public SourceSet {
        files = List.copyOf(files);
        imports = List.copyOf(imports);
        refusals = List.copyOf(refusals);
    }
}
