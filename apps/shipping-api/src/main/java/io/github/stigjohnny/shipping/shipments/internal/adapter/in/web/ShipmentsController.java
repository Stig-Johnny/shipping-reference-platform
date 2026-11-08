package io.github.stigjohnny.shipping.shipments.internal.adapter.in.web;

import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import io.github.stigjohnny.shipping.shipments.internal.application.BookShipment;
import io.github.stigjohnny.shipping.shipments.internal.application.GetShipment;
import io.github.stigjohnny.shipping.shipments.internal.application.ListShipments;
import io.github.stigjohnny.shipping.shipments.internal.application.RenderLabel;
import io.github.stigjohnny.shipping.shipments.internal.domain.BookingOutcome;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdempotencyKey;
import io.github.stigjohnny.shipping.shipments.internal.domain.LabelOutcome;
import io.github.stigjohnny.shipping.shipments.internal.domain.ListingOutcome;
import io.github.stigjohnny.shipping.shipments.internal.domain.Shipment;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Book, list, read and download a simulated label; every use case derives tenant identity from CurrentTenant. */
@RestController
@RequestMapping("/api/v1/shipments")
class ShipmentsController {
    private final BookShipment book;
    private final GetShipment get;
    private final ListShipments list;
    private final RenderLabel label;

    ShipmentsController(BookShipment book, GetShipment get, ListShipments list, RenderLabel label) {
        this.book = Objects.requireNonNull(book, "book");
        this.get = Objects.requireNonNull(get, "get");
        this.list = Objects.requireNonNull(list, "list");
        this.label = Objects.requireNonNull(label, "label");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> book(@RequestHeader(name = "Idempotency-Key", required = false)
            @NotNull(message = "is required") @Pattern(regexp = "^[A-Za-z0-9_-]{16,64}$",
                    message = "must be 16 to 64 letters, digits, underscores or hyphens") String key,
            @Valid @RequestBody BookingRequestBody body) {
        return switch (book.handle(new IdempotencyKey(key), body.toDomain())) {
            case BookingOutcome.Booked(var shipment) -> booked(shipment, false);
            case BookingOutcome.Replayed(var shipment) -> booked(shipment, true);
            case BookingOutcome.Rejected(var reason) -> ShipmentProblems.rejected(reason);
        };
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> list(@RequestParam(required = false)
            @Pattern(regexp = "^(?:[1-9][0-9]?|100)$", message = "must be an integer between 1 and 100") @Nullable String limit,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(required = false) @Pattern(regexp = "^(BOOKED|CANCELLED)$", message = "must be BOOKED or CANCELLED")
            @Nullable String status) {
        return switch (list.handle(new ShipmentQuery(limit == null ? 20 : Integer.parseInt(limit), cursor, status))) {
            case ListingOutcome.Listed(var page) -> ResponseEntity.ok().body(ShipmentBody.Page.of(page));
            case ListingOutcome.InvalidCursor _ -> ShipmentProblems.invalidCursor();
        };
    }

    @GetMapping(path = "/{shipmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> get(@PathVariable @Pattern(regexp = BookingRequestBody.UUID_PATTERN,
            message = "must be a lowercase UUID") String shipmentId) {
        return get.handle(new ShipmentId(UUID.fromString(shipmentId)))
                .map(shipment -> ResponseEntity.ok().<Object>body(ShipmentBody.of(shipment)))
                .orElseGet(ShipmentProblems::notFound);
    }

    @GetMapping(path = "/{shipmentId}/label", produces = "image/svg+xml")
    public ResponseEntity<Object> label(@PathVariable @Pattern(regexp = BookingRequestBody.UUID_PATTERN,
            message = "must be a lowercase UUID") String shipmentId) {
        return switch (label.handle(new ShipmentId(UUID.fromString(shipmentId)))) {
            case LabelOutcome.Rendered(var svg, var tracking) -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/svg+xml"))
                    .header("Content-Disposition", "attachment; filename=\"label-" + tracking.value() + ".svg\"")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; sandbox")
                    .body(svg.getBytes(StandardCharsets.UTF_8));
            case LabelOutcome.NotFound _ -> ShipmentProblems.notFound();
        };
    }

    private static ResponseEntity<Object> booked(Shipment shipment, boolean replayed) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(replayed ? 200 : 201)
                .location(URI.create("/api/v1/shipments/" + shipment.id().value()));
        if (replayed) {
            response.header("Idempotency-Replayed", "true");
        }
        return response.body(ShipmentBody.of(shipment));
    }
}
