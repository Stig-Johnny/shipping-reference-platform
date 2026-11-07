package io.github.stigjohnny.shipping.scenarios.support;

import io.github.stigjohnny.shipping.scenarios.support.ScenarioModel.Element;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioModel.C4Type;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioModel.View;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioModel.ViewStep;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The machine-readable steps of one executable scenario, {@code architecture/scenarios/<view slug>.json}: one entry per
 * step occurrence of the dynamic view, keyed by its order, never matched by its prose.
 *
 * <p>Reading validates the file completely. The JSON is strict (no unknown or duplicate members); every step of the
 * view has exactly one entry and every entry one step, with the same endpoints; each kind's endpoints have the right C4
 * types; a module API call names an interface that exists in the callee's {@code api} package and declares the method;
 * an event is a record in the publisher's {@code api} package; ordering constraints name earlier steps only. A problem
 * fails the read with the file, the step and the rule.
 */
public record Sidecar(String path, String view, List<Step> steps) {

    public static final String SCHEMA = "shipping-scenarios/sidecar/v1";

    /** What a step is; it decides which endpoints it may have and which observer can see it. */
    public enum StepKind {
        /** A person acts on a container: no code runs for the person. */
        USER_ACTION,
        /** An HTTP request entering the API and the API's response, whose status is part of the operation. */
        HTTP_EXCHANGE,
        /** A call from one module through an interface in another module's {@code api} package. */
        MODULE_API_CALL,
        /** A module publishes an event; the arrow points to the database, which holds the publication registry. */
        EVENT_PUBLICATION,
        /** A listener in one module handles another module's event; the arrow shows the code dependency. */
        LISTENER_EXECUTION,
        /** A module reads its schema. */
        DATABASE_READ,
        /** A module writes its schema. */
        DATABASE_WRITE,
        /** A module uses an external system that v0.1 simulates inside the module: no boundary is crossed. */
        SIMULATED_EXTERNAL
    }

    /** When a step happens relative to the scenario's stimulus. */
    public enum Phase {
        /** Inside the stimulus, on its thread, before it returns. */
        SYNCHRONOUS,
        /** After the publishing transaction commits, possibly on another thread. */
        AFTER_COMMIT
    }

    /** How often a step happens in one run: at least {@code min}, at most {@code max}. */
    public record Cardinality(int min, int max) {

        private static final Pattern FORM = Pattern.compile("(0|[1-9][0-9]?)(?:\\.\\.(\\*|[1-9][0-9]?))?");

        static Optional<Cardinality> parse(String text) {
            var matcher = FORM.matcher(text);
            if (!matcher.matches()) {
                return Optional.empty();
            }
            int min = Integer.parseInt(matcher.group(1));
            String upper = matcher.group(2);
            int max = upper == null ? min : "*".equals(upper) ? Integer.MAX_VALUE : Integer.parseInt(upper);
            return max < min || max == 0 ? Optional.empty() : Optional.of(new Cardinality(min, max));
        }

        public String text() {
            if (min == max) {
                return Integer.toString(min);
            }
            return min + ".." + (max == Integer.MAX_VALUE ? "*" : Integer.toString(max));
        }
    }

    /** One step occurrence, with the view's description of it. */
    public record Step(int order, String id, StepKind kind, String from, String to, Map<String, String> operation,
            Phase phase, Cardinality cardinality, List<String> after, String description) {

        public Step {
            operation = Map.copyOf(operation);
            after = List.copyOf(after);
        }

        /** The operation in one line, the way reports show it next to the description. */
        public String operationText() {
            return switch (kind) {
                case USER_ACTION, SIMULATED_EXTERNAL -> "";
                case HTTP_EXCHANGE -> operation.get("method") + " " + operation.get("path") + " ("
                        + operation.get("operationId") + ") -> " + operation.get("status");
                case MODULE_API_CALL -> operation.get("interface") + "#" + operation.get("method");
                case EVENT_PUBLICATION, LISTENER_EXECUTION -> operation.get("event");
                case DATABASE_READ, DATABASE_WRITE -> operation.get("statement") + " in schema "
                        + operation.get("schema");
            };
        }
    }

