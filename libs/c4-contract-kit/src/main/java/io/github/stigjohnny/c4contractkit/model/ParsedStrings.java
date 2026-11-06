package io.github.stigjohnny.c4contractkit.model;

import com.structurizr.PropertyHolder;
import com.structurizr.Workspace;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.CustomElement;
import com.structurizr.model.DeploymentNode;
import com.structurizr.model.Element;
import com.structurizr.model.GroupableElement;
import com.structurizr.model.InfrastructureNode;
import com.structurizr.model.ModelItem;
import com.structurizr.model.Perspective;
import com.structurizr.model.Relationship;
import com.structurizr.view.AbstractStyle;
import com.structurizr.view.Configuration;
import com.structurizr.view.ModelView;
import com.structurizr.view.RelationshipView;
import com.structurizr.view.Terminology;
import com.structurizr.view.View;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The check after parsing that backs up {@link UnresolvedReferences}: a {@code ${NAME}} still in a string of the
 * parsed workspace was not substituted, whatever the source scan concluded. It reads the strings of the workspace and
 * the model, of every element (with its perspectives) and relationship, of every view and the relationships it shows,
 * and of the view configuration: terminology, styles and properties.
 */
final class ParsedStrings {

    /** StructurizrDslParser.STRING_SUBSTITUTION_PATTERN. */
    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([a-zA-Z0-9-_.]+?)}");

    private ParsedStrings() {
    }

    /**
     * One unresolved reference.
     *
     * @param place where it stands: an element or relationship by DSL identifier, {@code view <key>}, {@code views}
     *     for the view configuration, {@code model} or {@code workspace}
     * @param name the name in {@code ${name}}
     */
    record Reference(String place, String name) {
    }

    /** Every unresolved reference in the parsed workspace, once per place and name, sorted. */
    static List<Reference> unresolved(Workspace workspace, ElementNames names) {
        List<Reference> found = new ArrayList<>();
        find("workspace", Stream.concat(Stream.of(workspace.getName(), workspace.getDescription()),
                properties(workspace)), found);
        find("model", properties(workspace.getModel()), found);
        for (Element element : workspace.getModel().getElements()) {
            find(names.of(element), texts(element), found);
        }
        for (Relationship relationship : workspace.getModel().getRelationships()) {
            find(names.of(relationship), texts(relationship), found);
        }
        for (View view : workspace.getViews().getViews()) {
            find("view " + view.getKey(), texts(view), found);
        }
        find("views", texts(workspace.getViews().getConfiguration()), found);
        return found.stream()
                .distinct()
                .sorted(Comparator.comparing(Reference::place).thenComparing(Reference::name))
                .toList();
    }

    /** Structurizr leaves a text it was not given as null. */
    private static void find(String place, Stream<String> texts, List<Reference> found) {
        texts.filter(Objects::nonNull).forEach(text -> {
            Matcher reference = REFERENCE.matcher(text);
            while (reference.find()) {
                found.add(new Reference(place, reference.group(1)));
            }
        });
    }

    private static Stream<String> texts(Element element) {
        String technology = switch (element) {
            case Container container -> container.getTechnology();
            case Component component -> component.getTechnology();
            case DeploymentNode node -> node.getTechnology();
            case InfrastructureNode node -> node.getTechnology();
            case CustomElement custom -> custom.getMetadata();
            default -> "";
        };
        String group = element instanceof GroupableElement groupable ? groupable.getGroup() : "";
        return Stream.concat(Stream.of(element.getName(), element.getDescription(), technology, group),
                texts((ModelItem) element));
    }

    private static Stream<String> texts(Relationship relationship) {
        return Stream.concat(Stream.of(relationship.getDescription(), relationship.getTechnology()),
                texts((ModelItem) relationship));
    }

    private static Stream<String> texts(ModelItem item) {
        return Stream.of(Stream.of(item.getTags(), item.getUrl()), properties(item),
                        item.getPerspectives().stream().flatMap(ParsedStrings::texts))
                .flatMap(texts -> texts);
    }

    private static Stream<String> texts(Perspective perspective) {
        return Stream.of(perspective.getName(), perspective.getDescription(), perspective.getValue(),
                perspective.getUrl());
    }

    private static Stream<String> texts(View view) {
        Stream<String> relationships = view instanceof ModelView modelView
                ? modelView.getRelationships().stream().flatMap(ParsedStrings::texts) : Stream.empty();
        return Stream.of(Stream.of(view.getKey(), view.getTitle(), view.getDescription()),
                properties(view), relationships).flatMap(texts -> texts);
    }

    private static Stream<String> texts(RelationshipView relationship) {
        return Stream.concat(Stream.of(relationship.getDescription(), relationship.getOrder(), relationship.getUrl()),
                properties(relationship));
    }

    private static Stream<String> texts(Configuration configuration) {
        Terminology terms = configuration.getTerminology();
        Stream<String> terminology = Stream.of(terms.getEnterprise(), terms.getPerson(), terms.getSoftwareSystem(),
                terms.getContainer(), terms.getComponent(), terms.getCode(), terms.getDeploymentNode(),
                terms.getInfrastructureNode(), terms.getRelationship());
        Stream<AbstractStyle> styles = Stream.concat(configuration.getStyles().getElements().stream(),
                configuration.getStyles().getRelationships().stream());
        return Stream.of(Stream.of(configuration.getDefaultView()), terminology, properties(configuration),
                styles.flatMap(style -> Stream.concat(Stream.of(style.getTag()), properties(style))))
                .flatMap(texts -> texts);
    }

    private static Stream<String> properties(PropertyHolder holder) {
        return holder.getProperties().entrySet().stream()
                .flatMap(property -> Stream.of(property.getKey(), property.getValue()));
    }
}
