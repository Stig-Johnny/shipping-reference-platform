package io.github.stigjohnny.c4contractkit.model;

import com.structurizr.Workspace;
import com.structurizr.dsl.Features;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.dsl.StructurizrDslParserException;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.DeploymentNode;
import com.structurizr.model.Element;
import com.structurizr.model.InfrastructureNode;
import com.structurizr.model.ModelItem;
import com.structurizr.model.Relationship;
import com.structurizr.view.ImageView;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The one place that creates a {@link StructurizrDslParser} (security/reviews/maven--structurizr-dsl--6.2.4.md).
 *
 * <p>Every parser it creates has all ten Structurizr features switched off before it parses: no environment variables,
 * no file system access beyond the model file itself, no HTTP or HTTPS, no {@code !include}, no plugins, no scripts, no
 * component finder, no documentation and no decision import. It never allows a URL on the parser's HTTP client and
 * never uses the deprecated restricted mode. What the parser accepts but the kit must not (image views and themes,
 * which renderers fetch later, and unresolved {@code ${NAME}} references) is rejected after parsing.
 */
final class RestrictedDslParser {

    private static final Pattern UNRESOLVED = Pattern.compile("\\$\\{([^}]*)}");

    static final List<String> DISABLED_FEATURES = List.of(Features.ENVIRONMENT, Features.FILE_SYSTEM, Features.HTTP,
            Features.HTTPS, Features.INCLUDE, Features.PLUGINS, Features.SCRIPTS, Features.COMPONENT_FINDER,
            Features.DOCUMENTATION, Features.DECISIONS);

    private RestrictedDslParser() {
    }

    static StructurizrDslParser newParser() {
        StructurizrDslParser parser = new StructurizrDslParser();
        for (String feature : DISABLED_FEATURES) {
            parser.getFeatures().disable(feature);
        }
        return parser;
    }

    /** Parses one model file; problems name the file by {@code displayPath}, never by its absolute path. */
    static ParsedModel parse(Path dsl, String displayPath) throws InvalidModelException {
        if (!Files.isRegularFile(dsl)) {
            throw new InvalidModelException(List.of(displayPath + ": the model file does not exist"));
        }
        StructurizrDslParser parser = newParser();
        File file = dsl.toFile();
        try {
            parser.parse(file);
        } catch (StructurizrDslParserException e) {
            String message = Objects.requireNonNullElse(e.getMessage(), "the DSL could not be parsed");
            throw new InvalidModelException(List.of(message.replace(file.getAbsolutePath(), displayPath)));
        }
        Workspace workspace = parser.getWorkspace();
        if (workspace == null) {
            throw new InvalidModelException(List.of(displayPath + ": the file defines no workspace"));
        }
        rejectWhatTheParserKept(workspace, new ElementNames(parser.getIdentifiersRegister()));
        return new ParsedModel(workspace, parser.getIdentifiersRegister());
    }

    /**
     * Rejects what the parser accepts but the kit must not: image views and themes, which renderers fetch, and
     * {@code ${NAME}} references the parser left as text because no constant defines them and environment
     * variables are off. A rejected construct is a model problem, never a silent pass.
     */
    private static void rejectWhatTheParserKept(Workspace workspace, ElementNames names) throws InvalidModelException {
        Problems problems = new Problems();
        for (ImageView view : workspace.getViews().getImageViews()) {
            problems.add("views", "image view '" + view.getKey()
                    + "' is not supported: image views make renderers fetch content");
        }
        for (String theme : workspace.getViews().getConfiguration().getThemes()) {
            problems.add("views", "theme '" + theme + "' is not supported: themes are fetched by renderers");
        }
        for (Element element : workspace.getModel().getElements()) {
            rejectUnresolvedReferences(names.of(element), texts(element), problems);
        }
        for (Relationship relationship : workspace.getModel().getRelationships()) {
            rejectUnresolvedReferences(names.of(relationship), texts(relationship), problems);
        }
        problems.throwIfAny();
    }

    private static void rejectUnresolvedReferences(String name, Stream<String> texts, Problems problems) {
        texts.filter(Objects::nonNull).forEach(text -> {
            Matcher reference = UNRESOLVED.matcher(text);
            while (reference.find()) {
                problems.add(name, reference.group() + " is not substituted: environment variables are not read, "
                        + "and the model defines no constant " + reference.group(1));
            }
        });
    }

    private static Stream<String> texts(Element element) {
        String technology = switch (element) {
            case Container container -> container.getTechnology();
            case Component component -> component.getTechnology();
            case DeploymentNode node -> node.getTechnology();
            case InfrastructureNode node -> node.getTechnology();
            default -> "";
        };
        return Stream.concat(Stream.of(element.getName(), element.getDescription(), technology),
                texts((ModelItem) element));
    }

    private static Stream<String> texts(Relationship relationship) {
        return Stream.concat(Stream.of(relationship.getDescription(), relationship.getTechnology()),
                texts((ModelItem) relationship));
    }

    private static Stream<String> texts(ModelItem item) {
        return Stream.concat(Stream.of(item.getTags(), item.getUrl()), item.getProperties().entrySet().stream()
                .flatMap(property -> Stream.of(property.getKey(), property.getValue())));
    }
}
