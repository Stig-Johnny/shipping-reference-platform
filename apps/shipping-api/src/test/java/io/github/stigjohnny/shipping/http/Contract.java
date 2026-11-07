package io.github.stigjohnny.shipping.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * it (ADR 0004): its status is documented for the operation, or is the generic 404 or 405 that any path can give; its
 * documented headers are present and valid; its media type is documented; its body validates against the documented
 * schema with networknt's json-schema-validator in the OpenAPI 3.1 dialect; and a problem's type, title and status are
 * exactly those of the problem registry in the contract's description, with the response's correlation ID and the
 * request path as its instance. Every response is also checked for {@code Cache-Control: no-store}.
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
    /** A row of the problem registry in the contract's description: the type's suffix, the status and the title. */
    private static final Pattern REGISTRY_ROW = Pattern.compile(
            "^\\| `([a-z-]+)`\\s*\\| (\\d{3})\\s*\\| ([^|]+?)\\s*\\|");
    private static final Contract CONTRACT = read();

    private final JsonNode document;
    private final SchemaRegistry schemas;
    private final JsonMapper json = JsonMapper.builder().build();
    /** The problem registry: type suffix to status and title. */
    private final Map<String, Registered> problems;

    private record Registered(int status, String title) {
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
    }

    /** The contract on the test class path. */
    public static Contract contract() {
        return CONTRACT;
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

    /** Asserts that the response is the generic 404 or 405 problem that the contract allows on any path. */
    public JsonNode assertGenericProblem(MvcResult result) {
        return assertGenericProblem(result.getResponse(), result.getRequest().getRequestURI());
    }

    private JsonNode assertGenericProblem(MockHttpServletResponse response, String requestPath) {
        int status = response.getStatus();
        String what = "generic " + status;
        assertThat(status).as("%s: a status the contract allows on any path", what).isIn(404, 405);
        assertThat(response.getHeader("Cache-Control")).as("%s: every response is not cached", what)
                .isEqualTo("no-store");
        Located correlationId = new Located(document, "#").at("components").at("headers").at("CorrelationId");
        assertHeader(CORRELATION_ID, correlationId, response, what);
        String mediaType = mediaType(response, what);
        assertThat(mediaType).as(what).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode body = assertBody(DOCUMENT + PROBLEM_SCHEMA, mediaType, response, requestPath, what);
        assertThat(body.path("type").stringValue()).as("%s: the generic problem's type", what)
                .isEqualTo(PROBLEM_TYPES + (status == 404 ? "not-found" : "method-not-allowed"));
        return body;
    }

    /** The title the contract's problem registry gives a type, for example {@code quote-not-found}. */
    public String problemTitle(String type) {
        Registered registered = problems.get(type);
        assertThat(registered).as("problem type %s is in the contract's registry", type).isNotNull();
        return Objects.requireNonNull(registered).title();
    }

    private JsonNode assertBody(String location, String mediaType, MockHttpServletResponse response,
            String requestPath, String what) {
        String body = body(response);
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
                .isEqualTo(requestPath);
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
        SchemaRegistry schemas = SchemaRegistry.withDefaultDialect(Dialects.getOpenApi31(), registry -> registry
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
