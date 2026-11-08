package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The tenants module's problems, which the contract's registry assigns to it: {@code 401 unauthenticated} with the
 * RFC 6750 challenge, and {@code 403 no-tenant-membership} for a user who belongs to no tenant. The security chain's
 * entry point and access-denied handler hand their exceptions to the MVC exception resolvers, which call these
 * handlers before the HTTP edge's fallback. A member is denied only outside the API, where nothing is served, so that
 * denial is reported as an unknown path.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthenticationProblems {

    private static final String TYPES = "https://shipping-reference-platform.example/problems/";

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"shipping-reference-platform\"")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication required",
                        "A valid demo bearer token is required."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> denied() {
        ProblemDetail problem = isMember() ? problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found",
                "No resource exists at this path.") : problem(HttpStatus.FORBIDDEN, "no-tenant-membership",
                "No tenant membership", "The authenticated user belongs to no tenant.");
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static boolean isMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> SecurityConfiguration.TENANT_MEMBER.equals(authority.getAuthority()));
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPES + type));
        problem.setTitle(title);
        return problem;
    }
}
