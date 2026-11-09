package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.*;
import io.github.stigjohnny.shipping.tenants.internal.application.MembershipResolution;
import io.github.stigjohnny.shipping.tenants.internal.domain.DemoToken;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

interface MembershipDirectoryContract {
    StoreSession session() throws SQLException;

    @Test
    default void tokensResolveTheirOwnMembershipAndDistinguishMissingMembershipFromUnknown() throws SQLException {
        try (StoreSession s = session()) {
            assertThat(s.memberships().resolve(new DemoToken(CLERK_ALPHA).sha256()))
                    .isEqualTo(new MembershipResolution.Member(ALPHA));
            assertThat(s.memberships().resolve(new DemoToken(CLERK_BETA).sha256()))
                    .isEqualTo(new MembershipResolution.Member(BETA));
            assertThat(s.memberships().resolve(new DemoToken(NO_TENANT_USER).sha256()))
                    .isEqualTo(new MembershipResolution.NoMembership(NO_TENANT_USER_ID));
            assertThat(s.memberships().resolve(new DemoToken(UNKNOWN).sha256()))
                    .isEqualTo(new MembershipResolution.UnknownToken());
        }
    }
}
