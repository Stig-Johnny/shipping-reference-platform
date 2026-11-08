package io.github.stigjohnny.shipping.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialects;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * contracts/shipping.openapi.yaml, read from the test class path, and the check that an exercised response conforms to
 * it (ADR 0004). A response conforms when:
 *
 * <ul>
 *   <li>its status is documented for the operation, or is one of the problems that the contract's description allows
 *       on any path (400 malformed-request, 401, 403, 404 and 405);
 *   <li>its documented headers are present and valid, and it carries {@code Cache-Control: no-store};
 *   <li>its media type is documented, and its body validates against the documented schema with networknt's
 *       json-schema-validator in the OpenAPI 3.1 dialect, with formats asserted, not only annotated;
 *   <li>a problem's type, title and status are exactly those of the problem registry in the contract's description,
 *       its correlation ID is the response's, and its instance is the request path, or {@code about:blank} where the
 *       contract says the path cannot be repeated.
 * </ul>
 *
 * <p>As the validator's review requires (security/reviews/maven--json-schema-validator--3.0.9.md), the contract is a
 * {@code classpath:} resource, sub-schemas are JSON pointers into it, and the schema loader may load {@code classpath:}
 * IRIs only, so a reference to anything else fails instead of reaching the network. The contract is read with the
 * default YAMLFactory.
 */
public final class Contract {

    private static final String RESOURCE = "contracts/shipping.openapi.yaml";
    private static final String DOCUMENT = "classpath:" + RESOURCE;
    private static final String PROBLEM_TYPES = "https://shipping-reference-platform.example/problems/";
    private static final String PROBLEM_SCHEMA = "#/components/schemas/Problem";
    private static final String CORRELATION_ID = "Correlation-Id";
    /** The longest request path that a problem's instance repeats; a longer one is {@code about:blank}. */
    private static final int MAX_INSTANCE = 2048;
    private static final String NO_INSTANCE = "about:blank";
    /**
     * The problems that can occur on any path (the contract's description, "Errors"), with the component that
     * documents each one's headers, if there is one.
     */
    private static final Map<Integer, AnyPath> ANY_PATH = Map.of(
            400, new AnyPath("malformed-request", "BadRequest"),
            401, new AnyPath("unauthenticated", "Unauthenticated"),
            403, new AnyPath("no-tenant-membership", "NoTenantMembership"),
            404, new AnyPath("not-found", ""),
            405, new AnyPath("method-not-allowed", ""));
    /** A row of the problem registry in the contract's description: the type's suffix, the status and the title. */
    private static final Pattern REGISTRY_ROW = Pattern.compile(
            "^\\| `([a-z-]+)`\\s*\\| (\\d{3})\\s*\\| ([^|]+?)\\s*\\|");
    private static final Contract CONTRACT = read();

    private final JsonNode document;
    private final SchemaRegistry schemas;
    private final JsonMapper json = JsonMapper.builder().build();
    /** The problem registry: type suffix to status and title. */
    private final Map<String, Registered> problems;
    /** The server's base path, {@code /api/v1}, from the contract's servers. */
    private final String basePath;
    /** Each path of the contract as it writes it, with the regular expression its templates expand to. */
    private final Map<String, Pattern> paths;

    private record Registered(int status, String title) {
    }

    /** A problem that can occur on any path: its type, and its response component or none. */
    private record AnyPath(String type, String component) {
    }

    /** A node of the contract and the JSON pointer it was found at. */
    private record Located(JsonNode node, String pointer) {

        Located at(String name) {
            return new Located(node.path(name), pointer + "/" + name.replace("~", "~0").replace("/", "~1"));
        }
    }

    private Contract(JsonNode document, SchemaRegistry schemas, Map<String, Registered> problems) {
        this.document = document;
        this.schemas = schemas;
        this.problems = problems;
        this.basePath = document.path("servers").path(0).path("url").stringValue("");
        assertThat(basePath).as("the contract's server").isEqualTo("/api/v1");
        Map<String, Pattern> templates = new TreeMap<>();
        for (String path : document.path("paths").propertyNames()) {
            templates.put(path, Pattern.compile(Pattern.quote(path).replaceAll("\\{[A-Za-z]+}", "\\\\E[^/]+\\\\Q")));
        }
        this.paths = templates;
    }

    /** The contract on the test class path. */
    public static Contract contract() {
        return CONTRACT;
    }

    /**
     * Asserts that the response to any request conforms to the contract: to the response it documents for the operation
     * that the request's method and path name, or, when they name none, to a problem that can occur on any path.
     * {@link Http#mockMvc} applies it to every response that a test exercises, so no response goes unchecked.
     */
    public void assertExercised(MvcResult result) {
        assertExercised(result.getRequest().getMethod(), result.getRequest().getRequestURI(), result.getResponse());
    }

