package io.github.stigjohnny.c4contractkit.api;

import io.github.stigjohnny.c4contractkit.model.ContractModelReader;
import io.github.stigjohnny.c4contractkit.model.InvalidModelException;
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
     * @param productionClassRoots directories or JAR files of compiled production classes, in any order
     * @return what the comparison found; {@link Report#passed()} tells whether the classes conform
     * @throws ModelException listing every model problem, when the model is invalid or uses a rejected construct
     */
    public static Report check(Path model, List<Path> productionClassRoots) {
        String modelPath = displayPath(model);
        try {
            ContractModelReader.read(model, modelPath);
        } catch (InvalidModelException e) {
            throw new ModelException(modelPath, e.problems());
        }
        throw new UnsupportedOperationException("comparing " + productionClassRoots.size()
                + " class roots with the model is not implemented yet (c4-contract-kit slice 4)");
    }

    /** The path relative to the working directory, with {@code /} as separator, as reports show it. */
    static String displayPath(Path path) {
        Path workingDirectory = Path.of("").toAbsolutePath();
        return workingDirectory.relativize(path.toAbsolutePath().normalize()).toString()
                .replace(File.separatorChar, '/');
    }
}
