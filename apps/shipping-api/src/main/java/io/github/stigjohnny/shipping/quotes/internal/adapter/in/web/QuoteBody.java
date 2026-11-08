package io.github.stigjohnny.shipping.quotes.internal.adapter.in.web;

import io.github.stigjohnny.shipping.quotes.api.Money;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.quotes.internal.adapter.in.web.QuoteRequestBody.ParcelBody;
import io.github.stigjohnny.shipping.quotes.internal.adapter.in.web.QuoteRequestBody.PlaceBody;
import io.github.stigjohnny.shipping.quotes.internal.domain.Offer;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteStatus;
import io.github.stigjohnny.shipping.quotes.internal.domain.Zone;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * The contract's Quote: the status is computed for the instant it is read, timestamps are UTC with exactly three
 * fraction digits, and amounts are decimal strings with exactly two.
 */
record QuoteBody(UUID quoteId, QuoteStatus status, String issuedAt, String expiresAt, ParcelBody parcel,
        PlaceBody origin, PlaceBody destination, Zone zone, int billableWeightGrams, List<OfferBody> offers) {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    /** The quote as it is at {@code now}: OPEN before its expiry, EXPIRED from it on. */
    static QuoteBody of(Quote quote, Instant now) {
        return new QuoteBody(quote.id().value(), quote.status(now), TIMESTAMP.format(quote.issuedAt()),
                TIMESTAMP.format(quote.expiresAt()), ParcelBody.of(quote.request().parcel()),
                PlaceBody.of(quote.request().origin()), PlaceBody.of(quote.request().destination()), quote.zone(),
                quote.billableWeightGrams(), quote.offers().stream().map(OfferBody::of).toList());
    }

    /** The contract's Offer. */
    record OfferBody(UUID offerId, ServiceLevel serviceLevel, MoneyBody price, TransitDaysBody transitDays) {

        static OfferBody of(Offer offer) {
            return new OfferBody(offer.id().value(), offer.level(), MoneyBody.of(offer.price()),
                    new TransitDaysBody(offer.transitDays().minDays(), offer.transitDays().maxDays()));
        }
    }

    /** The contract's Money: the amount as a decimal string with the currency's fraction digits, here two. */
    record MoneyBody(String amount, String currency) {

        static MoneyBody of(Money money) {
            return new MoneyBody(money.amount().toPlainString(), money.currency().getCurrencyCode());
        }
    }

    /** The contract's TransitDays. */
    record TransitDaysBody(int minDays, int maxDays) {
    }
}
