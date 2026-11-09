package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import io.github.stigjohnny.shipping.tenants.internal.application.MembershipDirectory;
import io.github.stigjohnny.shipping.tenants.internal.application.MembershipResolution;
import io.github.stigjohnny.shipping.tenants.internal.domain.DemoToken;

import io.github.stigjohnny.shipping.tenants.api.TenantId;
import io.github.stigjohnny.shipping.tenants.api.UserId;
import io.github.stigjohnny.shipping.tenants.internal.domain.Membership;
import io.github.stigjohnny.shipping.tenants.internal.domain.MembershipRole;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** The documented synthetic users only, for the disposable memory demo. */
public final class DemoMembershipDirectory implements MembershipDirectory {

    public static final String CLERK_ALPHA = "demo-clerk-alpha";
    public static final String CLERK_BETA = "demo-clerk-beta";
    public static final String NO_TENANT_USER = "demo-no-tenant-user";
    /** Well formed, but no user has it. */
    public static final String UNKNOWN = "demo-nobody-has-this";
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
        return byHash.getOrDefault(hash, new MembershipResolution.UnknownToken());
    }

    private static String hash(String token) {
        return HexFormat.of().formatHex(new DemoToken(token).sha256());
    }
}
