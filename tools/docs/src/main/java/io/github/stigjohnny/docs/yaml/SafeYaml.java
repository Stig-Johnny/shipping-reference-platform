package io.github.stigjohnny.docs.yaml;

import io.github.stigjohnny.docs.yaml.YamlValue.YBool;
import io.github.stigjohnny.docs.yaml.YamlValue.YList;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import io.github.stigjohnny.docs.yaml.YamlValue.YNull;
import io.github.stigjohnny.docs.yaml.YamlValue.YNumber;
import io.github.stigjohnny.docs.yaml.YamlValue.YString;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.events.AliasEvent;
import org.snakeyaml.engine.v2.events.CollectionEndEvent;
import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.DocumentStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.schema.CoreSchema;
import org.snakeyaml.engine.v2.schema.JsonSchema;
import org.snakeyaml.engine.v2.schema.Schema;

/**
 * The one factory through which this tool reads YAML and JSON, under the conditions of
 * security/reviews/maven--snakeyaml-engine--3.2.md. No other class may use SnakeYAML Engine
 * (DocsReviewConditionsTest).
 *
 * <ul>
 *   <li>Explicit {@link LoadSettings}: duplicate, recursive and non-scalar keys rejected, comments not parsed, no alias
 *       to a collection, a code point limit sized to the largest real input, and the schema named explicitly (Core for
 *       YAML, JSON for JSON; YAML 1.2 is a superset of JSON). No environment configuration and no tag constructors.
 *   <li>Single documents only. The event stream is read first, without recursion, and rejects a second document,
 *       nesting deeper than {@link #MAX_NESTING} and any explicit tag, so deep input fails before the recursive composer
 *       and constructor see it.
 *   <li>The composed nodes are inspected: an unresolved (explicit) tag or a {@code <<} merge key fails. The composer
 *       flattens a plain {@code <<} away, so the event pass rejects it first.
 *   <li>Any exception or error from the parser, a {@link StackOverflowError} included, becomes a
 *       {@link YamlInputException} that names the source.
 * </ul>
 */
public final class SafeYaml {

    /**
     * Code points per document: about three times the largest real input (security/allowlist.json, 368 KB on
     * 2026-11-06) and a third of the library's 3 MB default.
     */
    public static final int CODE_POINT_LIMIT = 1_048_576;

    /** Deepest nesting of mappings and sequences that any input may have (the API contract, the deepest, has 14). */
    public static final int MAX_NESTING = 64;

    private static final String MERGE_KEY = "<<";

    private SafeYaml() {
    }

    /** Reads one YAML 1.2 document with the Core schema. */
    public static YamlValue yaml(String text, String source) {
        return load(text, source, yamlSettings());
    }

    /** Reads one JSON document with YAML 1.2's JSON schema (YAML 1.2 is a superset of JSON). */
    public static YamlValue json(String text, String source) {
        return load(text, source, jsonSettings());
    }

    static LoadSettings yamlSettings() {
        return settings(new CoreSchema());
    }

    static LoadSettings jsonSettings() {
        return settings(new JsonSchema());
    }

    private static LoadSettings settings(Schema schema) {
        return LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .setAllowRecursiveKeys(false)
                .setAllowNonScalarKeys(false)
                .setParseComments(false)
                .setMaxAliasesForCollections(0)
                .setCodePointLimit(CODE_POINT_LIMIT)
                .setSchema(schema)
                .build();
    }

    private static YamlValue load(String text, String source, LoadSettings settings) {
        try {
            checkEvents(new Parse(settings).parseString(text));
            Node root = new Compose(settings).composeString(text)
                    .orElseThrow(() -> new YamlInputException("the document is empty"));
            checkNodes(root);
            return toValue(new Load(settings).loadFromString(text), "(root)");
        } catch (YamlInputException e) {
            throw new YamlInputException(source + ": " + e.getMessage(), e);
        } catch (YamlEngineException | IllegalArgumentException | IllegalStateException e) {
            throw new YamlInputException(source + ": " + e.getClass().getSimpleName() + ": " + oneLine(e), e);
        } catch (StackOverflowError e) {
            throw new YamlInputException(source + ": the input is nested too deeply to read", e);
        }
    }

    /** Streams the parser's events (no recursion) and rejects what the composer must never see. */
    private static void checkEvents(Iterable<Event> events) {
        int documents = 0;
        int depth = 0;
        for (Event event : events) {
            switch (event) {
                case DocumentStartEvent ignored -> {
                    if (++documents > 1) {
                        throw new YamlInputException("more than one document; only single documents are read");
                    }
                }
                case CollectionStartEvent start -> {
                    rejectExplicitTag(start.getTag());
                    if (++depth > MAX_NESTING) {
                        throw new YamlInputException("nested deeper than " + MAX_NESTING + " levels");
                    }
                }
                case CollectionEndEvent ignored -> depth--;
                case ScalarEvent scalar -> {
                    rejectExplicitTag(scalar.getTag());
                    // The composer resolves a plain << to a merge and flattens it away, so it is caught here.
                    if (scalar.isPlain() && MERGE_KEY.equals(scalar.getValue())) {
                        throw new YamlInputException("merge key '<<' is not allowed");
                    }
                }
                case AliasEvent ignored -> {
                    // Aliases to scalars are harmless; the composer rejects any alias to a collection (limit 0).
                }
                default -> {
                    // Stream and document boundaries carry no data.
                }
            }
        }
    }

    private static void rejectExplicitTag(Optional<String> tag) {
        if (tag.isPresent() && !"!".equals(tag.get())) {
            throw new YamlInputException("explicit tag '" + tag.get() + "' is not allowed");
        }
    }

    /** Walks the composed nodes without recursion: explicit tags and merge keys fail. */
    private static void checkNodes(Node root) {
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (!node.isResolved()) {
                throw new YamlInputException("explicit tag '" + node.getTag().getValue() + "' is not allowed");
            }
            switch (node) {
                case MappingNode mapping -> {
                    for (NodeTuple tuple : mapping.getValue()) {
                        if (tuple.getKeyNode() instanceof ScalarNode key && MERGE_KEY.equals(key.getValue())) {
                            throw new YamlInputException("merge key '<<' is not allowed");
                        }
                        pending.push(tuple.getKeyNode());
                        pending.push(tuple.getValueNode());
                    }
                }
                case SequenceNode sequence -> sequence.getValue().forEach(pending::push);
                default -> {
                    // A scalar has no children.
                }
            }
        }
    }

    private static YamlValue toValue(@Nullable Object value, String where) {
        return switch (value) {
            case null -> new YNull();
            case String text -> new YString(text);
            case Boolean bool -> new YBool(bool);
            case Number number -> new YNumber(number.toString());
            case Map<?, ?> map -> {
                Map<String, YamlValue> entries = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw new YamlInputException("a key in " + where + " is not a string");
                    }
                    entries.put(key, toValue(entry.getValue(), key));
                }
                yield new YMap(entries);
            }
            case List<?> list -> {
                List<YamlValue> items = new ArrayList<>();
                for (Object item : list) {
                    items.add(toValue(item, where));
                }
                yield new YList(items);
            }
            default -> throw new YamlInputException("unsupported value type " + value.getClass().getSimpleName()
                    + " in " + where);
        };
    }

    private static String oneLine(Throwable e) {
        String message = e.getMessage();
        return message == null ? "" : message.replace('\n', ' ').strip();
    }
}
