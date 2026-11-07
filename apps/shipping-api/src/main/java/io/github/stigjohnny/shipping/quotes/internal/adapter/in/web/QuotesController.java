package io.github.stigjohnny.shipping.quotes.internal.adapter.in.web;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.internal.application.CreateQuote;
import io.github.stigjohnny.shipping.quotes.internal.application.GetQuote;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteRequest;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteResult;
import io.github.stigjohnny.shipping.quotes.internal.domain.UnavailableReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * createQuote and getQuote (contracts/shipping.openapi.yaml, tag quotes). Business outcomes come from the use cases'
 * results, not from exceptions: an offered quote is {@code 201} with its {@code Location}, nothing offerable is
 * {@code 422 no-offer-available}, and a quote that the current tenant does not have, unknown or another tenant's, is
 * {@code 404 quote-not-found}. The tenant is never read from the request: the use cases take it from CurrentTenant.
 */
@RestController
@RequestMapping("/api/v1/quotes")
class QuotesController {

    private static final String LOWER_CASE_UUID = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    private final CreateQuote createQuote;
    private final GetQuote getQuote;
    private final Clock clock;

    QuotesController(CreateQuote createQuote, GetQuote getQuote, Clock clock) {
        this.createQuote = Objects.requireNonNull(createQuote, "createQuote");
        this.getQuote = Objects.requireNonNull(getQuote, "getQuote");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> createQuote(@Valid @RequestBody QuoteRequestBody body) {
        QuoteRequest request = body.toDomain();
        return switch (createQuote.handle(request)) {
            case QuoteResult.Offered(Quote quote) -> ResponseEntity.created(URI.create("/api/v1/quotes/"
                    + quote.id().value())).<Object>body(QuoteBody.of(quote, clock.instant()));
            case QuoteResult.Unavailable(UnavailableReason reason) -> QuoteProblems.noOfferAvailable(reason, request);
        };
    }

    @GetMapping(path = "/{quoteId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getQuote(
            @PathVariable @Pattern(regexp = LOWER_CASE_UUID, message = "must be a lowercase UUID") String quoteId) {
        return getQuote.handle(new QuoteId(UUID.fromString(quoteId)))
                .map(quote -> ResponseEntity.ok().<Object>body(QuoteBody.of(quote, clock.instant())))
                .orElseGet(QuoteProblems::quoteNotFound);
    }
}
