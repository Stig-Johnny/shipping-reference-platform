package io.github.stigjohnny.shipping.tenants.internal.application;

import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.api.MissingTenantContextException;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link CurrentTenant} from a binding to the current thread that lasts exactly as long as one unit of work. The
 * binding is a {@link ScopedValue} (final in Java 25), so it cannot outlive the work: when the work returns or throws,
 * the previous binding, or none, applies again, and no other thread sees it. The planned HTTP filter and
 * {@code TenantScope} (docs/modules/tenants.md) are to bind through it.
 */
public final class ThreadTenantContext implements CurrentTenant {

    private static final ScopedValue<TenantId> BOUND = ScopedValue.newInstance();

    @Override
    public TenantId require() {
        if (!BOUND.isBound()) {
            throw new MissingTenantContextException();
        }
        return BOUND.get();
    }

    /** Runs {@code work} with {@code tenant} bound to the current thread, and returns its result. */
    public <T> T callAs(TenantId tenant, Supplier<T> work) {
        return ScopedValue.where(BOUND, Objects.requireNonNull(tenant, "tenant")).call(work::get);
    }

    /** Runs {@code work} with {@code tenant} bound to the current thread. */
    public void runAs(TenantId tenant, Runnable work) {
        ScopedValue.where(BOUND, Objects.requireNonNull(tenant, "tenant")).run(work);
    }
}
