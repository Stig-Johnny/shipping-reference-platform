/**
 * The tenants module (docs/modules/tenants.md): the tenant of the current unit of work. Other modules use only its
 * {@code api} package. Built so far: the tenant's identity and the thread-bound tenant context; demo authentication,
 * tenant binding for row-level security, TenantScope and prototype flags are planned.
 */
@ApplicationModule
@NullMarked
package io.github.stigjohnny.shipping.tenants;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
