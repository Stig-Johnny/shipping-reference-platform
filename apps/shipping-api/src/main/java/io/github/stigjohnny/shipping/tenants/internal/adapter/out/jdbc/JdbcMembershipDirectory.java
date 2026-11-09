package io.github.stigjohnny.shipping.tenants.internal.adapter.out.jdbc;

import io.github.stigjohnny.shipping.tenants.api.TenantId;
import io.github.stigjohnny.shipping.tenants.api.UserId;
import io.github.stigjohnny.shipping.tenants.internal.application.MembershipDirectory;
import io.github.stigjohnny.shipping.tenants.internal.application.MembershipResolution;
import io.github.stigjohnny.shipping.tenants.internal.domain.Membership;
import io.github.stigjohnny.shipping.tenants.internal.domain.MembershipRole;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Pre-authentication lookup: no tenant is known yet; only the reviewed security-definer function reads identity. */
@Repository
@Profile("postgres")
@Transactional
public class JdbcMembershipDirectory implements MembershipDirectory {
    private final JdbcClient jdbc;

    public JdbcMembershipDirectory(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override public MembershipResolution resolve(byte[] tokenSha256) {
        jdbc.sql("select set_config('app.tenant_id', '', true)")
                .query(String.class)
                .single();
        return jdbc.sql("select * from tenants.resolve_demo_token(:hash)")
                .param("hash", tokenSha256)
                .query((row, _) -> {
                    UserId user = new UserId(UUID.fromString(row.getString("user_id")));
                    String tenant = row.getString("tenant_id");
                    if (tenant == null) {
                        return (MembershipResolution) new MembershipResolution.NoMembership(user);
                    }
                    return (MembershipResolution) new MembershipResolution.Member(new Membership(user,
                            new TenantId(UUID.fromString(tenant)), MembershipRole.valueOf(row.getString("role")),
                            row.getString("user_display_name"), row.getString("tenant_display_name")));
                })
                .optional().orElseGet(MembershipResolution.UnknownToken::new);
    }
}
