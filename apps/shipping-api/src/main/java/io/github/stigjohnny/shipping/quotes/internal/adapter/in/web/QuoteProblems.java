package io.github.stigjohnny.shipping.quotes.internal.adapter.in.web;

import io.github.stigjohnny.shipping.quotes.internal.domain.Parcel;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteRequest;
import io.github.stigjohnny.shipping.quotes.internal.domain.UnavailableReason;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * The quotes module's business outcomes as the contract's problems: {@code 422 no-offer-available} with its reason,
 * and {@code 404 quote-not-found}, whose detail is the same for another tenant's quote as for an unknown one. The HTTP
 * edge adds the correlation ID and the request path.
 */
final class QuoteProblems {

    private static final String TYPES = "https://shipping-reference-platform.example/problems/";

    private QuoteProblems() {
    }

    static ResponseEntity<Object> noOfferAvailable(UnavailableReason reason, QuoteRequest request) {
        int billable = request.parcel().billableWeightGrams();
        String detail = switch (reason) {
            case ROUTE_NOT_SERVED -> "The route from " + request.origin().countryCode() + " to "
                    + request.destination().countryCode() + " is not served by the simulated carrier.";
            case BILLABLE_WEIGHT_ABOVE_LIMIT -> "The billable weight of " + billable + " g is above the limit of "
                    + Parcel.MAX_WEIGHT_GRAMS + " g.";
            case SERVICE_LEVEL_NOT_AVAILABLE -> Objects.requireNonNull(request.serviceLevel())
                    + " is not available for a billable weight of " + billable + " g.";
        };
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "no-offer-available", "No offer available",
                detail);
        problem.setProperty("reason", reason.name());
        return response(problem);
    }

    static ResponseEntity<Object> quoteNotFound() {
        return response(problem(HttpStatus.NOT_FOUND, "quote-not-found", "Quote not found",
                "No quote with this ID exists."));
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPES + type));
        problem.setTitle(title);
        return problem;
    }

    private static ResponseEntity<Object> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
