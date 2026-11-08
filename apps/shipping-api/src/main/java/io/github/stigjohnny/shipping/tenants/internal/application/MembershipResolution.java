package io.github.stigjohnny.shipping.tenants.internal.application;

import io.github.stigjohnny.shipping.tenants.api.UserId;
import io.github.stigjohnny.shipping.tenants.internal.domain.Membership;
import java.util.Objects;

/** The expected outcomes of resolving a presented demo token: a member, a user without a tenant, or nobody. */
public sealed interface MembershipResolution permits MembershipResolution.Member, MembershipResolution.NoMembership,
        MembershipResolution.UnknownToken {

    /** The token belongs to a user with a membership: the request works for that membership's tenant. */
    record Member(Membership membership) implements MembershipResolution {

        public Member {
            Objects.requireNonNull(membership, "membership");
        }
    }

    /** The token belongs to a user who belongs to no tenant. */
    record NoMembership(UserId userId) implements MembershipResolution {

        public NoMembership {
            Objects.requireNonNull(userId, "userId");
        }
    }

    /** The token is malformed, or no user has it. */
    record UnknownToken() implements MembershipResolution {
    }
}
