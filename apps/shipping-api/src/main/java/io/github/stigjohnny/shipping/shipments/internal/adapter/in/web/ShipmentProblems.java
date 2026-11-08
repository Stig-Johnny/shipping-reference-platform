package io.github.stigjohnny.shipping.shipments.internal.adapter.in.web;

import io.github.stigjohnny.shipping.shipments.internal.domain.BookingRejection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** Business refusals as the contract's fixed type/status/title, without reflecting personal data. */
final class ShipmentProblems {
    private ShipmentProblems() {}

    static ResponseEntity<Object> rejected(BookingRejection reason) {
        return switch (reason) {
            case QUOTE_NOT_FOUND -> problem(404, "quote-not-found", "Quote not found", "No quote with this ID exists.");
            case OFFER_NOT_FOUND -> problem(404, "offer-not-found", "Offer not found", "No offer with this ID exists in the quote.");
            case QUOTE_EXPIRED -> problem(422, "quote-expired", "Quote expired", "The quote has expired. Request a fresh quote.");
            case RECIPIENT_DOES_NOT_MATCH_QUOTE -> problem(422, "recipient-does-not-match-quote", "Recipient does not match quote",
                    "The recipient's country and postcode must match the quote's destination.");
            case QUOTE_ALREADY_BOOKED -> problem(409, "quote-already-booked", "Quote already booked", "The quote already has a shipment.");
            case IDEMPOTENCY_KEY_REUSED -> problem(422, "idempotency-key-reused", "Idempotency key reused",
                    "This key has already been used with a different booking request.");
        };
    }

    static ResponseEntity<Object> notFound() {
        return problem(404, "shipment-not-found", "Shipment not found", "No shipment with this ID exists.");
    }

    static ResponseEntity<Object> invalidCursor() {
        ProblemDetail problem = detail(400, "validation-failed", "Request validation failed", "The request has 1 invalid value.");
        problem.setProperty("errors", List.of(Map.of("in", "query", "name", "cursor", "detail",
                "must be a valid cursor for this tenant and status filter")));
        return response(problem);
    }

    private static ResponseEntity<Object> problem(int status, String type, String title, String detail) {
        return response(detail(status, type, title, detail));
    }

    private static ProblemDetail detail(int status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("https://shipping-reference-platform.example/problems/" + type));
        problem.setTitle(title);
        problem.setDetail(detail);
        return problem;
    }

    private static ResponseEntity<Object> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
