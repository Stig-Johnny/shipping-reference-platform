package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import io.github.stigjohnny.shipping.tenants.internal.domain.Membership;
import io.github.stigjohnny.shipping.tenants.internal.domain.MembershipRole;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/me (getCurrentMembership): the caller's membership, from the principal the demo token resolved to. The
 * security chain lets only members reach it. Prototype flags are planned, so every flag is off: enabledFlags is empty.
 */
@RestController
class MeController {

    /** The contract's Membership schema. */
    record MembershipBody(UUID userId, String userDisplayName, UUID tenantId, String tenantDisplayName,
            MembershipRole role, List<String> enabledFlags) {

        static MembershipBody of(Membership membership) {
            return new MembershipBody(membership.userId().value(), membership.userDisplayName(),
                    membership.tenantId().value(), membership.tenantDisplayName(), membership.role(), List.of());
        }
    }

    @GetMapping(path = "/api/v1/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public MembershipBody getCurrentMembership(
            @AuthenticationPrincipal(errorOnInvalidType = true) TenantPrincipal member) {
        return MembershipBody.of(member.membership());
    }
}
