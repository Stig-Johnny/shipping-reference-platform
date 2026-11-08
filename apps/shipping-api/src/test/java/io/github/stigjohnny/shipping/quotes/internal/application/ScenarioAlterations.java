package io.github.stigjohnny.shipping.quotes.internal.application;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.OfferLookup;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.QuoteIssued;
import io.github.stigjohnny.shipping.quotes.api.QuoteOffers;
import io.github.stigjohnny.shipping.quotes.internal.domain.IdGenerator;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteRequest;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteResult;
import io.github.stigjohnny.shipping.quotes.internal.domain.Rating;
import io.github.stigjohnny.shipping.quotes.internal.domain.SyntheticTariff;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.api.TenantBadge;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Altered implementations of the quotes module, for the negative fixtures of the scenario checks
 * (ScenarioFixturesTest). Each is the real class with one interaction changed, and keeps the real one's outcome for
 * the fixture's tenant, so that the outcome assertions still pass. The first three change what crosses the boundary;
 * the others reach the tenants module around the observers, which coverage verification must find. They live in the
 * quotes package because the recorder attributes a caller to a module by its package, and the census reads them as the
 * module's code.
 */
public final class ScenarioAlterations {

    private ScenarioAlterations() {
    }

    /** A quotes-owned root that shares its receiver or container with a tenant-owned root. */
    public record SharedReference(Object held) {
    }

    /** Work deliberately held until after finish and close; no thread or executor is retained in the object graph. */
    public abstract static class DeferredTenantQuote extends CreateQuote {

        private final CurrentTenant tenant;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile boolean virtual;
        private volatile long worker;

        protected DeferredTenantQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            super(clock, ids, currentTenant, events, store);
            this.tenant = currentTenant;
        }

        protected final void lookUpLater() {
            virtual = Thread.currentThread().isVirtual();
            worker = Thread.currentThread().threadId();
            started.countDown();
            try {
                release.await();
                tenant.require();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                // No tenant is bound on this worker; reaching the observer is the fixture's purpose.
            } finally {
                completed.countDown();
            }
        }

        public boolean awaitStarted() throws InterruptedException {
            return started.await(10, TimeUnit.SECONDS);
        }

        public boolean virtual() {
            return virtual;
        }

        public long worker() {
            return worker;
        }

        public boolean completed() {
            return completed.getCount() == 0;
        }

