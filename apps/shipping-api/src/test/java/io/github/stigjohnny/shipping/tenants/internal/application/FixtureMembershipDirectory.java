package io.github.stigjohnny.shipping.tenants.internal.application;

import io.github.stigjohnny.shipping.tenants.api.TenantId;
import io.github.stigjohnny.shipping.tenants.api.UserId;
import io.github.stigjohnny.shipping.tenants.internal.domain.Membership;
import io.github.stigjohnny.shipping.tenants.internal.domain.MembershipRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * A test double of the {@link MembershipDirectory} port, holding the synthetic users and tenants of
 * docs/modules/tenants.md: like the planned database, it knows tokens only by their SHA-256. It stands in until the
 * JDBC adapter that calls {@code tenants.resolve_demo_token} exists, so tests that use it prove the HTTP edge, not the
 * database. One more token, {@link #FAILING_TOKEN}, makes it fail as an unavailable database would.
 */
public final class FixtureMembershipDirectory implements MembershipDirectory {

    public static final String CLERK_ALPHA = "demo-clerk-alpha";
    public static final String CLERK_BETA = "demo-clerk-beta";
    public static final String NO_TENANT_USER = "demo-no-tenant-user";
    /** Well formed, but no user has it. */
    public static final String UNKNOWN = "demo-nobody-has-this";
    /** Asking for it fails with an exception whose message no response may show. */
    public static final String FAILING_TOKEN = "demo-directory-unavailable";
    public static final String FAILURE_MESSAGE = "synthetic directory failure 7f3a";

    public static final TenantId TENANT_ALPHA = new TenantId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    public static final TenantId TENANT_BETA = new TenantId(UUID.fromString("22222222-2222-4222-8222-222222222222"));
    public static final Membership ALPHA = new Membership(new UserId(UUID.fromString(
            "00000000-0000-4000-8000-0000000000a1")), TENANT_ALPHA, MembershipRole.CLERK, "Clerk Alpha (synthetic)",
            "Tenant Alpha (synthetic)");
    public static final Membership BETA = new Membership(new UserId(UUID.fromString(
            "00000000-0000-4000-8000-0000000000b1")), TENANT_BETA, MembershipRole.CLERK, "Clerk Beta (synthetic)",
            "Tenant Beta (synthetic)");
    public static final UserId NO_TENANT_USER_ID = new UserId(UUID.fromString("00000000-0000-4000-8000-0000000000c1"));

    private final Map<String, MembershipResolution> byHash = Map.of(
            hash(CLERK_ALPHA), new MembershipResolution.Member(ALPHA),
            hash(CLERK_BETA), new MembershipResolution.Member(BETA),
            hash(NO_TENANT_USER), new MembershipResolution.NoMembership(NO_TENANT_USER_ID));

    @Override
    public MembershipResolution resolve(byte[] tokenSha256) {
        String hash = HexFormat.of().formatHex(tokenSha256);
        if (hash.equals(hash(FAILING_TOKEN))) {
            throw new IllegalStateException(FAILURE_MESSAGE);
        }
        return byHash.getOrDefault(hash, new MembershipResolution.UnknownToken());
    }

    /** As the seed computes it, sha256(convert_to(token, 'UTF8')), and independently of {@code DemoToken}. */
    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
