package io.github.stigjohnny.shipping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The first filter of every request, before Spring Security's (ADR 0004). Every response carries {@code
 * Correlation-Id} and {@code Cache-Control: no-store}, the contract's two headers for every response. The client's
 * correlation ID is used when it is valid, 8 to 64 characters from {@code A-Z a-z 0-9 -}; any other value, or none,
 * is replaced by a new UUID. The ID is in the MDC under {@code correlationId} for the request's log lines, and in a
 * request attribute, from which {@link FallbackProblemHandler} puts it into every problem.
 *
 * <p>A request whose path a problem could not repeat as its {@code instance}, a path longer than 2048 characters or one
 * that is not a valid URI reference, is refused here, before routing, as the contract's description says. An exception
 * that escapes the rest of the chain, from a filter such as the authentication filter as well as from the dispatcher,
 * becomes a problem through the MVC exception resolvers, as one from a controller does, unless the response is already
 * committed. An isolated class of the model: it uses no module, and no module uses it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter extends OncePerRequestFilter {

    /** The request and response header. */
    static final String HEADER = "Correlation-Id";
    /** The request attribute that holds the request's correlation ID. */
    static final String ATTRIBUTE = CorrelationIdFilter.class.getName();
    /** The MDC key, as ADR 0004 names it. */
    static final String MDC_KEY = "correlationId";
    /** The longest path a problem repeats: the contract's bound of {@code instance}. */
    static final int MAX_PATH = 2048;
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private final HandlerExceptionResolver problems;

    CorrelationIdFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver problems) {
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        String correlationId = presented != null && VALID.matcher(presented).matches() ? presented
                : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, correlationId);
        everyResponsesHeaders(response, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            Optional<RefusedPath> refused = refusal(request.getRequestURI());
            if (refused.isPresent()) {
                if (problems.resolveException(request, response, null, refused.get()) == null) {
                    throw refused.get();
                }
                return;
            }
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            if (response.isCommitted()) {
                throw e;
            }
            response.reset();
            everyResponsesHeaders(response, correlationId);
            if (problems.resolveException(request, response, null, e) == null) {
                throw e;
            }
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Why a problem could not repeat this path, if it could not. */
    private static Optional<RefusedPath> refusal(String path) {
        if (path.length() > MAX_PATH) {
            return Optional.of(new RefusedPath("The request path is longer than " + MAX_PATH + " characters."));
        }
        try {
            new URI(path);
            return Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.of(new RefusedPath("The request path is not a valid URI reference."));
        }
    }

    private static void everyResponsesHeaders(HttpServletResponse response, String correlationId) {
        response.setHeader(HEADER, correlationId);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /** A request refused before routing because of its path; the message is fixed text that never repeats it. */
    static final class RefusedPath extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        RefusedPath(String message) {
            super(message, null, false, false);
        }
    }
}
