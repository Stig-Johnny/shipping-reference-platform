package io.github.stigjohnny.shipping.scenarios;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.EXAMPLE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.shipping.quotes.internal.application.CreateQuote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteResult;
import io.github.stigjohnny.shipping.scenarios.support.Observers;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioRun;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * The quote scenario, dynamic view ScenarioQuote, run on the built modules: Tenant Alpha's clerk asks for a quote, and
 * the recorder's interactions are compared with architecture/scenarios/scenario-quote.json. The HTTP and database
 * steps are not evaluated in process; the report says why. The outcome is asserted separately.
 */
class QuoteScenarioTest {

    @Test
    void aQuoteIsIssuedTheWayItsScenarioDrawsIt(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.start("ScenarioQuote", test, Observers.inProcess(),
                InProcessModules.Core.class, InProcessModules.InMemoryStore.class, InProcessModules.Quotes.class)) {
            ThreadTenantContext tenants = run.implementation(CurrentTenant.class, ThreadTenantContext.class);
            CreateQuote createQuote = run.bean(CreateQuote.class);

            QuoteResult result = run.stimulus("alpha", "Tenant Alpha's clerk asks for a quote: the test binds Tenant "
                    + "Alpha and calls the quotes use case CreateQuote with the contract's example request, in place "
                    + "of POST /api/v1/quotes", () -> tenants.callAs(ALPHA, () -> createQuote.handle(EXAMPLE)));

            QuoteOutcomes.issuedFor(run, "Tenant Alpha's quote", result, ALPHA, BETA);
            run.verify();

            assertThat(run.result().verdict()).isEqualTo("COVERAGE_INCOMPLETE");
            assertThat(run.result().comparison().conforms()).isFalse();
            assertThat(run.result().failing()).hasSize(77);
            assertThat(run.result().markdown()).content().contains("coverage incomplete: 77 known gaps",
                    "Steps evaluated: 2 of 6, passed: 2, not evaluated: 4", "Outcomes held: 3 of 3")
                    .doesNotContain("**Verdict: CONFORMS**");
        }
    }
}