    /** As {@link #assertExercised(MvcResult)}, for the response to a request with this method and URI. */
    void assertExercised(String requestMethod, String requestUri, MockHttpServletResponse response) {
        String method = requestMethod.toLowerCase(Locale.ROOT);
        String path = requestUri.startsWith(basePath + "/") ? requestUri.substring(basePath.length()) : "";
        for (Map.Entry<String, Pattern> template : paths.entrySet()) {
            if (template.getValue().matcher(path).matches()
                    && document.path("paths").path(template.getKey()).has(method)) {
                assertConforms(method, template.getKey(), response, requestUri);
                return;
            }
        }
        assertGenericProblem(response, requestUri);
    }

    /** The contract's description, which documents the problem registry and the problems of any path. */
    String description() {
        return document.path("info").path("description").stringValue("");
    }

    /** The problems that can occur on any path, by status: their type suffixes. */
    static Map<Integer, String> anyPathTypes() {
        Map<Integer, String> types = new TreeMap<>();
        ANY_PATH.forEach((status, anyPath) -> types.put(status, anyPath.type()));
        return types;
    }

    /**
     * Asserts that the response conforms to the contract's response for the operation at {@code path} (as the
     * contract writes it, for example {@code /quotes/{quoteId}}) and {@code method} (lower case), and returns its body.
     */
    public JsonNode assertConforms(String method, String path, MvcResult result) {
        return assertConforms(method, path, result.getResponse(), result.getRequest().getRequestURI());
    }

    /** As {@link #assertConforms(String, String, MvcResult)}, for a response to a request for {@code requestPath}. */
    JsonNode assertConforms(String method, String path, MockHttpServletResponse response, String requestPath) {
        Located operation = new Located(document, "#").at("paths").at(path).at(method);
        assertThat(operation.node().isObject()).as("%s %s is an operation of the contract", method, path).isTrue();
        Located documented = operation.at("responses").at(String.valueOf(response.getStatus()));
        if (documented.node().isMissingNode()) {
            return assertGenericProblem(response, requestPath);
        }
        String what = method + " " + path + " " + response.getStatus();
        Located spec = resolve(documented);
        assertThat(response.getHeader("Cache-Control")).as("%s: every response is not cached", what)
                .isEqualTo("no-store");
        for (String header : spec.node().path("headers").propertyNames()) {
            assertHeader(header, resolve(spec.at("headers").at(header)), response, what);
        }
        Located content = spec.at("content");
        if (content.node().isMissingNode() || content.node().isEmpty()) {
            assertThat(body(response)).as("%s has no body", what).isEmpty();
            return json.missingNode();
        }
        String mediaType = mediaType(response, what);
        assertThat(content.node().propertyNames()).as("%s: the documented media types", what).contains(mediaType);
        return assertBody(schemaLocation(content.at(mediaType).at("schema")), mediaType, response, requestPath, what);
    }

    /**
     * Asserts that the response is a problem that the contract allows on any path, such as the 404 of an unknown path,
     * and returns its body.
     */
    public JsonNode assertGenericProblem(MvcResult result) {
        return assertGenericProblem(result.getResponse(), result.getRequest().getRequestURI());
    }

    private JsonNode assertGenericProblem(MockHttpServletResponse response, String requestPath) {
        int status = response.getStatus();
        String what = "any path " + status;
        AnyPath anyPath = ANY_PATH.get(status);
        assertThat(anyPath).as("%s: a status the contract allows on any path", what).isNotNull();
        assertThat(response.getHeader("Cache-Control")).as("%s: every response is not cached", what)
                .isEqualTo("no-store");
        Located components = new Located(document, "#").at("components");
        if (Objects.requireNonNull(anyPath).component().isEmpty()) {
            assertHeader(CORRELATION_ID, components.at("headers").at("CorrelationId"), response, what);
        } else {
            Located spec = components.at("responses").at(anyPath.component());
            for (String header : spec.node().path("headers").propertyNames()) {
                assertHeader(header, resolve(spec.at("headers").at(header)), response, what);
            }
        }
        String mediaType = mediaType(response, what);
        assertThat(mediaType).as(what).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode body = assertBody(DOCUMENT + PROBLEM_SCHEMA, mediaType, response, requestPath, what);
        assertThat(body.path("type").stringValue()).as("%s: the type of the problem allowed on any path", what)
                .isEqualTo(PROBLEM_TYPES + anyPath.type());
        return body;
    }

    /**
     * The instance a problem gives for a request path: the path itself, unless it is longer than 2048 characters or is
     * not a valid URI reference, in which case the contract's description says it is {@code about:blank}.
     */
    static String instanceFor(String requestPath) {
        if (requestPath.length() > MAX_INSTANCE) {
            return NO_INSTANCE;
        }
        try {
            new URI(requestPath);
            return requestPath;
        } catch (URISyntaxException e) {
            return NO_INSTANCE;
        }
    }

