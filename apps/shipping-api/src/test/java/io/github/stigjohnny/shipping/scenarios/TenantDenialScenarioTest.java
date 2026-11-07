package io.github.stigjohnny.shipping.scenarios;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.EXAMPLE;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.id;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.offered;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.OfferLookup;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.QuoteOffers;
import io.github.stigjohnny.shipping.quotes.internal.application.CreateQuote;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.scenarios.support.Observers;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioRun;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * The tenant-denial scenario, dynamic view ScenarioTenantDenial, run on the built modules: Tenant Beta asks QuoteOffers
 * for an offer of Tenant Alpha's quote and is told the quote does not exist. Shipments, the caller that the view draws,
 * is planned, so the test calls QuoteOffers itself; the steps that touch shipments, HTTP or the database are not
 * evaluated, and the report counts them. What this run does check is that nothing else crosses a module boundary, for
 * example that the lookup does not ask the tenants module for the current tenant instead of using the one it is given.
 */
class TenantDenialScenarioTest {

    @Test
    void anotherTenantsQuoteIsNotFound(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.start("ScenarioTenantDenial", test, Observers.inProcess(),
                InProcessModules.Core.class, InProcessModules.InMemoryStore.class, InProcessModules.Quotes.class)) {
            TenantDenial.runIn(run);
            run.verify();
        }
    }

    /** The scenario's setup, stimulus and outcomes, shared with the negative fixtures that alter its code. */
    static final class TenantDenial {

        private TenantDenial() {
        }

        static void runIn(ScenarioRun run) {
            ThreadTenantContext tenants = run.implementation(CurrentTenant.class, ThreadTenantContext.class);
            CreateQuote createQuote = run.bean(CreateQuote.class);
            QuoteOffers offers = run.bean(QuoteOffers.class);
            Quote alphas = run.setup("Tenant Alpha's clerk creates a quote",
                    () -> offered(tenants.callAs(ALPHA, () -> createQuote.handle(EXAMPLE))));
            OfferId standard = alphas.offers().get(1).id();
            Instant beforeExpiry = alphas.expiresAt().minusSeconds(60);

            OfferLookup lookup = run.stimulus("beta", "Tenant Beta's booking asks for an offer of Tenant Alpha's quote: "
                    + "the test binds Tenant Beta and calls QuoteOffers.findOffer itself, in place of the planned "
                    + "shipments module", () -> tenants.callAs(BETA,
                            () -> offers.findOffer(BETA, alphas.id(), standard, beforeExpiry)));

            run.outcome("Tenant Beta is told that the quote does not exist, exactly as for an unknown quote ID", () -> {
                assertThat(lookup).isEqualTo(new OfferLookup.QuoteNotFound());
                assertThat(tenants.callAs(BETA, () -> offers.findOffer(BETA, new QuoteId(id(999)), standard,
                        beforeExpiry))).isEqualTo(lookup);
            });
            run.outcome("The quote exists: for Tenant Alpha the same offer is bookable at the same instant",
                    () -> assertThat(tenants.callAs(ALPHA, () -> offers.findOffer(ALPHA, alphas.id(), standard,
                            beforeExpiry))).isInstanceOf(OfferLookup.Bookable.class));
        }
    }
}
