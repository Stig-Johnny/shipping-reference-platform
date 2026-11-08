package io.github.stigjohnny.shipping.quotes.internal.application;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.exampleQuote;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.api.MissingTenantContextException;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reading a quote is scoped to the current tenant: the tenant's own quote is found, and another tenant's quote is not
 * found, exactly like an unknown ID. The store is in memory; the same read from the database is for
 * QuotesModuleIntegrationTest, which is planned.
 */
class GetQuoteTest {

    private final Quote alphasQuote = exampleQuote(ALPHA, Instant.parse("2026-11-10T09:00:00.000Z"));
    private final InMemoryQuoteStore store = new InMemoryQuoteStore();
    /** The tenant the HTTP filter binds for a request; none until a test binds one. */
    private final List<TenantId> bound = new ArrayList<>();
    private final CurrentTenant currentTenant = () -> bound.stream().findFirst()
            .orElseThrow(MissingTenantContextException::new);
    private final GetQuote getQuote = new GetQuote(currentTenant, store);

    GetQuoteTest() {
        store.insert(alphasQuote);
    }

    @Test
    void theCurrentTenantReadsItsOwnQuote() {
        bound.add(ALPHA);

        assertThat(getQuote.handle(alphasQuote.id())).contains(alphasQuote);
    }

    @Test
    void anotherTenantsQuoteIsNotFoundLikeAnUnknownOne() {
        bound.add(BETA);

        assertThat(getQuote.handle(alphasQuote.id())).isEmpty();
        assertThat(getQuote.handle(new QuoteId(id(99)))).isEmpty();
    }

    @Test
    void withoutABoundTenantNothingIsRead() {
        assertThatThrownBy(() -> getQuote.handle(alphasQuote.id())).isInstanceOf(MissingTenantContextException.class);
    }
}
