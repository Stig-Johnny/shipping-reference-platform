package io.github.stigjohnny.docs;

import java.io.Serial;

/**
 * A documentation problem that fails the build: an unreadable or invalid input, a broken reference, a claim without
 * evidence, an unsafe link or diagram, or drift in a generated file. Messages name repository-relative paths only.
 */
public class DocsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String problem;

    public DocsException(String message) {
        super(message);
        this.problem = message;
    }

    public DocsException(String message, Throwable cause) {
        super(message, cause);
        this.problem = message;
    }

    /** The problem in words; never null. */
    @Override
    public String getMessage() {
        return problem;
    }
}
