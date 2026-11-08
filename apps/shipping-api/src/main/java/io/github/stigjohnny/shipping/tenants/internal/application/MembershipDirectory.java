package io.github.stigjohnny.shipping.tenants.internal.application;

/**
 * The port through which a demo token's user and membership are found, by the token's SHA-256 only: the planned JDBC
 * adapter calls the security-definer function {@code tenants.resolve_demo_token(bytea)} (docs/modules/tenants.md); the
 * tests use the synthetic fixtures of that document.
 */
public interface MembershipDirectory {

    /**
     * The user and membership of the token with this SHA-256 (32 bytes): {@code UnknownToken} when no user has it,
     * {@code NoMembership} for a user without a tenant.
     */
    MembershipResolution resolve(byte[] tokenSha256);
}
