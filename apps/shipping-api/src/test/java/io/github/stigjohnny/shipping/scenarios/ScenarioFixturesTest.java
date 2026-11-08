package io.github.stigjohnny.shipping.scenarios;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.EXAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.stigjohnny.shipping.quotes.api.QuoteOffers;
import io.github.stigjohnny.shipping.quotes.internal.application.CreateQuote;
import io.github.stigjohnny.shipping.quotes.internal.application.GatedQuoteStore;
import io.github.stigjohnny.shipping.quotes.internal.application.QuoteStore;
import io.github.stigjohnny.shipping.quotes.internal.application.ScenarioAlterations;
import io.github.stigjohnny.shipping.quotes.internal.domain.IdGenerator;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteResult;
import io.github.stigjohnny.shipping.scenarios.support.Comparison.Finding;
import io.github.stigjohnny.shipping.scenarios.support.Comparison.FindingType;
import io.github.stigjohnny.shipping.scenarios.support.Observers;
import io.github.stigjohnny.shipping.scenarios.support.Recorder;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioRun;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * The negative fixtures of the scenario checks. Each runs altered code, never an edited record: an implementation with
 * a removed call, one with an added call along a permitted relationship, one with two interactions swapped, and runs
 * with the call observer or the publication observer switched off. The first three keep the outcome, so their outcome
 * assertions pass and only the comparison fails them; the two disabled observers must be coverage gaps, never missing
 * steps. The last fixture must pass: two runs of the quote scenario interleave on two threads, and each is compared on
 * its own. Their reports are written to build/reports/scenarios/fixtures/.
 */
class ScenarioFixturesTest {

    private static final long WAIT_SECONDS = 10;

