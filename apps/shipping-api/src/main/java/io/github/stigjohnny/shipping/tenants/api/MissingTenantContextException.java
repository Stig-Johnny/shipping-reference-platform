package io.github.stigjohnny.shipping.tenants.api;

import java.io.Serial;

/** Code needs the current tenant, but none is bound: a programming error, never a client's. */
public final class MissingTenantContextException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MissingTenantContextException() {
        super("No tenant is bound to the current thread.");
    }
}
