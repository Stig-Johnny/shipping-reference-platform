package io.github.stigjohnny.shipping.scenarios;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.EXAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.stigjohnny.shipping.quotes.api.QuoteOffers;
import io.github.stigjohnny.shipping.quotes.internal.application.CreateQuote;
import io.github.stigjohnny.shipping.quotes.internal.application.QuoteStore;
import io.github.stigjohnny.shipping.quotes.internal.application.ScenarioAlterations;
import io.github.stigjohnny.shipping.quotes.internal.domain.IdGenerator;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteResult;
import io.github.stigjohnny.shipping.scenarios.support.Comparison.Finding;
import io.github.stigjohnny.shipping.scenarios.support.Comparison.FindingType;
import io.github.stigjohnny.shipping.scenarios.support.Observers;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioRun;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * Negative fixtures for coverage verification: each reaches the tenants module around the observers, so that a call
 * escapes the recorder, and each must fail with a coverage gap, never pass, while its outcome holds. The first is the
 * review's probe: a Supplier that captured the implementation before any proxy existed. The others keep the reference
 * nested in a holder, in a static field, in a singleton registered as an object, call after the recording ends from a
 * thread the run started, or create another module's internal class in a test implementation. Their reports are in
 * build/reports/scenarios/fixtures/.
 */
class CoverageBypassTest {

    private static final String TENANTS_IMPLEMENTATION =
            "io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext";