    @Test
    void aRemovedCallThatKeepsTheOutcomeIsAMissingStep(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "removed-call", test, Observers.inProcess(),
                InProcessModules.Core.class, InProcessModules.InMemoryStore.class, TenantFixedAtWiring.class)) {
            QuoteResult result = quoteFor(run, ALPHA, "alpha");
            QuoteOutcomes.issuedFor(run, "Tenant Alpha's quote", result, ALPHA, BETA);

            ScenarioRun.Result finished = run.finish();

            assertThat(finished.outcomesHeld()).as("the outcome is the real code's").isTrue();
            assertThat(finished.verdict()).isEqualTo("DOES_NOT_CONFORM");
            assertThat(finished.blocking()).extracting(Finding::type, Finding::step)
                    .containsExactly(tuple(FindingType.MISSING_STEP, "resolve-tenant"));
            assertThat(finished.markdown()).content().contains("> This run does not conform to its declared steps: 1 "
                    + "build blockers, listed below. Known coverage gaps remain refused separately.").doesNotContain("conformed to their declarations");
        }
    }

    @Test
    void anAddedCallAlongAPermittedRelationshipIsAnUnexpectedInteraction(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioTenantDenial", "added-call", test, Observers.inProcess(),
                InProcessModules.Core.class, InProcessModules.InMemoryStore.class,
                InProcessModules.InMemoryShipments.class, OffersThatAskForTheTenant.class,
                InProcessModules.Shipments.class)) {
            TenantDenialScenarioTest.TenantDenial.runIn(run);

            ScenarioRun.Result finished = run.finish();

            assertThat(finished.outcomesHeld()).as("the outcome is the real code's").isTrue();
            assertThat(finished.verdict()).isEqualTo("DOES_NOT_CONFORM");
            assertThat(finished.blocking()).singleElement().satisfies(finding -> {
                assertThat(finding.type()).isEqualTo(FindingType.UNEXPECTED_INTERACTION);
                assertThat(finding.run()).isEqualTo("beta");
                assertThat(finding.message()).startsWith("MODULE_API_CALL quotes -> tenants "
                        + "io.github.stigjohnny.shipping.tenants.api.CurrentTenant#require").endsWith(
                        "is not a step of the scenario");
            });
        }
    }

    @Test
    void swappedInteractionsAreAnOrderViolation(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "swapped-order", test, Observers.inProcess(),
                InProcessModules.Core.class, InProcessModules.InMemoryStore.class, PublishesFirst.class)) {
            QuoteResult result = quoteFor(run, ALPHA, "alpha");
            QuoteOutcomes.issuedFor(run, "Tenant Alpha's quote", result, ALPHA, BETA);

            ScenarioRun.Result finished = run.finish();

            assertThat(finished.outcomesHeld()).as("the outcome is the real code's").isTrue();
            assertThat(finished.blocking()).extracting(Finding::type, Finding::step, Finding::message)
                    .containsExactly(tuple(FindingType.ORDER_VIOLATION, "publish-quote-issued", "step "
                            + "publish-quote-issued must come after step resolve-tenant, but seq 1 came before seq 2"));
        }
    }

    @Test
    void aDisabledCallObserverIsACoverageGapAndNotAMissingStep(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "disabled-call-observer", test,
                Observers.inProcess().ignoringApi(CurrentTenant.class), InProcessModules.Core.class,
                InProcessModules.InMemoryStore.class, InProcessModules.Quotes.class)) {
            QuoteResult result = quoteFor(run, ALPHA, "alpha");
            QuoteOutcomes.issuedFor(run, "Tenant Alpha's quote", result, ALPHA, BETA);

            ScenarioRun.Result finished = run.finish();

            assertThat(finished.outcomesHeld()).isTrue();
            assertThat(finished.blocking()).isNotEmpty().allSatisfy(finding ->
                    assertThat(finding.type()).isEqualTo(FindingType.COVERAGE_GAP));
            assertThat(finished.blocking()).extracting(Finding::step, Finding::message).contains(
                    tuple("resolve-tenant", "no observed bean implements "
                            + "io.github.stigjohnny.shipping.tenants.api.CurrentTenant in this run, so its calls "
                            + "cannot be seen"),
                    tuple("", "bypass: bean 'createQuote' -> currentTenant reaches "
                            + "io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext, an "
                            + "object of module tenants that no observer wraps"));
        }
    }

    @Test
    void aDisabledPublicationObserverIsACoverageGapAndNotAMissingStep(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "disabled-publication-observer", test,
                Observers.inProcess().withoutPublications(), InProcessModules.Core.class,
                InProcessModules.InMemoryStore.class, InProcessModules.Quotes.class)) {
            QuoteResult result = quoteFor(run, ALPHA, "alpha");
            QuoteOutcomes.issuedFor(run, "Tenant Alpha's quote", result, ALPHA, BETA);

            ScenarioRun.Result finished = run.finish();

            assertThat(finished.outcomesHeld()).isTrue();
            assertThat(finished.blocking()).extracting(Finding::type, Finding::step).containsExactly(
                    tuple(FindingType.COVERAGE_GAP, ""), tuple(FindingType.COVERAGE_GAP, "publish-quote-issued"));
            assertThat(finished.blocking().getFirst().message()).startsWith("publications are not observed: a probe "
                    + "event published through the application context did not reach the publication observer");
        }
    }

    @Test
    void twoRunsThatInterleaveOnTwoThreadsEachConform(TestInfo test) throws Exception {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "interleaved-runs", test, Observers.inProcess(),
                InProcessModules.Core.class, GatedStoreConfiguration.class, InProcessModules.Quotes.class);
                ExecutorService threads = Executors.newFixedThreadPool(2)) {
            GatedQuoteStore store = (GatedQuoteStore) run.bean(QuoteStore.class);

            Future<QuoteResult> alpha = threads.submit(() -> quoteFor(run, ALPHA, "alpha"));
            assertThat(store.awaitWaiting()).as("Alpha reached the gate").isTrue();
            Future<QuoteResult> beta = threads.submit(() -> quoteFor(run, BETA, "beta"));
            QuoteResult betas = beta.get(WAIT_SECONDS, TimeUnit.SECONDS);
            store.open();
            QuoteResult alphas = alpha.get(WAIT_SECONDS, TimeUnit.SECONDS);
            QuoteOutcomes.issuedFor(run, "Tenant Alpha's quote", alphas, ALPHA, BETA);
            QuoteOutcomes.issuedFor(run, "Tenant Beta's quote", betas, BETA, ALPHA);

            run.verify();
            Map<String, List<Integer>> seqs = run.finish().interactions().stream().collect(Collectors.groupingBy(
                    Recorder.Interaction::run, Collectors.mapping(Recorder.Interaction::seq, Collectors.toList())));
            assertThat(seqs).as("Beta's run happened inside Alpha's").containsEntry("alpha", List.of(1, 4))
                    .containsEntry("beta", List.of(2, 3));
        }
    }

    @Test
    void aRunWithoutAStimulusFailsEvenWhereNoStepIsEvaluated(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioTenantDenial", "no-stimulus", test,
                Observers.inProcess(), TenantDenialScenarioTest.TenantDenial.MODULES)) {
            ScenarioRun.Result finished = run.finish();

            assertThat(finished.verdict()).isEqualTo("DOES_NOT_CONFORM");
            assertThat(finished.blocking()).extracting(Finding::type, Finding::message).containsExactly(tuple(
                    FindingType.MISSING_STEP, "no stimulus of the scenario ran, so nothing could be observed"));
        }
    }

    @Test
    void aStepDeclaredAfterCommitIsRefusedNotPassedWhenItRunsBeforeAnyCommit(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixtureWithSidecar("ScenarioQuote", "after-commit-premature",
                Path.of("src/test/resources/scenario-fixtures/after-commit"), test, Observers.inProcess(),
                InProcessModules.Core.class, InProcessModules.InMemoryStore.class, InProcessModules.Quotes.class)) {
            QuoteResult result = quoteFor(run, ALPHA, "alpha");
            QuoteOutcomes.issuedFor(run, "Tenant Alpha's quote", result, ALPHA, BETA);

            ScenarioRun.Result finished = run.finish();

            assertThat(finished.outcomesHeld()).isTrue();
            assertThat(finished.verdict()).isEqualTo("DOES_NOT_CONFORM");
            assertThat(finished.blocking()).extracting(Finding::type, Finding::step, Finding::message)
                    .containsExactly(tuple(FindingType.COVERAGE_GAP, "publish-quote-issued", "step "
                            + "publish-quote-issued is declared AFTER_COMMIT, but no run observes commits yet, so when "
                            + "it happened cannot be checked: it is refused, not passed"));
        }
    }

    /** Tenant Alpha's or Beta's clerk asks for a quote, as in QuoteScenarioTest. */
    private static QuoteResult quoteFor(ScenarioRun run, TenantId tenant, String name) {
        ThreadTenantContext tenants = run.implementation(CurrentTenant.class, ThreadTenantContext.class);
        CreateQuote createQuote = run.bean(CreateQuote.class);
        return run.stimulus(name, "The clerk asks for a quote: the test binds the tenant and calls CreateQuote with "
                + "the contract's example request", () -> tenants.callAs(tenant, () -> createQuote.handle(EXAMPLE)));
    }

    /** The quotes module with CreateQuote replaced by one that never asks CurrentTenant. */
    @TestConfiguration(proxyBeanMethods = false)
    static class TenantFixedAtWiring {

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new ScenarioAlterations.TenantFixedAtWiring(clock, ids, currentTenant, events, store, ALPHA);
        }

        @Bean
        QuoteOffers quoteOffers(QuoteStore store) {
            return new InProcessModules.Quotes().quoteOffers(store);
        }
    }

    /** The quotes module with CreateQuote replaced by one that publishes before it resolves the tenant. */
    @TestConfiguration(proxyBeanMethods = false)
    static class PublishesFirst {

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new ScenarioAlterations.PublishesBeforeResolvingTheTenant(clock, ids, currentTenant, events, store,
                    ALPHA);
        }

        @Bean
        QuoteOffers quoteOffers(QuoteStore store) {
            return new InProcessModules.Quotes().quoteOffers(store);
        }
    }

    /** The quotes module with QuoteOffers replaced by one that also asks CurrentTenant. */
    @TestConfiguration(proxyBeanMethods = false)
    static class OffersThatAskForTheTenant {

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new InProcessModules.Quotes().createQuote(clock, ids, currentTenant, events, store);
        }

        @Bean
        QuoteOffers quoteOffers(CurrentTenant currentTenant, QuoteStore store) {
            return new ScenarioAlterations.OffersThatAskForTheCurrentTenant(currentTenant, store);
        }
    }

    /** The in-memory store behind a gate, to make two runs interleave. */
    @TestConfiguration(proxyBeanMethods = false)
    static class GatedStoreConfiguration {

        @Bean
        QuoteStore quoteStore() {
            return new GatedQuoteStore(ALPHA);
        }
    }
}
