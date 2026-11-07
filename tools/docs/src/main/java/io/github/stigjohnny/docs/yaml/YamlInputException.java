package io.github.stigjohnny.docs.yaml;

import io.github.stigjohnny.docs.DocsException;
import java.io.Serial;

/** A YAML or JSON input that this tool refuses to read, or that lacks a value the tool needs. */
public final class YamlInputException extends DocsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public YamlInputException(String message) {
        super(message);
    }

    public YamlInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
