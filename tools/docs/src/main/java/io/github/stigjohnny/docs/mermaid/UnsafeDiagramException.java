package io.github.stigjohnny.docs.mermaid;

import io.github.stigjohnny.docs.DocsException;
import java.io.Serial;

/** A diagram definition that the validator or the adapter refuses to publish. */
public final class UnsafeDiagramException extends DocsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnsafeDiagramException(String message) {
        super(message);
    }
}