    private static final Pattern STEP_ID = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    private static final Pattern JAVA_NAME = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*\\.[A-Z][A-Za-z0-9]*");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][A-Za-z0-9]*");
    private static final Pattern API_PATH = Pattern.compile("/api/v1(/[a-z0-9{}-]+)+");
    private static final Set<String> TOP = Set.of("schema", "view", "steps");
    private static final Set<String> STEP = Set.of("order", "id", "kind", "from", "to", "operation", "phase",
            "cardinality", "after");
    private static final Map<StepKind, Set<String>> OPERATIONS = Map.of(
            StepKind.USER_ACTION, Set.of(),
            StepKind.SIMULATED_EXTERNAL, Set.of(),
            StepKind.HTTP_EXCHANGE, Set.of("method", "path", "operationId", "status"),
            StepKind.MODULE_API_CALL, Set.of("interface", "method"),
            StepKind.EVENT_PUBLICATION, Set.of("event"),
            StepKind.LISTENER_EXECUTION, Set.of("event"),
            StepKind.DATABASE_READ, Set.of("statement", "schema"),
            StepKind.DATABASE_WRITE, Set.of("statement", "schema"));
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public Sidecar {
        steps = List.copyOf(steps);
    }

    /** The step with this id. */
    public Optional<Step> step(String id) {
        return steps.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /** The file name a view's sidecar must have: the view key in kebab case, as the docs tool names its pages. */
    public static String fileName(String viewKey) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < viewKey.length(); i++) {
            char c = viewKey.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(viewKey.charAt(i - 1))) {
                out.append('-');
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString().toLowerCase(Locale.ROOT) + ".json";
    }

    /** Reads and validates a sidecar against the model's dynamic view and the compiled classes. */
    public static Sidecar read(Path file, String displayPath, ScenarioModel model) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        JsonNode root;
        try {
            root = JSON.readTree(text);
        } catch (JacksonException e) {
            throw new IllegalStateException(displayPath + ": not strict JSON: " + e.getOriginalMessage(), e);
        }
        Problems problems = new Problems(displayPath);
        members(root, TOP, "the file", problems);
        problems.failIfAny();
        if (!SCHEMA.equals(string(root, "schema", "the file", problems))) {
            problems.add("the file", "schema must be " + SCHEMA);
        }
        String viewKey = string(root, "view", "the file", problems);
        problems.failIfAny();
        View view = model.view(viewKey).orElseThrow(() -> new IllegalStateException(displayPath + ": the model has no "
                + "dynamic view " + viewKey));
        if (!file.getFileName().toString().equals(fileName(viewKey))) {
            problems.add("the file", "the sidecar of view " + viewKey + " must be named " + fileName(viewKey));
        }
        JsonNode array = root.get("steps");
        if (array == null || !array.isArray()) {
            problems.add("the file", "steps must be an array");
            problems.failIfAny();
        }
        List<Step> steps = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int position = 0;
        for (JsonNode node : array.values()) {
            position++;
            Optional<Step> step = step(node, position, view, model, ids, problems);
            step.ifPresent(steps::add);
        }
        if (position != view.steps().size()) {
            problems.add("the file", "the view " + viewKey + " has " + view.steps().size() + " steps and the sidecar "
                    + position + " entries; each step needs exactly one entry and each entry a step");
        }
        problems.failIfAny();
        return new Sidecar(displayPath, viewKey, steps);
    }

    private static Optional<Step> step(JsonNode node, int position, View view, ScenarioModel model, Set<String> ids,
            Problems problems) {
        String where = "entry " + position;
        members(node, STEP, where, problems);
        if (node.get("order") == null || !node.get("order").isInt() || node.get("order").intValue() != position) {
            problems.add(where, "order must be " + position + ": entries follow the view's steps in order");
            return Optional.empty();
        }
        String id = string(node, "id", where, problems);
        where = "step " + position + " (" + id + ")";
        if (!STEP_ID.matcher(id).matches() || !ids.add(id)) {
            problems.add(where, "id must be a new kebab-case identifier");
        }
        StepKind kind = enumValue(StepKind.class, string(node, "kind", where, problems), "kind", where, problems);
        Phase phase = enumValue(Phase.class, string(node, "phase", where, problems), "phase", where, problems);
        Optional<Cardinality> cardinality = Cardinality.parse(string(node, "cardinality", where, problems));
        if (cardinality.isEmpty()) {
            problems.add(where, "cardinality must be n, n..m or n..* with n <= m and m > 0");
        }
        String from = string(node, "from", where, problems);
        String to = string(node, "to", where, problems);
        Optional<ViewStep> drawn = view.steps().stream().filter(s -> s.order() == position).findFirst();
        if (drawn.isEmpty()) {
            problems.add(where, "the view has no step " + position);
            return Optional.empty();
        }
        if (!drawn.get().from().equals(from) || !drawn.get().to().equals(to)) {
            problems.add(where, "the endpoints are " + from + " -> " + to + ", but step " + position + " of the view "
                    + "is " + drawn.get().from() + " -> " + drawn.get().to() + " (line " + drawn.get().line() + ")");
        }
        List<String> after = new ArrayList<>();
        JsonNode afterNode = node.get("after");
        if (afterNode == null || !afterNode.isArray()) {
            problems.add(where, "after must be an array of earlier step ids");
        } else {
            for (JsonNode earlier : afterNode.values()) {
                if (!earlier.isString() || !ids.contains(earlier.stringValue()) || earlier.stringValue().equals(id)
                        || after.contains(earlier.stringValue())) {
                    problems.add(where, "after may name only earlier steps, each once");
                } else {
                    after.add(earlier.stringValue());
                }
            }
        }
        if (kind == null || phase == null || cardinality.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> operation = operation(node.get("operation"), kind, where, problems);
        endpoints(kind, from, to, model, where, problems);
        semantics(kind, from, to, operation, model, where, problems);
        return Optional.of(new Step(position, id, kind, from, to, operation, phase, cardinality.get(), after,
                drawn.get().description()));
    }

    private static Map<String, String> operation(JsonNode node, StepKind kind, String where, Problems problems) {
        Set<String> keys = OPERATIONS.get(kind);
        if (keys.isEmpty()) {
            if (node != null) {
                problems.add(where, kind + " has no operation: no code runs for it");
            }
            return Map.of();
        }
        if (node == null || !node.isObject()) {
            problems.add(where, kind + " needs an operation with " + keys);
            return Map.of();
        }
        members(node, keys, where + " operation", problems);
        Map<String, String> operation = new LinkedHashMap<>();
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && "status".equals(key) && value.isInt()) {
                operation.put(key, Integer.toString(value.intValue()));
            } else if (value != null && value.isString() && !"status".equals(key)) {
                operation.put(key, value.stringValue());
            } else {
                problems.add(where, "operation." + key + " is missing or has the wrong type");
            }
        }
        return operation;
    }

    /** Each kind's endpoints, by their C4 type in the model. */
    private static void endpoints(StepKind kind, String from, String to, ScenarioModel model, String where,
            Problems problems) {
        Optional<Element> source = model.element(from);
        Optional<Element> target = model.element(to);
        if (source.isEmpty() || target.isEmpty()) {
            problems.add(where, "both endpoints must be elements of the model");
            return;
        }
        boolean fromModule = model.module(from).isPresent();
        boolean toModule = model.module(to).isPresent();
        boolean toDatabase = target.get().type() == C4Type.CONTAINER && target.get().tagged("Database");
        boolean ok = switch (kind) {
            case USER_ACTION -> source.get().type() == C4Type.PERSON && !toModule;
            case HTTP_EXCHANGE -> !fromModule && source.get().type() != C4Type.COMPONENT && toModule;
            case MODULE_API_CALL, LISTENER_EXECUTION -> fromModule && toModule && !from.equals(to);
            case EVENT_PUBLICATION, DATABASE_READ, DATABASE_WRITE -> fromModule && toDatabase;
            case SIMULATED_EXTERNAL -> fromModule && target.get().type() == C4Type.SOFTWARE_SYSTEM
                    && target.get().tagged("External") && target.get().tagged("Simulated");
        };
        if (!ok) {
            problems.add(where, kind + " cannot go from " + from + " to " + to + ": " + switch (kind) {
                case USER_ACTION -> "it goes from a person to a container";
                case HTTP_EXCHANGE -> "it goes from a client to a module";
                case MODULE_API_CALL, LISTENER_EXECUTION -> "it goes from one module to another";
                case EVENT_PUBLICATION, DATABASE_READ, DATABASE_WRITE -> "it goes from a module to the database";
                case SIMULATED_EXTERNAL -> "it goes from a module to a simulated external system";
            });
        }
    }

    /** What an operation names must exist: the interface and its method, the event, the statement. */
    private static void semantics(StepKind kind, String from, String to, Map<String, String> operation,
            ScenarioModel model, String where, Problems problems) {
        switch (kind) {
            case MODULE_API_CALL -> {
                Optional<Class<?>> type = type(operation.getOrDefault("interface", ""));
                boolean ok = type.isPresent() && type.get().isInterface() && !type.get().isSealed()
                        && model.apiModuleOf(type.get()).filter(to::equals).isPresent()
                        && Arrays.stream(type.get().getMethods()).map(Method::getName)
                                .anyMatch(operation.getOrDefault("method", "")::equals);
                if (!ok) {
                    problems.add(where, "operation must name an interface in the api package of " + to + ", and one "
                            + "of its methods");
                }
            }
            case EVENT_PUBLICATION, LISTENER_EXECUTION -> {
                String publisher = kind == StepKind.EVENT_PUBLICATION ? from : to;
                Optional<Class<?>> type = type(operation.getOrDefault("event", ""));
                if (type.isEmpty() || !type.get().isRecord()
                        || model.apiModuleOf(type.get()).filter(publisher::equals).isEmpty()) {
                    problems.add(where, "operation.event must be a record in the api package of " + publisher);
                }
            }
            case HTTP_EXCHANGE -> {
                boolean ok = Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(operation.get("method"))
                        && API_PATH.matcher(operation.getOrDefault("path", "")).matches()
                        && IDENTIFIER.matcher(operation.getOrDefault("operationId", "")).matches()
                        && operation.getOrDefault("status", "").matches("[1-5][0-9][0-9]");
                if (!ok) {
                    problems.add(where, "operation needs an HTTP method, a path under /api/v1, an operationId and a "
                            + "status");
                }
            }
            case DATABASE_READ, DATABASE_WRITE -> {
                Set<String> statements = kind == StepKind.DATABASE_READ ? Set.of("SELECT")
                        : Set.of("INSERT", "UPDATE", "DELETE");
                if (!statements.contains(operation.get("statement"))
                        || !IDENTIFIER.matcher(operation.getOrDefault("schema", "")).matches()) {
                    problems.add(where, "operation needs one of " + statements + " and a schema");
                }
            }
            case USER_ACTION, SIMULATED_EXTERNAL -> {
                // No operation: nothing to name.
            }
        }
    }

    private static Optional<Class<?>> type(String name) {
        if (!JAVA_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(name, false, Sidecar.class.getClassLoader()));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    private static void members(JsonNode node, Set<String> allowed, String where, Problems problems) {
        if (node == null || !node.isObject()) {
            problems.add(where, "must be an object");
            return;
        }
        for (String name : node.propertyNames()) {
            if (!allowed.contains(name)) {
                problems.add(where, "unknown member '" + name + "'");
            }
        }
    }

    private static String string(JsonNode node, String name, String where, Problems problems) {
        JsonNode value = node == null ? null : node.get(name);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            problems.add(where, name + " must be a non-empty string");
            return "";
        }
        return value.stringValue();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String text, String name, String where,
            Problems problems) {
        for (E value : type.getEnumConstants()) {
            if (value.name().equals(text)) {
                return value;
            }
        }
        problems.add(where, name + " must be one of " + Arrays.toString(type.getEnumConstants()));
        return null;
    }

    /** Every problem of one file, reported together. */
    private static final class Problems {

        private final String path;
        private final List<String> messages = new ArrayList<>();

        Problems(String path) {
            this.path = path;
        }

        void add(String where, String message) {
            messages.add(where + ": " + message);
        }

        void failIfAny() {
            if (!messages.isEmpty()) {
                throw new IllegalStateException(path + ":\n  " + String.join("\n  ", messages));
            }
        }
    }
}
