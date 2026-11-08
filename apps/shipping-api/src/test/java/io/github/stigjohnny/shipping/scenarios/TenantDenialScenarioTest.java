package io.github.stigjohnny.shipping.scenarios;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.EXAMPLE;
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
import io.github.stigjohnny.shipping.shipments.internal.application.BookShipment;
import io.github.stigjohnny.shipping.shipments.internal.application.ShipmentStore;
import io.github.stigjohnny.shipping.shipments.internal.domain.BookingOutcome;
import io.github.stigjohnny.shipping.shipments.internal.domain.BookingRejection;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdempotencyKey;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * The tenant-denial scenario, dynamic view ScenarioTenantDenial, run on the built modules: a clerk of Tenant Beta books
 * an offer of Tenant Alpha's quote through the shipments module's booking use case, and is refused as for an unknown
 * quote. The two module API calls are evaluated; the HTTP and database steps are not, and the report says why. The
 * refusal, and that nothing was stored for Tenant Beta, are asserted separately.
 */
class TenantDenialScenarioTest {

    @Test
    void anotherTenantsQuoteIsNotFound(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.start("ScenarioTenantDenial", test, Observers.inProcess(),
                TenantDenial.MODULES)) {
            TenantDenial.runIn(run);
            run.verify();
            assertThat(run.result().verdict()).isEqualTo("COVERAGE_INCOMPLETE");
            assertThat(run.result().comparison().conforms()).isFalse();
            assertThat(run.result().failing()).hasSize(77);
            assertThat(run.result().markdown()).content().contains("coverage incomplete: 77 known gaps",
                    "Steps evaluated: 2 of 6, passed: 2, not evaluated: 4", "Outcomes held: 3 of 3");
        }
    }

    /** The scenario's setup, stimulus and outcomes, shared with the negative fixtures that alter its code. */
    static final class TenantDenial {

        /** The real modules: tenants, quotes and shipments, with the in-memory stores. */
        static final Class<?>[] MODULES = {InProcessModules.Core.class, InProcessModules.InMemoryStore.class,
            InProcessModules.InMemoryShipments.class, InProcessModules.Quotes.class, InProcessModules.Shipments.class};

        private TenantDenial() {
        }

        static void runIn(ScenarioRun run) {
            ThreadTenantContext tenants = run.implementation(CurrentTenant.class, ThreadTenantContext.class);
            CreateQuote createQuote = run.bean(CreateQuote.class);
            BookShipment bookShipment = run.bean(BookShipment.class);
            Quote alphas = run.setup("Tenant Alpha's clerk creates a quote",
                    () -> offered(tenants.callAs(ALPHA, () -> createQuote.handle(EXAMPLE))));
            OfferId standard = alphas.offers().get(1).id();

            BookingOutcome outcome = run.stimulus("beta", "Tenant Beta's clerk books the STANDARD offer of Tenant "
                    + "Alpha's quote: the test binds Tenant Beta and calls the shipments use case BookShipment, in "
                    + "place of POST /api/v1/shipments", () -> tenants.callAs(BETA, () -> bookShipment.handle(
                            ShipmentFixtures.KEY, ShipmentFixtures.command(alphas.id(), standard))));

            QuoteOffers offers = run.bean(QuoteOffers.class);
            ShipmentStore shipments = run.bean(ShipmentStore.class);
            Instant beforeExpiry = alphas.expiresAt().minusSeconds(60);
            run.outcome("Tenant Beta is refused with quote-not-found, exactly as for an unknown quote ID", () -> {
                assertThat(outcome).isEqualTo(new BookingOutcome.Rejected(BookingRejection.QUOTE_NOT_FOUND));
                BookingOutcome unknown = tenants.callAs(BETA, () -> bookShipment.handle(
                        new IdempotencyKey("unknown-quote-key-0001"), ShipmentFixtures.command(
                                new QuoteId(UUID.fromString("00000000-0000-4000-8000-000000000999")), standard)));
                assertThat(unknown).isEqualTo(outcome);
            });
            run.outcome("Nothing was stored for Tenant Beta under the booking's key", () -> assertThat(
                    shipments.findByKey(BETA, ShipmentFixtures.KEY)).isEmpty());
            run.outcome("The quote exists: for Tenant Alpha the same offer is bookable at the same instant",
                    () -> assertThat(tenants.callAs(ALPHA, () -> offers.findOffer(ALPHA, alphas.id(), standard,
                            beforeExpiry))).isInstanceOf(OfferLookup.Bookable.class));
        }
    }
}