    /** The title the contract's problem registry gives a type, for example {@code quote-not-found}. */
    public String problemTitle(String type) {
        Registered registered = problems.get(type);
        assertThat(registered).as("problem type %s is in the contract's registry", type).isNotNull();
        return Objects.requireNonNull(registered).title();
    }

    private JsonNode assertBody(String location, String mediaType, MockHttpServletResponse response,
            String requestPath, String what) {
        // SVG is a raw HTTP string, not a JSON document. Validate its JSON string representation against the
        // operation's string schema, keeping all documented headers and length assertions active.
        String body = mediaType.equals("image/svg+xml") ? json.writeValueAsString(body(response)) : body(response);
        List<Error> errors = schemas.getSchema(SchemaLocation.of(location)).validate(body, InputFormat.JSON);
        assertThat(errors).as("%s: the body %s against %s", what, body, location).isEmpty();
        JsonNode parsed = json.readTree(body);
        if (mediaType.equals(MediaType.APPLICATION_PROBLEM_JSON_VALUE)) {
            assertProblem(parsed, response, requestPath, what);
        }
        return parsed;
    }

    private void assertHeader(String name, Located header, MockHttpServletResponse response, String what) {
        String value = response.getHeader(name);
        if (value == null) {
            assertThat(header.node().path("required").asBoolean(false))
                    .as("%s: required header %s is missing", what, name).isFalse();
            return;
        }
        String location = schemaLocation(header.at("schema"));
        List<Error> errors = schemas.getSchema(SchemaLocation.of(location)).validate(json.writeValueAsString(value),
                InputFormat.JSON);
        assertThat(errors).as("%s: header %s: %s against %s", what, name, value, location).isEmpty();
    }

    private void assertProblem(JsonNode problem, MockHttpServletResponse response, String requestPath, String what) {
        String type = problem.path("type").stringValue("");
        assertThat(type).as("%s: a problem type of the registry", what).startsWith(PROBLEM_TYPES);
        Registered registered = problems.get(type.substring(PROBLEM_TYPES.length()));
        assertThat(registered).as("%s: %s is in the contract's problem registry", what, type).isNotNull();
        assertThat(Objects.requireNonNull(registered).status()).as("%s: the registry's status for %s", what, type)
                .isEqualTo(response.getStatus());
        assertThat(problem.path("status").asInt()).as("%s: the problem's status", what).isEqualTo(response.getStatus());
        assertThat(problem.path("title").stringValue()).as("%s: the registry's title for %s", what, type)
                .isEqualTo(registered.title());
        assertThat(problem.path("correlationId").stringValue()).as("%s: the problem's correlation ID", what)
                .isEqualTo(response.getHeader(CORRELATION_ID));
        assertThat(problem.path("instance").stringValue()).as("%s: the problem's instance, the request path", what)
                .isEqualTo(instanceFor(requestPath));
    }

    /** Where the validator finds a schema: the component it references, or the inline schema's own pointer. */
    private static String schemaLocation(Located schema) {
        String reference = schema.node().path("$ref").stringValue("");
        return DOCUMENT + (reference.isEmpty() ? schema.pointer() : reference);
    }

    private Located resolve(Located located) {
        String reference = located.node().path("$ref").stringValue("");
        if (reference.isEmpty()) {
            return located;
        }
        Located target = new Located(document, "#");
        for (String segment : reference.substring("#/".length()).split("/", -1)) {
            target = target.at(segment.replace("~1", "/").replace("~0", "~"));
        }
        assertThat(target.node().isMissingNode()).as("%s resolves", reference).isFalse();
        return target;
    }

    private static String mediaType(MockHttpServletResponse response, String what) {
        String contentType = response.getContentType();
        assertThat(contentType).as("%s has a content type", what).isNotNull();
        MediaType actual = MediaType.parseMediaType(Objects.requireNonNull(contentType));
        return actual.getType() + "/" + actual.getSubtype();
    }

    private static String body(MockHttpServletResponse response) {
        try {
            return response.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Contract read() {
        JsonNode document;
        try (InputStream in = Contract.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is not on the test class path.");
            }
            document = YAMLMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Formats are assertions here, not annotations: a date-time, a URI reference or a UUID must be one.
        SchemaRegistry schemas = SchemaRegistry.withDefaultDialect(Dialects.getOpenApi31(), registry -> registry
                .schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build())
                .schemaLoader(loader -> loader.allow(iri -> iri.toString().startsWith("classpath:"))));
        Map<String, Registered> problems = new TreeMap<>();
        for (String line : document.path("info").path("description").stringValue("").lines().toList()) {
            Matcher row = REGISTRY_ROW.matcher(line);
            if (row.find()) {
                problems.put(row.group(1), new Registered(Integer.parseInt(row.group(2)), row.group(3)));
            }
        }
        assertThat(problems).as("the contract's problem registry").hasSize(18);
        return new Contract(document, schemas, problems);
    }
}
