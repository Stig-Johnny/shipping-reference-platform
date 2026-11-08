package io.github.stigjohnny.c4contractkit.model;

import java.nio.file.Path;

/**
 * Reads the web part of a Structurizr DSL file, restricted, and validates it into a {@link WebModel}: the container
 * with {@code web.root}, its components and their relationships (the header of architecture/workspace.dsl). It uses
 * the same restricted parser as {@link ContractModelReader}; the two vocabularies are checked apart, and neither
 * reader accepts the other's properties on its own elements.
 */
public final class WebModelReader {

    private WebModelReader() {
    }

    /**
     * Parses and validates the web part of a model.
     *
     * @param dsl the model file
     * @param displayPath how problems name the file, for example a path relative to the working directory
     * @return the validated web part
     * @throws InvalidModelException listing every problem, when the model cannot be parsed or breaks a rule
     */
    public static WebModel read(Path dsl, String displayPath) throws InvalidModelException {
        Problems problems = new Problems();
        ParsedModel parsed = RestrictedDslParser.parse(dsl, displayPath, problems);
        return new WebModelBuilder(parsed, problems).build();
    }
}
