package io.github.stigjohnny.shipping.scenarios.support;

import io.github.stigjohnny.c4contractkit.api.ComponentResult;
import io.github.stigjohnny.c4contractkit.api.ComponentStatus;
import io.github.stigjohnny.c4contractkit.api.Report;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * What the scenario checks need from architecture/workspace.dsl: the elements with their type, tags and properties,
 * the modules (the checked container's components), and the steps of the dynamic views.
 *
 * <p>Structurizr's review allows its parser in c4-contract-kit and the docs tool only, so this class reads the text
 * itself, and only a restricted part of it: element declarations ({@code id = person|softwareSystem|container|component
 * "name" ...}) with their {@code tags} and {@code properties}, and dynamic views whose body holds nothing but
 * {@code from -> to "description" ["technology"]} steps and {@code autoLayout}. Anything else in a dynamic view
 * (parallel sequences, explicit step numbers, steps without a description) is refused, so no step can be numbered
 * differently from Structurizr's own numbering. The rest of the file is tokenised to check its quoting and otherwise
 * ignored. Every time, the modules read here must equal the components that the kit's restricted Structurizr parse
 * reports, by contract.id, name, status and package roots.
 */
public final class ScenarioModel {

    /** An element's C4 type. */
    public enum C4Type {
        PERSON, SOFTWARE_SYSTEM, CONTAINER, COMPONENT
    }

    /** An element as declared, with its DSL identifier. */
    public record Element(String id, C4Type type, String name, List<String> tags, Map<String, String> properties,
            String parent) {

        public Element {
            tags = List.copyOf(tags);
            properties = Map.copyOf(properties);
        }

        public boolean tagged(String tag) {
            return tags.contains(tag);
        }
    }

    /** A step of a dynamic view, numbered from 1 in the order of the file, as Structurizr numbers it. */
    public record ViewStep(int order, String from, String to, String description, int line) {
    }

    /** A dynamic view. */
    public record View(String key, String scope, String description, List<ViewStep> steps, int line) {

        public View {
            steps = List.copyOf(steps);
        }
    }

    /** A module: a component of the checked container, with its status and package roots from the kit. */
    public record Module(String id, boolean built, List<String> packages) {

        public Module {
            packages = List.copyOf(packages);
        }
    }

    private static final Map<String, C4Type> TYPES = Map.of("person", C4Type.PERSON, "softwareSystem",
            C4Type.SOFTWARE_SYSTEM, "container", C4Type.CONTAINER, "component", C4Type.COMPONENT);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
    /** The one autoLayout form the reader accepts: Structurizr's direction and separations, nothing else. */
    private static final Pattern AUTO_LAYOUT =
            Pattern.compile("autoLayout( (tb|bt|lr|rl)( [0-9]{1,4}( [0-9]{1,4})?)?)?");
    private static final String ROOT = "contract.root";

    private final String path;
    private final Map<String, Element> elements;
    private final Map<String, View> views;
    private final Map<String, Module> modules;
    private final String root;

    private ScenarioModel(String path, Map<String, Element> elements, Map<String, View> views,
            Map<String, Module> modules, String root) {
        this.path = path;
        this.elements = Map.copyOf(elements);
        this.views = Map.copyOf(views);
        this.modules = Map.copyOf(modules);
        this.root = root;
    }

    /** Reads the model file and cross-checks its modules with the kit's report on the same file. */
    public static ScenarioModel read(Path dsl, Report kit) {
        String display = kit.modelPath();
        List<String> lines;
        try {
            lines = Files.readAllLines(dsl, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Reader reader = new Reader(display);
        reader.read(lines);
        List<Element> containers = reader.elements.values().stream().filter(e -> e.properties().containsKey(ROOT))
                .toList();
        if (containers.size() != 1) {
            throw reader.problem(0, "exactly one container must carry " + ROOT + ", not " + containers.size());
        }
        Element api = containers.getFirst();
        Map<String, Module> modules = new TreeMap<>();
        for (Element component : reader.elements.values()) {
            if (component.type() == C4Type.COMPONENT && component.parent().equals(api.id())) {
                modules.put(component.id(), module(component, display));
            }
        }
        crossCheck(modules, reader.elements, kit, display);
        return new ScenarioModel(display, reader.elements, reader.views, modules, api.properties().get(ROOT));
    }

    private static Module module(Element component, String display) {
        String id = component.properties().get("contract.id");
        if (!component.id().equals(id)) {
            throw new IllegalStateException(display + ": component " + component.id() + " has contract.id " + id
                    + "; the scenario checks need the DSL identifier and the contract.id to be equal");
        }
        List<String> packages = Pattern.compile(",").splitAsStream(component.properties().getOrDefault(
                "contract.packages", "")).map(String::strip).filter(p -> !p.isEmpty()).sorted().toList();
        return new Module(id, "built".equals(component.properties().get("contract.status")), packages);
    }

    private static void crossCheck(Map<String, Module> modules, Map<String, Element> elements, Report kit,
            String display) {
        Map<String, ComponentResult> components = new TreeMap<>();
        kit.components().forEach(c -> components.put(c.id(), c));
        if (!components.keySet().equals(modules.keySet())) {
            throw new IllegalStateException(display + ": the scenario reader found the modules " + modules.keySet()
                    + ", c4-contract-kit's parse found " + components.keySet());
        }
        for (ComponentResult component : components.values()) {
            Module module = modules.get(component.id());
            boolean same = component.name().equals(elements.get(component.id()).name())
                    && module.built() == (component.status() == ComponentStatus.BUILT)
                    && module.packages().equals(component.packages().stream().sorted().toList());
            if (!same) {
                throw new IllegalStateException(display + ": the scenario reader and c4-contract-kit disagree about "
                        + "module " + component.id());
            }
        }
    }

    public String path() {
        return path;
    }

    /** The checked container's {@code contract.root}. */
    public String root() {
        return root;
    }

    public Optional<Element> element(String id) {
        return Optional.ofNullable(elements.get(id));
    }

    public Optional<View> view(String key) {
        return Optional.ofNullable(views.get(key));
    }

    /** Every dynamic view, by key. */
    public Map<String, View> views() {
        return new TreeMap<>(views);
    }

    public Optional<Module> module(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    /** The module whose package roots hold the class, if any. */
    public Optional<String> moduleOf(String className) {
        int dot = className.lastIndexOf('.');
        String pkg = dot < 0 ? "" : className.substring(0, dot);
        for (Module module : modules.values()) {
            for (String packageRoot : module.packages()) {
                if (pkg.equals(packageRoot) || pkg.startsWith(packageRoot + ".")) {
                    return Optional.of(module.id());
                }
            }
        }
        return Optional.empty();
    }

    /** The module whose {@code api} package declares the type, if the type is in one. */
    public Optional<String> apiModuleOf(Class<?> type) {
        String pkg = type.getPackageName();
        for (Module module : modules.values()) {
            for (String packageRoot : module.packages()) {
                if (pkg.equals(packageRoot + ".api")) {
                    return Optional.of(module.id());
                }
            }
        }
        return Optional.empty();
    }

    /** Reads the restricted subset line by line. */
    private static final class Reader {

        private record Token(String text, boolean quoted) {
        }

        private record Block(String kind, String element) {
        }

        /** The kind of block an element declaration opens. */
        private static final String DECLARED = "declared element";

        private final String display;
        private final Map<String, Element> elements = new LinkedHashMap<>();
        private final Map<String, View> views = new LinkedHashMap<>();
        private final Deque<Block> blocks = new ArrayDeque<>();
        private final List<ViewStep> dynamicSteps = new ArrayList<>();
        private boolean flat;
        private View dynamic;

        Reader(String display) {
            this.display = display;
        }

        /**
         * Reads the lines with Structurizr's comment rules where they are unambiguous and refuses every other form. A
         * line comment is a line that starts with {@code //} or {@code #}. A block comment opens on a line that is
         * exactly {@code /*} and closes on a line that is exactly {@code *}{@code /}; a line that is one block comment,
         * {@code /*} text {@code *}{@code /}, is skipped as Structurizr skips it. Any other line that holds a
         * comment marker is refused, also inside a block comment and inside a string: there Structurizr's lexer and a
         * simpler one could disagree about where the comment ends.
         */
        void read(List<String> lines) {
            boolean comment = false;
            for (int i = 0; i < lines.size(); i++) {
                int number = i + 1;
                String line = lines.get(i).strip();
                boolean markers = line.contains("/*") || line.contains("*/");
                if (comment) {
                    if (line.equals("*/")) {
                        comment = false;
                    } else if (markers) {
                        throw problem(number, "inside a block comment, only a line that is exactly '*/' may hold a "
                                + "comment marker; the scenario reader refuses this line");
                    }
                    continue;
                }
                if (line.startsWith("//") || line.startsWith("#")) {
                    continue;
                }
                if (line.equals("/*")) {
                    comment = true;
                    continue;
                }
                if (markers) {
                    String inner = line.length() >= 4 ? line.substring(2, line.length() - 2) : "";
                    if (line.startsWith("/*") && line.endsWith("*/") && !inner.contains("/*") && !inner.contains("*/")) {
                        continue;
                    }
                    throw problem(number, "a block comment marker shares this line with other text; the scenario "
                            + "reader accepts only '/*' and '*/' on lines of their own, or a line that is one comment");
                }
                List<Token> tokens = tokens(line, number);
                if (!tokens.isEmpty()) {
                    statement(tokens, number);
                }
            }
            if (!flat) {
                throw problem(0, "the scenario reader needs '!identifiers flat'");
            }
            if (!blocks.isEmpty() || dynamic != null) {
                throw problem(lines.size(), "a block is not closed");
            }
        }

        private void statement(List<Token> tokens, int number) {
            Token first = tokens.getFirst();
            boolean opens = tokens.getLast().text().equals("{") && !tokens.getLast().quoted();
            if (dynamic != null) {
                dynamicStatement(tokens, number);
                return;
            }
            if (first.text().equals("}") && !first.quoted()) {
                if (tokens.size() != 1 || blocks.isEmpty()) {
                    throw problem(number, "a '}' must stand alone and close a block");
                }
                blocks.pop();
                return;
            }
            if (first.text().equals("!identifiers") && tokens.size() == 2) {
                flat = tokens.get(1).text().equals("flat");
            }
            Block enclosing = blocks.peek();
            if (tokens.size() >= 4 && tokens.get(1).text().equals("=") && TYPES.containsKey(tokens.get(2).text())) {
                declare(tokens, number);
            } else if (enclosing != null && enclosing.kind().equals("properties") && tokens.size() == 2
                    && tokens.get(0).quoted() && tokens.get(1).quoted()) {
                property(enclosing, tokens);
            } else if (enclosing != null && enclosing.kind().equals(DECLARED) && first.text().equals("tags")) {
                tags(enclosing.element(), tokens.subList(1, tokens.size()));
            } else if (first.text().equals("dynamic") && opens) {
                startDynamic(tokens, number);
                return;
            }
            if (opens) {
                blocks.push(blockOf(tokens));
            }
        }

        private Block blockOf(List<Token> tokens) {
            if (tokens.size() >= 4 && tokens.get(1).text().equals("=") && TYPES.containsKey(tokens.get(2).text())) {
                return new Block(DECLARED, tokens.getFirst().text());
            }
            if (tokens.getFirst().text().equals("properties")) {
                Block enclosing = blocks.peek();
                return new Block("properties", enclosing == null ? "" : enclosing.element());
            }
            return new Block(tokens.getFirst().text(), "");
        }

        private void declare(List<Token> tokens, int number) {
            String id = tokens.getFirst().text();
            if (!IDENTIFIER.matcher(id).matches() || elements.containsKey(id)) {
                throw problem(number, "'" + id + "' is not a new element identifier");
            }
            C4Type type = TYPES.get(tokens.get(2).text());
            List<String> texts = tokens.subList(3, tokens.size()).stream().filter(Token::quoted).map(Token::text)
                    .toList();
            int tagsAt = type == C4Type.CONTAINER || type == C4Type.COMPONENT ? 3 : 2;
            List<String> tags = new ArrayList<>();
            if (texts.size() > tagsAt) {
                tags.addAll(split(texts.get(tagsAt)));
            }
            String parent = blocks.stream().filter(b -> b.kind().equals(DECLARED)).map(Block::element).findFirst()
                    .orElse("");
            elements.put(id, new Element(id, type, texts.isEmpty() ? id : texts.getFirst(), List.copyOf(tags),
                    new TreeMap<>(), parent));
        }

        private void property(Block enclosing, List<Token> tokens) {
            Element element = elements.get(enclosing.element());
            if (element != null) {
                Map<String, String> properties = new TreeMap<>(element.properties());
                properties.put(tokens.get(0).text(), tokens.get(1).text());
                elements.put(element.id(), new Element(element.id(), element.type(), element.name(), element.tags(),
                        properties, element.parent()));
            }
        }

        private void tags(String id, List<Token> tokens) {
            Element element = elements.get(id);
            Set<String> tags = new LinkedHashSet<>(element.tags());
            tokens.stream().filter(Token::quoted).forEach(t -> tags.addAll(split(t.text())));
            elements.put(id, new Element(id, element.type(), element.name(), List.copyOf(tags), element.properties(),
                    element.parent()));
        }

        private void startDynamic(List<Token> tokens, int number) {
            List<Token> texts = tokens.subList(2, tokens.size() - 1);
            if (tokens.size() < 4 || texts.isEmpty() || texts.size() > 2 || !texts.stream().allMatch(Token::quoted)) {
                throw problem(number, "a dynamic view needs the form: dynamic <scope> \"<key>\" [\"<description>\"] {");
            }
            String key = texts.getFirst().text();
            if (views.containsKey(key)) {
                throw problem(number, "a second dynamic view with the key " + key);
            }
            dynamic = new View(key, tokens.get(1).text(), texts.size() == 2 ? texts.get(1).text() : "", List.of(),
                    number);
            dynamicSteps.clear();
        }

        private void dynamicStatement(List<Token> tokens, int number) {
            String first = tokens.getFirst().text();
            if (first.equals("}") && tokens.size() == 1) {
                views.put(dynamic.key(), new View(dynamic.key(), dynamic.scope(), dynamic.description(),
                        dynamicSteps, dynamic.line()));
                dynamic = null;
            } else if (AUTO_LAYOUT.matcher(String.join(" ", tokens.stream().map(Token::text).toList())).matches()
                    && tokens.stream().noneMatch(Token::quoted)) {
                return;
            } else if (tokens.size() >= 4 && tokens.size() <= 5 && tokens.get(1).text().equals("->")
                    && !tokens.get(0).quoted() && !tokens.get(2).quoted()
                    && tokens.subList(3, tokens.size()).stream().allMatch(Token::quoted)) {
                dynamicSteps.add(new ViewStep(dynamicSteps.size() + 1, first, tokens.get(2).text(),
                        tokens.get(3).text(), number));
            } else {
                throw problem(number, "dynamic view " + dynamic.key() + " may hold only 'from -> to \"description\"' "
                        + "steps and autoLayout; the scenario reader refuses anything else");
            }
        }

        /**
         * Splits a line into tokens: words separated by whitespace and strings in double quotes. A string must stand
         * apart from its neighbours, close on its line and hold no backslash, and a {@code //} after other text is
         * refused: Structurizr reads it as text, not as a comment.
         */
        private List<Token> tokens(String line, int number) {
            List<Token> tokens = new ArrayList<>();
            int i = 0;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                } else if (line.startsWith("//", i)) {
                    throw problem(number, "'//' after other text is not a comment for Structurizr; the scenario "
                            + "reader refuses it");
                } else if (c == '"') {
                    int end = line.indexOf('"', i + 1);
                    String text = end < 0 ? "" : line.substring(i + 1, end);
                    boolean apart = (i == 0 || Character.isWhitespace(line.charAt(i - 1)))
                            && (end < 0 || end + 1 == line.length() || Character.isWhitespace(line.charAt(end + 1)));
                    if (end < 0 || text.indexOf('\\') >= 0 || !apart) {
                        throw problem(number, "the scenario reader accepts only strings that stand apart, close on "
                                + "their line and hold no backslash");
                    }
                    tokens.add(new Token(text, true));
                    i = end + 1;
                } else {
                    int start = i;
                    while (i < line.length() && !Character.isWhitespace(line.charAt(i)) && line.charAt(i) != '"') {
                        i++;
                    }
                    tokens.add(new Token(line.substring(start, i), false));
                }
            }
            return tokens;
        }

        private static List<String> split(String tags) {
            return Pattern.compile(",").splitAsStream(tags).map(String::strip).filter(t -> !t.isEmpty()).toList();
        }

        IllegalStateException problem(int line, String message) {
            return new IllegalStateException(display + (line > 0 ? ":" + line : "") + ": " + message);
        }
    }
}
