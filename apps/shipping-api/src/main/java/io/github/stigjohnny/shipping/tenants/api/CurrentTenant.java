package io.github.stigjohnny.shipping.tenants.api;

/**
 * The tenant of the current unit of work. A module asks for it here and never takes a tenant from a header, a path,
 * a query or a body.
 */
public interface CurrentTenant {

    /**
     * The tenant bound to the current thread.
     *
     * @throws MissingTenantContextException when none is bound: a programming error, since the planned HTTP filter
     *     binds a tenant before any controller runs, and a listener binds the event's tenant before its work
     */
    TenantId require();
}
