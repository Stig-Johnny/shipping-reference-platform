package io.github.stigjohnny.c4contractkit.api;

import io.github.stigjohnny.c4contractkit.bytecode.ClassRootImporter;
import io.github.stigjohnny.c4contractkit.bytecode.ImportedClass;
import io.github.stigjohnny.c4contractkit.model.ContractModel;
import io.github.stigjohnny.c4contractkit.model.ContractModelReader;
import io.github.stigjohnny.c4contractkit.model.InvalidModelException;
import io.github.stigjohnny.c4contractkit.rules.ConformanceRules;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Checks compiled Java classes against a C4 model written in Structurizr DSL, in both directions: a dependency the
 * model does not declare fails, and so does a REQUIRED relationship without any static reference.
 */
public final class Conformance {

    private Conformance() {
    }

    /**
     * Parses the model in restricted mode, imports the class roots and compares them with the model.
     *
     * @param model the Structurizr DSL file, for example {@code architecture/workspace.dsl}
     * @param productionClassRoots directories of compiled production classes, such as {@code build/classes/java/main},
     *     in any order
     * @return what the comparison found; {@link Report#passed()} tells whether the classes conform
     * @throws ModelException listing every model problem, when the model is invalid or uses a rejected construct
     * @throws IllegalArgumentException if a class root is not a directory, if a class root is or holds a symbolic link
     *     to a directory, or if a class file under the roots could not be imported (a corrupt file, a file that is not
     *     a class file, or a second definition of a class)
     * @throws IllegalStateException if an {@code archunit.*} system property sets a class resolver or resolution from
     *     the class path, which the import would have to obey
     */
    public static Report check(Path model, List<Path> productionClassRoots) {
        String modelPath = displayPath(model);
        ContractModel contract;
        try {
            contract = ContractModelReader.read(model, modelPath);
        } catch (InvalidModelException e) {
            throw new ModelException(modelPath, e.problems());
        }
        List<ImportedClass> classes = ClassRootImporter.importRoots(productionClassRoots);
        return ResultMapping.toReport(modelPath, ConformanceRules.compare(contract, classes));
    }

    /** The path relative to the working directory, with {@code /} as separator, as reports show it. */
    static String displayPath(Path path) {
        Path workingDirectory = Path.of("").toAbsolutePath();
        return workingDirectory.relativize(path.toAbsolutePath().normalize()).toString()
                .replace(File.separatorChar, '/');
    }
}