        public boolean releaseAndAwait() throws InterruptedException {
            release.countDown();
            return completed.await(10, TimeUnit.SECONDS);
        }
    }

    /** A virtual task whose scheduler is warmed before recording begins. */
    public static final class VirtualTenantQuote extends DeferredTenantQuote {

        public VirtualTenantQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            super(clock, ids, currentTenant, events, store);
        }

        @Override
        public QuoteResult handle(QuoteRequest request) {
            Thread.ofVirtual().start(this::lookUpLater);
            return super.handle(request);
        }
    }

    /** Dispatch to a pool initialized before recording; the task is released only after the run closes. */
    public static final class ExecutorTenantQuote extends DeferredTenantQuote {

        public ExecutorTenantQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            super(clock, ids, currentTenant, events, store);
        }

        @Override
        public QuoteResult handle(QuoteRequest request) {
            ForkJoinPool.commonPool().execute(this::lookUpLater);
            return super.handle(request);
        }
    }

    /**
     * CreateQuote without its call to CurrentTenant: it uses a tenant fixed when it is wired, which is the tenant the
     * fixture binds, so the quote, the store and the event are the same. A removed call.
     */
    public static final class TenantFixedAtWiring extends CreateQuote {

        private final Clock clock;
        private final IdGenerator ids;
        private final ApplicationEventPublisher events;
        private final QuoteStore store;
        private final Rating rating;
        private final TenantId tenant;

        public TenantFixedAtWiring(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store, TenantId tenant) {
            super(clock, ids, currentTenant, events, store);
            this.clock = clock;
            this.ids = ids;
            this.events = events;
            this.store = store;
            this.rating = new Rating(new SyntheticTariff(), ids);
            this.tenant = Objects.requireNonNull(tenant, "tenant");
        }

        @Override
        public QuoteResult handle(QuoteRequest request) {
            QuoteResult result = rating.quote(tenant, request, clock.instant());
            if (result instanceof QuoteResult.Offered(Quote quote)) {
                store.insert(quote);
                events.publishEvent(new QuoteIssued(ids.next(), tenant, quote.id(), quote.issuedAt()));
            }
            return result;
        }
    }

    /**
     * CreateQuote that rates, stores and publishes with a tenant fixed at wiring, and only then asks CurrentTenant and
     * fails if the two differ. For the fixture's tenant nothing differs but the order: the publication comes before
     * the tenant is resolved. Two interactions swapped.
     */
    public static final class PublishesBeforeResolvingTheTenant extends CreateQuote {

        private final CurrentTenant currentTenant;
        private final TenantFixedAtWiring publisher;
        private final TenantId tenant;

        public PublishesBeforeResolvingTheTenant(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store, TenantId tenant) {
            super(clock, ids, currentTenant, events, store);
            this.currentTenant = currentTenant;
            this.publisher = new TenantFixedAtWiring(clock, ids, currentTenant, events, store, tenant);
            this.tenant = tenant;
        }

        @Override
        public QuoteResult handle(QuoteRequest request) {
            QuoteResult result = publisher.handle(request);
            if (!currentTenant.require().equals(tenant)) {
                throw new IllegalStateException("the quote was issued for another tenant than the bound one");
            }
            return result;
        }
    }

    /**
     * QuoteOffers that also asks CurrentTenant for the current tenant before it looks the offer up with the tenant it is
     * given. quotes -> tenants is a REQUIRED relationship of the model, so the extra call is statically permitted, and
     * the answer is the real one's. An added call.
     */
    public static final class OffersThatAskForTheCurrentTenant implements QuoteOffers {

        private final CurrentTenant currentTenant;
        private final QuoteOffersService offers;

        public OffersThatAskForTheCurrentTenant(CurrentTenant currentTenant, QuoteStore store) {
            this.currentTenant = currentTenant;
            this.offers = new QuoteOffersService(store);
        }

        @Override
        public OfferLookup findOffer(TenantId tenant, QuoteId quote, OfferId offer, Instant at) {
            TenantId bound = currentTenant.require();
            return offers.findOffer(bound.equals(tenant) ? tenant : bound, quote, offer, at);
        }
    }

    /**
     * CreateQuote that also calls a Supplier holding the tenants module's implementation unwrapped, captured before
     * any proxy existed: the review's probe. The extra call never reaches an observer; the walk must find the
     * reference.
     */
    public static final class CapturedTenantQuote extends CreateQuote {

        private final Supplier<TenantId> captured;

        public CapturedTenantQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store, CurrentTenant unwrapped) {
            super(clock, ids, currentTenant, events, store);
            this.captured = unwrapped::require;
        }

        @Override
        public QuoteResult handle(QuoteRequest request) {
            Objects.requireNonNull(captured.get(), "tenant");
            return super.handle(request);
        }
    }

    /** A holder that keeps a reference one level down. */
    public record TenantHolder(CurrentTenant tenant) {
    }

    /** CreateQuote that also calls the tenants module through a reference nested in a holder. */
    public static final class NestedTenantQuote extends CreateQuote {

        private final TenantHolder holder;

        public NestedTenantQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store, TenantHolder holder) {
            super(clock, ids, currentTenant, events, store);
            this.holder = holder;
        }

        @Override
        public QuoteResult handle(QuoteRequest request) {
            holder.tenant().require();
            return super.handle(request);
        }
    }

    /** CreateQuote that also calls the tenants module through a reference kept in a static field. */
    public static final class StaticTenantQuote extends CreateQuote {

        private static volatile @Nullable CurrentTenant held;

        public StaticTenantQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            super(clock, ids, currentTenant, events, store);
        }

        /** Keeps the reference that handle uses; null forgets it. */
        public static void hold(@Nullable CurrentTenant tenant) {
            held = tenant;
        }

        @Override
        public QuoteResult handle(QuoteRequest request) {
            Objects.requireNonNull(held, "held").require();
            return super.handle(request);
        }
    }

    /**
     * CreateQuote that starts a thread which asks the tenants module for the current tenant once it is released, after
     * the scenario's recording has ended: asynchronous work.
     */
    public static final class LateTenantQuote extends CreateQuote {

        /** The name of the thread it starts. */
        public static final String THREAD = "late-tenant-lookup";

        private final CurrentTenant tenant;
        private final CountDownLatch release = new CountDownLatch(1);

        public LateTenantQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            super(clock, ids, currentTenant, events, store);
            this.tenant = currentTenant;
        }

        /** Lets the thread make its call. */
        public void release() {
            release.countDown();
        }

        @Override
        public QuoteResult handle(QuoteRequest request) {
            Thread.ofPlatform().name(THREAD).start(this::lookUpLater);
            return super.handle(request);
        }

        private void lookUpLater() {
            try {
                if (release.await(10, TimeUnit.SECONDS)) {
                    tenant.require();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                // No tenant is bound on this thread; the call has been made, which is what the fixture needs.
            }
        }
    }

    /**
     * QuoteOffers that binds the tenant itself through the tenants module's internal class, which it creates: a test
     * implementation whose own use of another module no proxy can intercept. The census reads it.
     */
    public static final class OffersThatBindTheTenantThemselves implements QuoteOffers {

        private final QuoteOffersService offers;

        public OffersThatBindTheTenantThemselves(QuoteStore store) {
            this.offers = new QuoteOffersService(store);
        }

        @Override
        public OfferLookup findOffer(TenantId tenant, QuoteId quote, OfferId offer, Instant at) {
            return new ThreadTenantContext().callAs(tenant, () -> offers.findOffer(tenant, quote, offer, at));
        }
    }

    /** CreateQuote that also calls behaviour on a value of the tenants module's api: not a value use. */
    public static final class BadgeQuote extends CreateQuote {

        public BadgeQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant, ApplicationEventPublisher events,
                QuoteStore store) {
            super(clock, ids, currentTenant, events, store);
        }

        @Override
        public QuoteResult handle(QuoteRequest request) {
            QuoteResult result = super.handle(request);
            if (result instanceof QuoteResult.Offered(Quote quote)) {
                Objects.requireNonNull(new TenantBadge(quote.tenantId()).describe(), "badge");
            }
            return result;
        }
    }
    /** A new Spring access is still refused, even if its owner already occurs in the committed ledger. */
    public static final class UnledgeredSpringAccess {
        public org.springframework.http.ResponseEntity.BodyBuilder reply() {
            return org.springframework.http.ResponseEntity.status(204);
        }
    }

}
