package io.github.stigjohnny.shipping.tenants.internal.application;

import io.github.stigjohnny.shipping.tenants.internal.domain.DemoToken;
import java.util.Objects;

/**
 * Resolves a presented demo token to its user's membership. A token that does not have the demo token format is
 * unknown without a lookup; any other is looked up by its SHA-256, never by its text.
 */
public final class ResolveMembership {

    private final MembershipDirectory directory;

    public ResolveMembership(MembershipDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /** The membership the presented token stands for, a user without one, or nobody. */
    public MembershipResolution resolve(String presentedToken) {
        return DemoToken.parse(presentedToken).map(token -> directory.resolve(token.sha256()))
                .orElseGet(MembershipResolution.UnknownToken::new);
    }
}
