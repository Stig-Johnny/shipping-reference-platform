package io.github.stigjohnny.shipping;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * The HTTP edge's problems (ADR 0004). Failures that Spring detects before or after a module's code runs become RFC
 * 9457 problems with exactly the contract's types, titles and statuses: a request that fails validation, with each
 * invalid value named ({@code validation-failed}), a body that is missing or not JSON ({@code malformed-request}), an
 * unknown path, a method or media type that the path does not support, and any unexpected exception, whose problem
 * says nothing about its cause, which is logged with the correlation ID ({@code internal-error}). Modules map their
 * own business outcomes in their web adapters.
 *
 * <p>Every problem the application writes, a module's included, passes through {@link #beforeBodyWrite}, which adds
 * the request's correlation ID and, when the problem has none, the request path as its {@code instance}. An isolated
 * class of the model: it uses no module, and no module uses it.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public final class FallbackProblemHandler extends ResponseEntityExceptionHandler implements ResponseBodyAdvice<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackProblemHandler.class);
    private static final String TYPES = "https://shipping-reference-platform.example/problems/";
    /** Errors in a fixed order, by where, name and detail: Bean Validation reports violations in no order. */
    private static final Comparator<Map<String, String>> ERROR_ORDER = Comparator.comparing(
            error -> error.getOrDefault("in", "") + '\u0000' + error.getOrDefault("name", "") + '\u0000'
                    + error.getOrDefault("detail", ""));

    /** Every unexpected exception: logged with its cause, answered without it. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
        LOG.error("Unexpected failure while handling {}", request.getDescription(false), exception);
        ProblemDetail problem = internalError();
        return ResponseEntity.status(problem.getStatus()).headers(problemHeaders(new HttpHeaders())).body(problem);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = switch (exception) {
            case MethodArgumentNotValidException invalid -> validationFailed(invalid.getBindingResult()
                    .getFieldErrors().stream().map(error -> error("body", pointer(error.getField()), message(error))));
            case HandlerMethodValidationException invalid -> validationFailed(invalid.getParameterValidationResults()
                    .stream().flatMap(FallbackProblemHandler::errors));
            case HttpMessageNotReadableException unreadable -> unreadable(unreadable);
            case NoHandlerFoundException _ -> notFound();
            case NoResourceFoundException _ -> notFound();
            case HttpRequestMethodNotSupportedException _ -> problem(HttpStatus.METHOD_NOT_ALLOWED,
                    "method-not-allowed", "Method not allowed", "The path does not support this method.");
            case HttpMediaTypeNotAcceptableException _ -> problem(HttpStatus.NOT_ACCEPTABLE, "not-acceptable",
                    "Not acceptable", "None of the media types that the request accepts can be produced.");
            case HttpMediaTypeNotSupportedException _ -> problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "unsupported-media-type", "Unsupported media type", "The request body must be application/json.");
            default -> otherFailure(exception, statusCode, request);
        };
        return super.handleExceptionInternal(exception, problem, problemHeaders(headers),
                HttpStatusCode.valueOf(problem.getStatus()), request);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem && request instanceof ServletServerHttpRequest servlet) {
            HttpServletRequest http = servlet.getServletRequest();
            if (problem.getInstance() == null) {
                try {
                    problem.setInstance(URI.create(http.getRequestURI()));
                } catch (IllegalArgumentException e) {
                    LOG.warn("The request path is not a URI, so the problem has no instance.");
                }
            }
            if (http.getAttribute(CorrelationIdFilter.ATTRIBUTE) instanceof String correlationId) {
                problem.setProperty("correlationId", correlationId);
            }
        }
        return body;
    }

    private static ProblemDetail otherFailure(Exception exception, HttpStatusCode status, WebRequest request) {
        return switch (status.value()) {
            case 400 -> problem(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request",
                    "The request is malformed.");
            case 404 -> notFound();
            default -> {
                LOG.error("Unexpected failure while handling {}", request.getDescription(false), exception);
                yield internalError();
            }
        };
    }

    /** A body that Jackson could not read: an invalid value is named; anything else is a malformed body. */
    private static ProblemDetail unreadable(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof JacksonException cause && !cause.getPath().isEmpty()) {
            String name = pointer(cause.getPath());
            if (cause instanceof UnrecognizedPropertyException) {
                return validationFailed(Stream.of(error("body", name, "is not a member of this object")));
            }
            if (cause instanceof InputCoercionException) {
                return validationFailed(Stream.of(error("body", name, "is out of range")));
            }
            if (cause instanceof MismatchedInputException mismatch) {
                return validationFailed(Stream.of(error("body", name, expected(mismatch.getTargetType()))));
            }
        }
        return problem(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request",
                "The request body is missing or is not valid JSON.");
    }

    /** The errors of one parameter of a handler method, named where the contract names them. */
    private static Stream<Map<String, String>> errors(ParameterValidationResult result) {
        MethodParameter parameter = result.getMethodParameter();
        String in = parameter.hasParameterAnnotation(PathVariable.class) ? "path"
                : parameter.hasParameterAnnotation(RequestParam.class) ? "query"
                : parameter.hasParameterAnnotation(RequestHeader.class) ? "header" : "body";
        String name = String.valueOf(parameter.getParameterName());
        return result.getResolvableErrors().stream().map(error -> error(in, name, message(error)));
    }

    private static String expected(@Nullable Class<?> type) {
        if (type == null) {
            return "has the wrong type";
        }
        if (type.isEnum()) {
            return "must be one of " + Stream.of(type.getEnumConstants()).map(constant -> ((Enum<?>) constant).name())
                    .collect(Collectors.joining(", "));
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return "must be an integer";
        }
        return type == String.class ? "must be a string" : "must be an object";
    }

    /** A JSON Pointer (RFC 6901) from a bound field's path, such as {@code parcel.weightGrams}. */
    private static String pointer(String field) {
        return "/" + field.replaceAll("\\[(\\d+)]", ".$1").replace('.', '/');
    }

    /** A JSON Pointer (RFC 6901) from the path of a Jackson exception. */
    private static String pointer(List<JacksonException.Reference> path) {
        StringBuilder pointer = new StringBuilder();
        for (JacksonException.Reference reference : path) {
            String property = reference.getPropertyName();
            pointer.append('/').append(property == null ? String.valueOf(reference.getIndex())
                    : property.replace("~", "~0").replace("/", "~1"));
        }
        return pointer.toString();
    }

    private static String message(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null ? "is invalid" : message;
    }

    private static Map<String, String> error(String in, String name, String detail) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("in", in);
        error.put("name", name);
        error.put("detail", detail);
        return error;
    }

    private static ProblemDetail validationFailed(Stream<Map<String, String>> errors) {
        List<Map<String, String>> sorted = new ArrayList<>(errors.toList());
        sorted.sort(ERROR_ORDER);
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Request validation failed",
                "The request has " + sorted.size() + " invalid value" + (sorted.size() == 1 ? "." : "s."));
        problem.setProperty("errors", sorted);
        return problem;
    }

    private static ProblemDetail notFound() {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", "No resource exists at this path.");
    }

    private static ProblemDetail internalError() {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
                "An unexpected error occurred. Quote the correlation ID when you report it.");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPES + type));
        problem.setTitle(title);
        return problem;
    }

    private static HttpHeaders problemHeaders(HttpHeaders headers) {
        HttpHeaders copy = HttpHeaders.copyOf(headers);
        copy.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return copy;
    }
}
