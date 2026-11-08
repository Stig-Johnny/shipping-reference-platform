/**
 * The tenants module (docs/modules/tenants.md): the tenant of the current unit of work. Other modules use only its
 * {@code api} package. Built so far: the tenant's identity, the thread-bound tenant context, demo-token authentication
 * through a membership directory port, the filter that binds the member's tenant for each request, and GET
 * /api/v1/me; the directory's database adapter, tenant binding for row-level security, TenantScope and prototype
 * flags are planned.
 */
@ApplicationModule
@NullMarked
package io.github.stigjohnny.shipping.tenants;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
