package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import io.github.stigjohnny.shipping.tenants.api.UserId;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * A demo bearer token as Spring Security sees it: first the presented token, not yet authenticated; then, once
 * resolved, a member with the {@code TENANT_MEMBER} authority, or a user without a tenant and without authorities.
 * The token is erased after authentication and its string form never shows it. The application is stateless, so it is
 * never serialised; its fields are transient.
 */
final class DemoTokenAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Object principal;
    private transient @Nullable String token;

    private DemoTokenAuthentication(Object principal, @Nullable String token,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = token;
        super.setAuthenticated(token == null);
    }

    /** The token a request presented, before it is resolved. */
    static DemoTokenAuthentication presented(String token) {
        return new DemoTokenAuthentication("presented demo token", Objects.requireNonNull(token, "token"), List.of());
    }

    /** A user with a tenant membership. */
    static DemoTokenAuthentication member(TenantPrincipal member) {
        return new DemoTokenAuthentication(member, null,
                List.of(new SimpleGrantedAuthority(SecurityConfiguration.TENANT_MEMBER)));
    }

    /** A user who belongs to no tenant: authenticated, but without authority to call the API. */
    static DemoTokenAuthentication withoutTenant(UserId user) {
        return new DemoTokenAuthentication(user, null, List.of());
    }

    /** The presented token, while it is not yet resolved. */
    String presentedToken() {
        return Objects.requireNonNull(token, "The token is erased once it is resolved.");
    }

    @Override
    public @Nullable Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        token = null;
    }
}
