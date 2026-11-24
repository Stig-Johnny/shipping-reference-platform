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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
 * <p>It also reads the contract's own examples, the {@code example} and named {@code examples} of each media type and
 * parameter, so that {@code ContractExamplesTest} can validate each one against its schema as a body is, and a problem
 * example against the registry and its response. An example that its walk meets anywhere else, such as in a schema or a
 * header, fails; a schema keyword that it does not know to hold schemas is data, and an example inside one is not
 * seen.
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
    /** The members of a media type or a parameter that hold examples: one example, or named examples. */
    private static final Set<String> EXAMPLE_KEYS = Set.of("example", "examples");
    /** A schema's keywords whose value is one schema. */
    private static final Set<String> SCHEMA_ONE = Set.of("items", "additionalItems", "additionalProperties", "not",
            "if", "then", "else", "contains", "propertyNames", "unevaluatedItems", "unevaluatedProperties",
            "contentSchema");
    /** A schema's keywords whose value is a list of schemas. */
    private static final Set<String> SCHEMA_LIST = Set.of("allOf", "anyOf", "oneOf", "prefixItems");
    /**
     * A schema's keywords whose members are names, each of a schema; draft 7's {@code dependencies} names a schema or,
     * as data, a list of property names.
     */
    private static final Set<String> SCHEMA_NAME_MAPS = Set.of("properties", "patternProperties", "$defs",
            "definitions", "dependentSchemas", "dependencies");
    /** What a media type with examples belongs to, besides an operation's request body. */
    private static final Set<String> MEDIA_TYPE_OWNERS = Set.of("responses", "requestBodies", "parameters");
    /** One problem type at the start of a response's description, its note, and the separator after it. */
    private static final Pattern RESPONSE_TYPE = Pattern.compile(
            "\\G`([a-z]+(?:-[a-z]+)*)`(?: \\([^()]*\\))?(, or |, | or )?");
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

        Located item(int index) {
            return new Located(node.path(index), pointer + "/" + index);
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

    /**
     * One example of the contract: where it is, the location of the schema it must validate against and its value. A
     * problem example also carries its response's status and the problem types that its response's description names.
     */
    record Example(String pointer, String schemaLocation, JsonNode value, boolean problem, int status,
            List<String> responseTypes) {
    }

    /** Every example of the contract, in document order. */
    List<Example> examples() {
        return examples(document);
    }

    /** As {@link #examples(JsonNode, String)}, for a document whose schemas are read from the contract itself. */
    static List<Example> examples(JsonNode document) {
        return examples(document, DOCUMENT);
    }

    /**
     * Every example of a contract document, in document order: the {@code example} and the named {@code examples} of a
     * media type or a parameter, each with the location of the whole schema beside it in the document at {@code iri}.
     * An example anywhere else, such as in a schema or a header, or one that holds no value of its own (an
     * {@code externalValue} or a {@code $ref}), fails instead of passing unchecked. A schema's keywords are read by
     * kind, those of JSON Schema 2020-12 and draft 7's {@code definitions} and {@code dependencies}: an example's value,
     * a keyword that holds no schema (such as {@code enum}, {@code default} and {@code dependentRequired}), any keyword
     * this list does not know, and the names of a schema's properties are data, and a member named example there is
     * not an example.
     */
    static List<Example> examples(JsonNode document, String iri) {
        return collect(document, iri, new Located(document, "#"), false, false);
    }

    /**
     * Asserts that an example validates against its schema, as a response body does, and that a problem example is its
     * type's problem in the registry, with the status of its response, of a type that its response's description names.
     */
    void assertExample(Example example) {
        String value = json.writeValueAsString(example.value());
        List<Error> errors = schemas.getSchema(SchemaLocation.of(example.schemaLocation())).validate(value,
                InputFormat.JSON);
        String at = example.pointer();
        assertThat(errors).as("%s: the example %s against %s", at, value, example.schemaLocation()).isEmpty();
        if (!example.problem()) {
            return;
        }
        String type = example.value().path("type").stringValue("");
        assertThat(type).as("%s: a problem type of the registry", at).startsWith(PROBLEM_TYPES);
        String suffix = type.substring(PROBLEM_TYPES.length());
        Registered registered = problems.get(suffix);
        assertThat(registered).as("%s: %s is in the contract's problem registry", at, type).isNotNull();
        assertThat(example.value().path("title").stringValue("")).as("%s: the registry's title for %s", at, type)
                .isEqualTo(Objects.requireNonNull(registered).title());
        assertThat(example.value().path("status").asInt()).as("%s: the problem's status is the status of its response",
                at).isEqualTo(example.status());
        assertThat(registered.status()).as("%s: the registry's status for %s is the status of its response", at, type)
                .isEqualTo(example.status());
        assertThat(example.responseTypes()).as("%s: %s is a type that its response names", at, suffix)
                .contains(suffix);
    }

    /**
     * The examples under {@code at}, in document order. Its members are a schema's keywords when {@code schema}, the
     * names of a schema's properties when {@code names}, and OpenAPI's otherwise.
     */
    private static List<Example> collect(JsonNode document, String iri, Located at, boolean schema, boolean names) {
        JsonNode node = at.node();
        Stream<Example> found = Stream.empty();
        for (int i = 0; node.isArray() && i < node.size(); i++) {
            found = Stream.concat(found, collect(document, iri, at.item(i), schema, names).stream());
        }
        boolean holder = !schema && !names && holdsExamples(segments(at.pointer()));
        for (String key : node.propertyNames()) {
            if (!names && EXAMPLE_KEYS.contains(key)) {
                if (!holder) {
                    throw new AssertionError(at.at(key).pointer() + ": an example in a place where no example is "
                            + "read; only a media type's or a parameter's example and examples are");
                }
                found = Stream.concat(found, examplesOf(document, iri, at, key).stream());
            } else if (names) {
                // Each member is a property's name, and its value a schema.
                found = Stream.concat(found, collect(document, iri, at.at(key), true, false).stream());
            } else if (schema) {
                // A schema's keyword holds a schema, a list of schemas, the names of schemas, or else data or an
                // annotation, which is not read.
                if (SCHEMA_ONE.contains(key) || SCHEMA_LIST.contains(key)) {
                    found = Stream.concat(found, collect(document, iri, at.at(key), true, false).stream());
                } else if (SCHEMA_NAME_MAPS.contains(key)) {
                    found = Stream.concat(found, collect(document, iri, at.at(key), false, true).stream());
                }
            } else {
                boolean schemas = at.pointer().equals("#/components") && key.equals("schemas");
                found = Stream.concat(found, collect(document, iri, at.at(key), key.equals("schema"), schemas)
                        .stream());
            }
        }
        return found.toList();
    }

    /** Whether the node at these pointer segments is a parameter or a media type of a request body, response or parameter. */
    private static boolean holdsExamples(List<String> at) {
        int size = at.size();
        boolean parameter = size >= 2 && at.get(size - 2).equals("parameters");
        boolean mediaType = size >= 3 && at.get(size - 2).equals("content") && (at.get(size - 3).equals("requestBody")
                || (size >= 4 && MEDIA_TYPE_OWNERS.contains(at.get(size - 4))));
        return parameter || mediaType;
    }

    private static List<Example> examplesOf(JsonNode document, String iri, Located holder, String key) {
        List<String> at = segments(holder.pointer());
        Located schema = holder.at("schema");
        if (schema.node().isMissingNode()) {
            throw new AssertionError(holder.pointer() + ": an example needs a schema beside it to be validated");
        }
        String location = iri + schema.pointer();
        boolean problem = at.size() >= 4 && at.getLast().equals(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                && at.get(at.size() - 4).equals("responses");
        String response = problem ? holder.pointer().substring(0, holder.pointer().lastIndexOf("/content/")) : "";
        int status = problem ? status(document, at, response) : 0;
        List<String> types = problem
                ? responseTypes(document.at(response.substring(1)).path("description").stringValue("")) : List.of();
        Located examples = holder.at(key);
        if (key.equals("example")) {
            return List.of(new Example(examples.pointer(), location, examples.node(), problem, status, types));
        }
        if (!examples.node().isObject()) {
            throw new AssertionError(examples.pointer() + ": examples is a map of named examples");
        }
        return examples.node().propertyNames().stream().map(name -> {
            Located example = examples.at(name);
            if (!example.node().has("value") || example.node().has("externalValue") || example.node().has("$ref")) {
                throw new AssertionError(example.pointer() + ": an example holds its value itself, not an "
                        + "externalValue or a $ref");
            }
            return new Example(example.pointer(), location, example.node().path("value"), problem, status, types);
        }).toList();
    }

    /**
     * The status of the response at {@code response}: its key under an operation's responses, or, for a component, the
     * one status under which the operations use it.
     */
    private static int status(JsonNode document, List<String> at, String response) {
        Set<String> statuses = new TreeSet<>();
        if (at.getFirst().equals("paths")) {
            statuses.add(at.get(at.size() - 3));
        } else {
            Located paths = new Located(document, "#").at("paths");
            for (String path : paths.node().propertyNames()) {
                for (String method : paths.at(path).node().propertyNames()) {
                    Located responses = paths.at(path).at(method).at("responses");
                    for (String code : responses.node().propertyNames()) {
                        Located use = responses.at(code);
                        if (!response.equals(use.node().path("$ref").stringValue(""))) {
                            continue;
                        }
                        if (use.node().has("description")) {
                            throw new AssertionError(use.pointer() + " overrides the description of " + response
                                    + ", whose problem example is checked against the component's description only");
                        }
                        statuses.add(code);
                    }
                }
            }
        }
        if (statuses.size() != 1 || !statuses.iterator().next().matches("[1-5][0-9]{2}")) {
            throw new AssertionError(response + ": a response with a problem example has one status code, not "
                    + statuses);
        }
        return Integer.parseInt(statuses.iterator().next());
    }

    /**
     * The problem types that a response's description names first: code spans, each with an optional note in
     * parentheses, joined by commas and "or", and ended by a colon, a full stop or the end. None when it starts
     * otherwise.
     */
    static List<String> responseTypes(String description) {
        String text = description.strip().replaceAll("\\s+", " ");
        Matcher item = RESPONSE_TYPE.matcher(text);
        Stream<String> types = Stream.empty();
        while (item.find()) {
            types = Stream.concat(types, Stream.of(item.group(1)));
            if (item.group(2) == null) {
                boolean ends = item.end() == text.length() || ":.".indexOf(text.charAt(item.end())) >= 0;
                return ends ? types.toList() : List.of();
            }
        }
        return List.of();
    }

    /** A pointer's segments after its {@code #}, unescaped. */
    private static List<String> segments(String pointer) {
        if (pointer.equals("#")) {
            return List.of();
        }
        return List.of(pointer.substring(2).split("/", -1)).stream()
                .map(segment -> segment.replace("~1", "/").replace("~0", "~")).toList();
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

    /**
     * Where the validator finds a schema: its own pointer in the contract, so that a keyword beside a {@code $ref} applies
     * too; the validator resolves the reference.
     */
    private static String schemaLocation(Located schema) {
        return DOCUMENT + schema.pointer();
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

    /** The contract's text, as committed, from the test class path. */
    static String text() {
        return text(RESOURCE);
    }

    /** The text of a resource on the test class path, such as a fixture contract. */
    static String text(String resource) {
        try (InputStream in = Contract.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the test class path.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A contract document read from YAML text with the default YAMLFactory, as the contract itself is read. */
    static JsonNode parse(String yaml) {
        return YAMLMapper.builder().build().readTree(yaml);
    }

    private static Contract read() {
        JsonNode document = parse(text());
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
