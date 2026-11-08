package io.github.stigjohnny.shipping.shipments.internal.adapter.in.web;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.shipments.internal.domain.BookingCommand;
import io.github.stigjohnny.shipping.shipments.internal.domain.Recipient;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Closed JSON records, validated before constructing domain values; regex lengths count Unicode code points. */
record BookingRequestBody(
        @NotNull(message = "is required") @Pattern(regexp = UUID_PATTERN, message = "must be a lowercase UUID") String quoteId,
        @NotNull(message = "is required") @Pattern(regexp = UUID_PATTERN, message = "must be a lowercase UUID") String offerId,
        @NotNull(message = "is required") @Valid RecipientBody recipient,
        @JsonDeserialize(using = OptionalMember.class)
        @Pattern(regexp = "^[A-Za-z0-9 ._/-]{1,35}$", message = "must be 1 to 35 letters, digits, spaces, dots, underscores, slashes or hyphens")
        @Nullable String reference) {
    static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    private static final String LINE = "^[^\\x00-\\x1F\\x7F\\p{Cs}]{1,70}$";
    private static final String CITY = "^[^\\x00-\\x1F\\x7F\\p{Cs}]{1,50}$";
    private static final String TEXT_MESSAGE = "must be well-formed Unicode within the field's length bound, without control characters";

    BookingCommand toDomain() {
        return new BookingCommand(new QuoteId(UUID.fromString(quoteId)), new OfferId(UUID.fromString(offerId)),
                recipient.toDomain(), reference == null ? null : new ShipmentReference(reference));
    }

    record RecipientBody(
            @NotNull(message = "is required") @Pattern(regexp = LINE, message = TEXT_MESSAGE) String name,
            @NotNull(message = "is required") @Pattern(regexp = LINE, message = TEXT_MESSAGE) String addressLine1,
            @JsonDeserialize(using = OptionalMember.class) @Pattern(regexp = LINE, message = TEXT_MESSAGE)
            @Nullable String addressLine2,
            @NotNull(message = "is required") @Pattern(regexp = "^[A-Z0-9][A-Z0-9 -]{0,8}[A-Z0-9]$",
                    message = "must be 2 to 10 upper-case letters, digits, spaces or hyphens, starting and ending with a letter or digit") String postcode,
            @NotNull(message = "is required") @Pattern(regexp = CITY, message = TEXT_MESSAGE) String city,
            @NotNull(message = "is required") @Pattern(regexp = "^[A-Z]{2}$", message = "must be two upper-case letters") String countryCode) {
        Recipient toDomain() {
            return new Recipient(name, addressLine1, addressLine2, postcode, city, countryCode);
        }
    }
}
