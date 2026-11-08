package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import io.github.stigjohnny.shipping.tenants.api.UserId;
import io.github.stigjohnny.shipping.tenants.internal.application.MembershipResolution;
import io.github.stigjohnny.shipping.tenants.internal.application.ResolveMembership;
import io.github.stigjohnny.shipping.tenants.internal.domain.Membership;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

/**
 * Resolves a presented demo token to a member, a user without a tenant, or nobody, which fails authentication. The
 * tenant comes only from the membership the token resolves to.
 */
final class DemoTokenAuthenticationProvider implements AuthenticationProvider {

    private final ResolveMembership resolveMembership;

    DemoTokenAuthenticationProvider(ResolveMembership resolveMembership) {
        this.resolveMembership = Objects.requireNonNull(resolveMembership, "resolveMembership");
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String token = ((DemoTokenAuthentication) authentication).presentedToken();
        return switch (resolveMembership.resolve(token)) {
            case MembershipResolution.Member(Membership membership) ->
                    DemoTokenAuthentication.member(new TenantPrincipal(membership));
            case MembershipResolution.NoMembership(UserId user) -> DemoTokenAuthentication.withoutTenant(user);
            case MembershipResolution.UnknownToken() -> throw new BadCredentialsException("No user has this token.");
        };
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return DemoTokenAuthentication.class.isAssignableFrom(authentication);
    }
}