    @Test
    void aNewSpringAccessFailsEvenWhenItsOwnerAndMemberAreAlreadyLedgered(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "unledgered-spring", test, Observers.inProcess(),
                InProcessModules.Core.class, InProcessModules.InMemoryStore.class, InProcessModules.Quotes.class,
                ExtraSpring.class)) {
            List<Finding> blocking = quoteAndFinish(run);
            assertThat(blocking).singleElement().satisfies(f -> assertThat(f.message()).contains(
                    "ScenarioAlterations$UnledgeredSpringAccess.reply()", "ResponseEntity#status(I)"));
            assertThat(run.result().failing()).hasSize(107);
            assertThat(catchThrowable(run::verify)).isInstanceOf(AssertionError.class)
                    .hasMessageContaining("ResponseEntity#status(I)");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExtraSpring {
        @Bean
        ScenarioAlterations.UnledgeredSpringAccess extraSpringAccess() {
            return new ScenarioAlterations.UnledgeredSpringAccess();
        }
    }

    @Test
    void virtualWorkReleasedAfterCloseIsAlreadyRefused(TestInfo test) throws InterruptedException {
        Thread.ofVirtual().start(() -> { }).join(); // Initialize the scheduler before the recorder snapshots threads.
        assertDeferredDispatchRefused(test, Virtual.class, true, "java.lang.Thread#ofVirtual");
    }

    @Test
    void preExistingExecutorWorkReleasedAfterCloseIsAlreadyRefused(TestInfo test) throws InterruptedException {
        CountDownLatch warm = new CountDownLatch(1);
        ForkJoinPool.commonPool().execute(warm::countDown);
        assertThat(warm.await(10, TimeUnit.SECONDS)).isTrue();
        assertDeferredDispatchRefused(test, ExistingExecutor.class, false, "java.util.concurrent.ForkJoinPool#execute");
    }

    private static void assertDeferredDispatchRefused(TestInfo test, Class<?> configuration, boolean virtual,
            String dispatch) throws InterruptedException {
        Set<Long> existingThreads = Thread.getAllStackTraces().keySet().stream().map(Thread::threadId)
                .collect(Collectors.toSet());
        ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", virtual ? "bypass-virtual" : "bypass-existing-executor",
                test, Observers.inProcess(), InProcessModules.Core.class, configuration,
                InProcessModules.InMemoryStore.class);
        ScenarioAlterations.DeferredTenantQuote quote = (ScenarioAlterations.DeferredTenantQuote)
                run.bean(CreateQuote.class);
        try {
            ThreadTenantContext tenants = run.implementation(CurrentTenant.class, ThreadTenantContext.class);
            QuoteResult result = run.stimulus("alpha", "Tenant Alpha's clerk asks for a quote", () ->
                    tenants.callAs(ALPHA, () -> quote.handle(EXAMPLE)));
            QuoteOutcomes.issuedFor(run, "Tenant Alpha's quote", result, ALPHA, BETA);
            assertThat(quote.awaitStarted()).isTrue();
            assertThat(quote.virtual()).isEqualTo(virtual);
            if (!virtual) {
                assertThat(existingThreads).as("the executor worker predates recording").contains(quote.worker());
            }
            ScenarioRun.Result finished = run.finish();
            run.close(); // No late interaction exists yet: refusal must not depend on close's one-time snapshot.

            assertThat(quote.completed()).isFalse();
            assertThat(finished.outcomes()).hasSize(3);
            assertThat(finished.outcomesHeld()).isTrue();
            assertThat(finished.blocking()).anySatisfy(finding -> {
                assertThat(finding.type()).isEqualTo(FindingType.COVERAGE_GAP);
                assertThat(finding.message()).contains(dispatch, "asynchronous dispatch is unsupported");
            });
            assertThat(finished.verdict()).isEqualTo("DOES_NOT_CONFORM");
        } finally {
            run.close();
            assertThat(quote.releaseAndAwait()).as("the fixture releases and finishes its task after close").isTrue();
        }
    }

    @Test
    void aSupplierThatCapturedTheImplementationBeforeItsProxyIsAGap(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "bypass-captured-supplier", test,
                Observers.inProcess(), Captured.class, InProcessModules.InMemoryStore.class)) {
            List<Finding> failing = quoteAndFinish(run);

            assertThat(failing).extracting(Finding::message).contains("bypass: bean 'createQuote' -> captured -> "
                    + "captured arg$1 reaches " + TENANTS_IMPLEMENTATION + ", an object of module tenants that no "
                    + "observer wraps");
        }
    }

    @Test
    void aReferenceNestedInAHolderIsAGap(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "bypass-nested", test, Observers.inProcess(),
                Nested.class, InProcessModules.InMemoryStore.class)) {
            List<Finding> failing = quoteAndFinish(run);

            assertThat(failing).extracting(Finding::message).contains("bypass: bean 'createQuote' -> holder -> tenant "
                    + "reaches " + TENANTS_IMPLEMENTATION + ", an object of module tenants that no observer wraps");
        }
    }

    @Test
    void aReferenceInAStaticFieldIsAGap(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "bypass-static-field", test,
                Observers.inProcess(), Static.class, InProcessModules.InMemoryStore.class)) {
            List<Finding> failing = quoteAndFinish(run);

            assertThat(failing).extracting(Finding::message).contains("bypass: io.github.stigjohnny.shipping.quotes"
                    + ".internal.application.ScenarioAlterations$StaticTenantQuote.held reaches "
                    + TENANTS_IMPLEMENTATION + ", an object of module tenants that no observer wraps");
        } finally {
            ScenarioAlterations.StaticTenantQuote.hold(null);
        }
    }

    @Test
    void aSingletonRegisteredAsAnObjectIsAGap(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "bypass-registered-singleton", test,
                Observers.inProcess(), Singleton.class, InProcessModules.InMemoryStore.class)) {
            List<Finding> failing = quoteAndFinish(run);

            assertThat(failing).extracting(Finding::message).contains("singleton 'currentTenant' was registered as an "
                    + "object, so no post-processor, and no observer, saw it", "bypass: bean 'createQuote' -> "
                    + "currentTenant reaches " + TENANTS_IMPLEMENTATION + ", an object of module tenants that no "
                    + "observer wraps");
        }
    }

    @Test
    void workThatOutlivesTheRecordingIsAGapAndClosingFails(TestInfo test) throws InterruptedException {
        ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "bypass-after-the-recording", test,
                Observers.inProcess(), InProcessModules.Core.class, Late.class, InProcessModules.InMemoryStore.class);
        Throwable closing;
        try {
            List<Finding> failing = quoteAndFinish(run);
            assertThat(failing).extracting(Finding::message).contains("thread '"
                    + ScenarioAlterations.LateTenantQuote.THREAD + "' started during the run and is still running: work "
                    + "it does after the recording ends would not be seen");

            ((ScenarioAlterations.LateTenantQuote) run.bean(CreateQuote.class)).release();
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread.getName().equals(ScenarioAlterations.LateTenantQuote.THREAD)) {
                    thread.join(10_000);
                }
            }
        } finally {
            closing = catchThrowable(run::close);
        }

        assertThat(closing).isInstanceOf(AssertionError.class).hasMessageContaining("1 interactions happened after "
                + "the recording ended");
        assertThat(run.result().failing()).extracting(Finding::message).contains("after the recording ended, "
                + "io.github.stigjohnny.shipping.tenants.api.CurrentTenant#require on thread '"
                + ScenarioAlterations.LateTenantQuote.THREAD + "' ran: asynchronous work is not observed, so this "
                + "run's comparison is incomplete");
    }

    @Test
    void aTestImplementationThatCreatesAnotherModulesInternalClassIsAGap(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioTenantDenial", "bypass-test-implementation", test,
                Observers.inProcess(), InProcessModules.Core.class, InProcessModules.InMemoryStore.class,
                InProcessModules.InMemoryShipments.class, OffersThatBindTheTenant.class,
                InProcessModules.Shipments.class)) {
            TenantDenialScenarioTest.TenantDenial.runIn(run);

            ScenarioRun.Result finished = run.finish();

            assertThat(finished.outcomesHeld()).isTrue();
            assertThat(finished.verdict()).isEqualTo("DOES_NOT_CONFORM");
            assertThat(finished.blocking()).isNotEmpty().allSatisfy(f -> assertThat(f.type())
                    .isEqualTo(FindingType.COVERAGE_GAP));
            assertThat(finished.blocking()).extracting(Finding::message).anySatisfy(message -> assertThat(message)
                    .contains("ScenarioAlterations$OffersThatBindTheTenantThemselves.findOffer(")
                    .endsWith("uses " + TENANTS_IMPLEMENTATION + "#<init>, which no proxy can intercept"));
        }
    }

    @Test
    void behaviourOnAnotherModulesValueIsAGapWhileItsConstructorIsAValueUse(TestInfo test) {
        try (ScenarioRun run = ScenarioRun.fixture("ScenarioQuote", "bypass-value-behaviour", test,
                Observers.inProcess(), InProcessModules.Core.class, Badge.class,
                InProcessModules.InMemoryStore.class)) {
            ThreadTenantContext tenants = run.implementation(CurrentTenant.class, ThreadTenantContext.class);
            CreateQuote createQuote = run.bean(CreateQuote.class);
            QuoteResult result = run.stimulus("alpha", "Tenant Alpha's clerk asks for a quote", () -> tenants.callAs(ALPHA,
                    () -> createQuote.handle(EXAMPLE)));
            QuoteOutcomes.issuedFor(run, "Tenant Alpha's quote", result, ALPHA, BETA);

            ScenarioRun.Result finished = run.finish();

            assertThat(finished.outcomes()).hasSize(3);
            assertThat(finished.outcomesHeld()).isTrue();
            assertThat(finished.verdict()).isEqualTo("DOES_NOT_CONFORM");
            assertThat(finished.blocking()).singleElement().satisfies(finding -> {
                assertThat(finding.type()).isEqualTo(FindingType.COVERAGE_GAP);
                assertThat(finding.message()).startsWith("call site io.github.stigjohnny.shipping.quotes.internal"
                        + ".application.ScenarioAlterations$BadgeQuote.handle(").endsWith("uses io.github.stigjohnny"
                        + ".shipping.tenants.api.TenantBadge#describe, which no proxy can intercept");
            });
        }
    }

    /** Tenant Alpha's clerk asks for a quote; the outcome holds, and every failing finding is a coverage gap. */
    private static List<Finding> quoteAndFinish(ScenarioRun run) {
        ThreadTenantContext tenants = run.implementation(CurrentTenant.class, ThreadTenantContext.class);
        CreateQuote createQuote = run.bean(CreateQuote.class);
        QuoteResult result = run.stimulus("alpha", "Tenant Alpha's clerk asks for a quote", () -> tenants.callAs(ALPHA,
                () -> createQuote.handle(EXAMPLE)));
        QuoteOutcomes.issuedFor(run, "Tenant Alpha's quote", result, ALPHA, BETA);

        ScenarioRun.Result finished = run.finish();

        assertThat(finished.outcomesHeld()).as("the outcome is the real code's").isTrue();
        assertThat(finished.verdict()).isEqualTo("DOES_NOT_CONFORM");
        assertThat(finished.blocking()).isNotEmpty().allSatisfy(f -> assertThat(f.type())
                .isEqualTo(FindingType.COVERAGE_GAP));
        return finished.blocking();
    }

    /** The clock and identifiers of the fixtures that wire the tenants module themselves. */
    abstract static class ClockAndIds {

        @Bean
        Clock clock() {
            return Clock.fixed(InProcessModules.NOW, ZoneOffset.UTC);
        }

        @Bean
        IdGenerator ids() {
            return QuoteFixtures.sequentialIds();
        }
    }

    /** CurrentTenant's implementation is the bean, which the observer wraps, and CreateQuote also captures it raw. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Captured extends ClockAndIds {

        private final ThreadTenantContext unwrapped = new ThreadTenantContext();

        @Bean
        CurrentTenant currentTenant() {
            return unwrapped;
        }

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new ScenarioAlterations.CapturedTenantQuote(clock, ids, currentTenant, events, store, unwrapped);
        }
    }

    /** As Captured, with the raw reference in a holder. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Nested extends ClockAndIds {

        private final ThreadTenantContext unwrapped = new ThreadTenantContext();

        @Bean
        CurrentTenant currentTenant() {
            return unwrapped;
        }

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new ScenarioAlterations.NestedTenantQuote(clock, ids, currentTenant, events, store,
                    new ScenarioAlterations.TenantHolder(unwrapped));
        }
    }

    /** As Captured, with the raw reference in a static field. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Static extends ClockAndIds {

        private final ThreadTenantContext unwrapped = new ThreadTenantContext();

        @Bean
        CurrentTenant currentTenant() {
            return unwrapped;
        }

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            ScenarioAlterations.StaticTenantQuote.hold(unwrapped);
            return new ScenarioAlterations.StaticTenantQuote(clock, ids, currentTenant, events, store);
        }
    }

    /** CurrentTenant's implementation registered as an object, which no post-processor sees. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Singleton extends ClockAndIds {

        @Bean
        static BeanFactoryPostProcessor unwrappedTenant() {
            return factory -> factory.registerSingleton("currentTenant", new ThreadTenantContext());
        }

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new CreateQuote(clock, ids, currentTenant, events, store);
        }
    }

    /** CreateQuote that calls behaviour on a value of the tenants module. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Badge {

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new ScenarioAlterations.BadgeQuote(clock, ids, currentTenant, events, store);
        }
    }

    /** CreateQuote that leaves a thread behind. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Late {

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new ScenarioAlterations.LateTenantQuote(clock, ids, currentTenant, events, store);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Virtual {

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new ScenarioAlterations.VirtualTenantQuote(clock, ids, currentTenant, events, store);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExistingExecutor {

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new ScenarioAlterations.ExecutorTenantQuote(clock, ids, currentTenant, events, store);
        }
    }

    /** The quotes module with QuoteOffers replaced by one that creates the tenants module's internal class. */
    @TestConfiguration(proxyBeanMethods = false)
    static class OffersThatBindTheTenant {

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new CreateQuote(clock, ids, currentTenant, events, store);
        }

        @Bean
        QuoteOffers quoteOffers(QuoteStore store) {
            return new ScenarioAlterations.OffersThatBindTheTenantThemselves(store);
        }
    }
}
