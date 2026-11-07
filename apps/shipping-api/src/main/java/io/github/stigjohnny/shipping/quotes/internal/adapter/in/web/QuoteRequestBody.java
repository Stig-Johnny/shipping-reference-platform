package io.github.stigjohnny.shipping.quotes.internal.adapter.in.web;

import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.quotes.internal.domain.Parcel;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The contract's QuoteRequest, read from untrusted JSON. Jackson rejects unknown members, wrong types and enum values
 * that are no level's name; a member can still be missing or out of bounds, which Bean Validation rejects, with the
 * contract's bounds, before {@link #toDomain()} builds the domain's values. The messages are fixed English text, so a
 * client's Accept-Language does not change them.
 */
record QuoteRequestBody(
        @NotNull(message = Messages.REQUIRED) @Valid ParcelBody parcel,
        @NotNull(message = Messages.REQUIRED) @Valid PlaceBody origin,
        @NotNull(message = Messages.REQUIRED) @Valid PlaceBody destination,
        @Nullable ServiceLevel serviceLevel) {

    QuoteRequest toDomain() {
        return new QuoteRequest(parcel.toDomain(), origin.toDomain(), destination.toDomain(), serviceLevel);
    }

    /** The contract's Parcel; the response repeats it. */
    record ParcelBody(
            @NotNull(message = Messages.REQUIRED)
            @Min(value = 1, message = Messages.WEIGHT) @Max(value = Parcel.MAX_WEIGHT_GRAMS, message = Messages.WEIGHT)
            Integer weightGrams,
            @NotNull(message = Messages.REQUIRED)
            @Min(value = 1, message = Messages.SIDE) @Max(value = Parcel.MAX_SIDE_MM, message = Messages.SIDE)
            Integer lengthMm,
            @NotNull(message = Messages.REQUIRED)
            @Min(value = 1, message = Messages.SIDE) @Max(value = Parcel.MAX_SIDE_MM, message = Messages.SIDE)
            Integer widthMm,
            @NotNull(message = Messages.REQUIRED)
            @Min(value = 1, message = Messages.SIDE) @Max(value = Parcel.MAX_SIDE_MM, message = Messages.SIDE)
            Integer heightMm) {

        static ParcelBody of(Parcel parcel) {
            return new ParcelBody(parcel.weightGrams(), parcel.lengthMm(), parcel.widthMm(), parcel.heightMm());
        }

        Parcel toDomain() {
            return new Parcel(weightGrams, lengthMm, widthMm, heightMm);
        }
    }

    /** The contract's Place; the response repeats it. */
    record PlaceBody(
            @NotNull(message = Messages.REQUIRED) @Pattern(regexp = "^[A-Z]{2}$", message = Messages.COUNTRY)
            String countryCode,
            @NotNull(message = Messages.REQUIRED)
            @Pattern(regexp = "^[A-Z0-9][A-Z0-9 -]{0,8}[A-Z0-9]$", message = Messages.POSTCODE)
            String postcode) {

        static PlaceBody of(Place place) {
            return new PlaceBody(place.countryCode(), place.postcode());
        }

        Place toDomain() {
            return new Place(countryCode, postcode);
        }
    }

    /** What an invalid value is told, in the contract's words. */
    static final class Messages {

        static final String REQUIRED = "is required";
        static final String WEIGHT = "must be between 1 and " + Parcel.MAX_WEIGHT_GRAMS;
        static final String SIDE = "must be between 1 and " + Parcel.MAX_SIDE_MM;
        static final String COUNTRY = "must be two upper-case letters (ISO 3166-1 alpha-2)";
        static final String POSTCODE = "must be 2 to 10 upper-case letters, digits, spaces or hyphens, starting and "
                + "ending with a letter or digit";

        private Messages() {
        }
    }
}
