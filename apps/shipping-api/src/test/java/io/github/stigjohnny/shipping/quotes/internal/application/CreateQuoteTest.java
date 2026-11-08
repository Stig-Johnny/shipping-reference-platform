package io.github.stigjohnny.shipping.quotes.internal.application;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.EXAMPLE;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.id;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.offered;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.sequentialIds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.QuoteIssued;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteRequest;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteResult;
import io.github.stigjohnny.shipping.quotes.internal.domain.UnavailableReason;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.api.MissingTenantContextException;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Creating a quote (invariant Q8): the tenant comes from CurrentTenant, and only an offered quote is stored and
 * publishes QuoteIssued. The store is in memory and the publisher records what it gets; that the publication happens
 * inside the database transaction is for QuotesModuleIntegrationTest, which is planned.
 */
class CreateQuoteTest {

    private static final Instant NOW = Instant.parse("2026-11-10T09:00:00.000Z");

    private final InMemoryQuoteStore store = new InMemoryQuoteStore();
    private final List<Object> published = new ArrayList<>();
    /** The tenant the HTTP filter is to bind for a request; none until a test binds one. */
    private final List<TenantId> bound = new ArrayList<>();
    private final CurrentTenant currentTenant = () -> bound.stream().findFirst()
            .orElseThrow(MissingTenantContextException::new);
    private final CreateQuote createQuote = new CreateQuote(Clock.fixed(NOW, ZoneOffset.UTC), sequentialIds(),
            currentTenant, published::add, store);

    @Test
    void anOfferedQuoteIsStoredForTheCurrentTenantAndPublishesQuoteIssued() {
        bound.add(ALPHA);

        Quote quote = offered(createQuote.handle(EXAMPLE));

        assertThat(quote.tenantId()).isEqualTo(ALPHA);
        assertThat(quote.issuedAt()).isEqualTo(NOW);
        assertThat(store.all()).containsExactly(quote);
        // The rating took id(1) for the quote and id(2) to id(4) for its offers; the event takes the next one.
        assertThat(published).containsExactly(new QuoteIssued(id(5), ALPHA, quote.id(), NOW));
    }

    @Test
    void theQuoteBelongsToTheTenantBoundForTheRequest() {
        bound.add(BETA);

        Quote quote = offered(createQuote.handle(EXAMPLE));

        assertThat(store.find(BETA, quote.id())).contains(quote);
        assertThat(store.find(ALPHA, quote.id())).isEmpty();
        assertThat(published).singleElement().isInstanceOfSatisfying(QuoteIssued.class,
                issued -> assertThat(issued.tenantId()).isEqualTo(BETA));
    }

    @Test
    void anUnavailableResultIsNeitherStoredNorPublished() {
        bound.add(ALPHA);
        QuoteRequest unserved = new QuoteRequest(EXAMPLE.parcel(), new Place("GB", "0150"), EXAMPLE.destination(),
                null);

        QuoteResult result = createQuote.handle(unserved);

        assertThat(result).isEqualTo(new QuoteResult.Unavailable(UnavailableReason.ROUTE_NOT_SERVED));
        assertThat(store.all()).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void withoutABoundTenantNothingIsStoredOrPublished() {
        assertThatThrownBy(() -> createQuote.handle(EXAMPLE)).isInstanceOf(MissingTenantContextException.class);

        assertThat(store.all()).isEmpty();
        assertThat(published).isEmpty();
    }
}
