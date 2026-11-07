package io.github.stigjohnny.shipping.quotes.internal.domain;

import io.github.stigjohnny.shipping.quotes.api.Money;
import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Optional;
import java.util.Set;

/**
 * The carrier's synthetic tariff (docs/modules/quotes.md, "The synthetic tariff"): fictional values, fixed in code,
 * and pure functions of them, with no clock and no I/O. Changing any value is a level 3 change that updates the
 * specification's table, its golden prices and SyntheticTariffTest in the same pull request. The weight bands' upper
 * bounds are in {@link WeightBand}.
 */
public final class SyntheticTariff {

    /** Prices are in euros, rounded to cents. */
    public static final Currency EUR = Currency.getInstance("EUR");
    /** A route is served when both of its countries are one of these six. */
    public static final Set<String> SERVED_COUNTRIES = Set.of("DE", "DK", "FI", "NL", "NO", "SE");
    /** EXPRESS is available up to this billable weight; ECONOMY and STANDARD up to the last band's upper bound. */
    public static final int EXPRESS_LIMIT_GRAMS = 10_000;
    /** The fuel surcharge of 7.5 % on every offer, as a factor. */
    private static final BigDecimal FUEL_SURCHARGE = new BigDecimal("1.075");
    private static final int CENTS = 2;

    public Zone zone(Place origin, Place destination) {
        return origin.countryCode().equals(destination.countryCode()) ? Zone.DOMESTIC : Zone.INTERNATIONAL;
    }

    /** Whether the level can be offered for a parcel of this billable weight. */
    public boolean available(ServiceLevel level, int billableWeightGrams) {
        int limit = level == ServiceLevel.EXPRESS ? EXPRESS_LIMIT_GRAMS : WeightBand.BAND_6.upToGrams();
        return billableWeightGrams >= 1 && billableWeightGrams <= limit;
    }

    /**
     * Why nothing can be offered for the request, or empty when something can. The rules are checked in this order,
     * and the first that fails decides: both countries served, the billable weight within 30000 g, and the requested
     * level, if any, available for the parcel.
     */
    public Optional<UnavailableReason> unavailability(QuoteRequest request) {
        if (!SERVED_COUNTRIES.contains(request.origin().countryCode())
                || !SERVED_COUNTRIES.contains(request.destination().countryCode())) {
            return Optional.of(UnavailableReason.ROUTE_NOT_SERVED);
        }
        int billable = request.parcel().billableWeightGrams();
        if (WeightBand.of(billable).isEmpty()) {
            return Optional.of(UnavailableReason.BILLABLE_WEIGHT_ABOVE_LIMIT);
        }
        ServiceLevel requested = request.serviceLevel();
        if (requested != null && !available(requested, billable)) {
            return Optional.of(UnavailableReason.SERVICE_LEVEL_NOT_AVAILABLE);
        }
        return Optional.empty();
    }

    /**
     * base x level factor x fuel surcharge, computed exactly and then rounded half-up to cents: the only rounding of a
     * price. Whether the level is available for the band is {@link #available}'s question, not this one's.
     */
    public Money price(Zone zone, WeightBand band, ServiceLevel level) {
        BigDecimal exact = basePrice(zone, band).multiply(levelFactor(level)).multiply(FUEL_SURCHARGE);
        return new Money(exact.setScale(CENTS, RoundingMode.HALF_UP), EUR);
    }

    /** The simulated transit days of a level in a zone. */
    public TransitDays transitDays(Zone zone, ServiceLevel level) {
        boolean domestic = zone == Zone.DOMESTIC;
        return switch (level) {
            case ECONOMY -> domestic ? new TransitDays(3, 5) : new TransitDays(5, 8);
            case STANDARD -> domestic ? new TransitDays(1, 3) : new TransitDays(3, 5);
            case EXPRESS -> domestic ? new TransitDays(1, 1) : new TransitDays(1, 2);
        };
    }

    /** The base price in EUR of a band in a zone. */
    private static BigDecimal basePrice(Zone zone, WeightBand band) {
        boolean domestic = zone == Zone.DOMESTIC;
        String euros = switch (band) {
            case BAND_1 -> domestic ? "6.90" : "14.00";
            case BAND_2 -> domestic ? "8.50" : "17.50";
            case BAND_3 -> domestic ? "11.90" : "24.00";
            case BAND_4 -> domestic ? "15.50" : "32.00";
            case BAND_5 -> domestic ? "21.00" : "45.00";
            case BAND_6 -> domestic ? "27.50" : "58.00";
        };
        return new BigDecimal(euros);
    }

    private static BigDecimal levelFactor(ServiceLevel level) {
        return switch (level) {
            case ECONOMY -> new BigDecimal("0.80");
            case STANDARD -> new BigDecimal("1.00");
            case EXPRESS -> new BigDecimal("1.50");
        };
    }
}
