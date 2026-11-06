package io.github.stigjohnny.c4contractkit.model;

import java.nio.file.Path;

/** Reads a Structurizr DSL file, restricted, and validates it into a {@link ContractModel}. */
public final class ContractModelReader {

    private ContractModelReader() {
    }

    /**
     * Parses and validates a model.
     *
     * @param dsl the model file
     * @param displayPath how problems name the file, for example a path relative to the working directory
     * @return the validated model
     * @throws InvalidModelException listing every problem, when the model cannot be parsed or breaks a rule
     */
    public static ContractModel read(Path dsl, String displayPath) throws InvalidModelException {
        Problems problems = new Problems();
        ParsedModel parsed = RestrictedDslParser.parse(dsl, displayPath, problems);
        return new ContractModelBuilder(parsed, problems).build();
    }
}
