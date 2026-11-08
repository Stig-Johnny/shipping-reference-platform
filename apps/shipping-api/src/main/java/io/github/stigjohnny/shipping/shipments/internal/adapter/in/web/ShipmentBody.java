package io.github.stigjohnny.shipping.shipments.internal.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.stigjohnny.shipping.quotes.api.Money;
import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.shipments.internal.domain.Recipient;
import io.github.stigjohnny.shipping.shipments.internal.domain.Shipment;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentSlice;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentStatus;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Only contract fields leave the module: no tenant, key or request fingerprint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ShipmentBody(UUID shipmentId, UUID quoteId, UUID offerId, ShipmentStatus status, ServiceLevel serviceLevel,
        MoneyBody price, String trackingNumber, Place origin, RecipientBody recipient, @Nullable String reference,
        String bookedAt) {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    static ShipmentBody of(Shipment shipment) {
        return new ShipmentBody(shipment.id().value(), shipment.quoteId().value(), shipment.offerId().value(),
                shipment.status(), shipment.serviceLevel(), MoneyBody.of(shipment.price()), shipment.trackingNumber().value(),
                shipment.origin(), RecipientBody.of(shipment.recipient()),
                shipment.reference() == null ? null : shipment.reference().value(), TIMESTAMP.format(shipment.bookedAt()));
    }

    record MoneyBody(String amount, String currency) {
        static MoneyBody of(Money money) {
            return new MoneyBody(money.amount().toPlainString(), money.currency().getCurrencyCode());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RecipientBody(String name, String addressLine1, @Nullable String addressLine2, String postcode, String city,
            String countryCode) {
        static RecipientBody of(Recipient recipient) {
            return new RecipientBody(recipient.name(), recipient.addressLine1(), recipient.addressLine2(),
                    recipient.postcode(), recipient.city(), recipient.countryCode());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Summary(UUID shipmentId, ShipmentStatus status, ServiceLevel serviceLevel, MoneyBody price,
            String trackingNumber, String recipientName, Place destination, @Nullable String reference, String bookedAt) {
        static Summary of(Shipment shipment) {
            return new Summary(shipment.id().value(), shipment.status(), shipment.serviceLevel(),
                    MoneyBody.of(shipment.price()), shipment.trackingNumber().value(), shipment.recipient().name(),
                    new Place(shipment.recipient().countryCode(), shipment.recipient().postcode()),
                    shipment.reference() == null ? null : shipment.reference().value(), TIMESTAMP.format(shipment.bookedAt()));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Page(List<Summary> items, @Nullable String nextCursor) {
        static Page of(ShipmentSlice slice) {
            return new Page(slice.items().stream().map(Summary::of).toList(), slice.nextCursor());
        }
    }
}
