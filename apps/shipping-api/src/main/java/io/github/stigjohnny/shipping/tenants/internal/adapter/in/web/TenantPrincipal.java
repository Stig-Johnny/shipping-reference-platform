package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import io.github.stigjohnny.shipping.tenants.internal.domain.Membership;
import java.util.Objects;

/** The principal of an authenticated member: the membership its demo token resolved to, and the only tenant source. */
record TenantPrincipal(Membership membership) {

    TenantPrincipal {
        Objects.requireNonNull(membership, "membership");
    }
}
