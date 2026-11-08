package io.github.stigjohnny.shipping.tenants.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.shipping.tenants.api.MissingTenantContextException;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The tenant of the current unit of work: bound for exactly the work's duration, and required where it is used. */
class ThreadTenantContextTest {

    private static final TenantId ALPHA = new TenantId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    private static final TenantId BETA = new TenantId(UUID.fromString("22222222-2222-4222-8222-222222222222"));

    private final ThreadTenantContext context = new ThreadTenantContext();

    @Test
    void withoutABindingTheTenantIsMissing() {
        assertThatThrownBy(context::require).isInstanceOf(MissingTenantContextException.class)
                .hasMessage("No tenant is bound to the current thread.");
    }

    @Test
    void theTenantIsBoundForTheWorkAndOnlyForIt() {
        assertThat(context.callAs(ALPHA, context::require)).isEqualTo(ALPHA);
        assertThatThrownBy(context::require).isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void anInnerBindingAppliesInsideAndTheOuterOneAgainAfterIt() {
        List<TenantId> seen = new ArrayList<>();

        context.runAs(ALPHA, () -> {
            seen.add(context.require());
            context.runAs(BETA, () -> seen.add(context.require()));
            seen.add(context.require());
        });

        assertThat(seen).containsExactly(ALPHA, BETA, ALPHA);
    }

    @Test
    void theBindingEndsWhenTheWorkThrows() {
        assertThatThrownBy(() -> context.runAs(ALPHA, () -> {
            throw new IllegalStateException("the work failed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("the work failed");

        assertThatThrownBy(context::require).isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void anIndependentlyStartedThreadDoesNotInheritTheBinding() throws InterruptedException {
        AtomicReference<Throwable> other = new AtomicReference<>();

        Thread thread = context.callAs(ALPHA, () -> Thread.ofPlatform().start(() -> {
            try {
                context.require();
            } catch (MissingTenantContextException e) {
                other.set(e);
            }
        }));
        thread.join();

        assertThat(other.get()).isInstanceOf(MissingTenantContextException.class);
    }
}
