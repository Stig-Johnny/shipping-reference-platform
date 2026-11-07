package io.github.stigjohnny.shipping.scenarios;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.offered;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.quotes.internal.application.QuoteStore;
import io.github.stigjohnny.shipping.quotes.internal.domain.Offer;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteResult;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioRun;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Duration;

/** The outcome of issuing a quote, asserted separately from the interactions that produced it. */
final class QuoteOutcomes {

    private QuoteOutcomes() {
    }

    /**
     * The quote is offered for the tenant at the fixed clock, with three offers, and stored for that tenant; the other
     * tenant does not find it. Each description starts with the quote's name, such as "Tenant Alpha's quote".
     */
    static void issuedFor(ScenarioRun run, String name, QuoteResult result, TenantId tenant, TenantId other) {
        QuoteStore store = run.bean(QuoteStore.class);
        run.outcome(name + " is offered with ECONOMY, STANDARD and EXPRESS offers, in level order",
                () -> assertThat(offered(result).offers()).extracting(Offer::level)
                        .containsExactly(ServiceLevel.ECONOMY, ServiceLevel.STANDARD, ServiceLevel.EXPRESS));
        run.outcome(name + " belongs to the bound tenant, was issued at the fixed clock's instant and expires 30 "
                + "minutes later", () -> {
                    Quote quote = offered(result);
                    assertThat(quote.tenantId()).isEqualTo(tenant);
                    assertThat(quote.issuedAt()).isEqualTo(InProcessModules.NOW);
                    assertThat(quote.expiresAt()).isEqualTo(quote.issuedAt().plus(Duration.ofMinutes(30)));
                });
        run.outcome(name + " is stored under the bound tenant, and the other tenant does not find it", () -> {
            Quote quote = offered(result);
            assertThat(store.find(tenant, quote.id())).contains(quote);
            assertThat(store.find(other, quote.id())).isEmpty();
        });
    }
}
