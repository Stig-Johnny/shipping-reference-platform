package io.github.stigjohnny.shipping.tenants.internal.domain;

import io.github.stigjohnny.shipping.tenants.api.TenantId;
import io.github.stigjohnny.shipping.tenants.api.UserId;
import java.util.Objects;

/**
 * A user's membership of one tenant, the only source of the tenant a request works for. v0.1 gives a user at most one
 * membership. Display names are synthetic and 1 to 70 characters long, as the contract's Membership schema requires.
 */
public record Membership(UserId userId, TenantId tenantId, MembershipRole role, String userDisplayName,
        String tenantDisplayName) {

    private static final int MAX_DISPLAY_NAME = 70;

    public Membership {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(role, "role");
        displayName("userDisplayName", userDisplayName);
        displayName("tenantDisplayName", tenantDisplayName);
    }

    private static void displayName(String name, String value) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > MAX_DISPLAY_NAME) {
            throw new IllegalArgumentException(name + " must be 1 to " + MAX_DISPLAY_NAME + " characters, not blank.");
        }
    }
}
