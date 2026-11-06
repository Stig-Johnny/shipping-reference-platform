package io.github.stigjohnny.c4contractkit.model;

import com.structurizr.PropertyHolder;
import com.structurizr.Workspace;
import com.structurizr.configuration.User;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.CustomElement;
import com.structurizr.model.DeploymentElement;
import com.structurizr.model.DeploymentNode;
import com.structurizr.model.Element;
import com.structurizr.model.GroupableElement;
import com.structurizr.model.HttpHealthCheck;
import com.structurizr.model.InfrastructureNode;
import com.structurizr.model.ModelItem;
import com.structurizr.model.Perspective;
import com.structurizr.model.Relationship;
import com.structurizr.model.StaticStructureElementInstance;
import com.structurizr.view.AbstractStyle;
import com.structurizr.view.Configuration;
import com.structurizr.view.DeploymentView;
import com.structurizr.view.ElementStyle;
import com.structurizr.view.FilteredView;
import com.structurizr.view.ModelView;
import com.structurizr.view.RelationshipStyle;
import com.structurizr.view.RelationshipView;
import com.structurizr.view.Terminology;
import com.structurizr.view.View;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The check after parsing that backs up {@link UnresolvedReferences}: a {@code ${NAME}} still in a string of the
 * parsed workspace was not substituted, whatever the source scan concluded.
 *
 * <p>It reads every text that the Structurizr 6.2.4 types reachable from a parsed workspace hold, taken from their
 * public getters (javap of structurizr-core 6.2.4) and walked explicitly, without reflection:
 * <ul>
 *   <li>the workspace: name, description, version, thumbnail, last modifying user and agent, properties, and the
 *       users of its configuration;</li>
 *   <li>the model: properties;</li>
 *   <li>every element, of any type: name, description, tags, URL, properties, perspectives (name, description,
 *       value, URL) and group; the technology of a container, component, deployment node or infrastructure node;
 *       the metadata of a custom element; the environment of a deployment node, infrastructure node or instance;
 *       the instances and deployment groups of a deployment node; the deployment groups and health checks (name,
 *       URL, headers) of a software system or container instance;</li>
 *   <li>every relationship: description, technology, tags, URL, properties and perspectives;</li>
 *   <li>every view: key, title, description, properties, and in a model view the relationships it shows
 *       (description, order, URL, properties); the environment of a deployment view; the base view key and tags of
 *       a filtered view;</li>
 *   <li>the view configuration: default view, terminology, properties, and every element and relationship style
 *       (tag, properties and colours, and the icon of an element style).</li>
 * </ul>
 * Not read: identifiers the model generates (element, relationship and view element ids), names derived from the
 * texts above (canonical names, view names), values that are not text (enums, numbers, dates), and image views and
 * themes, which the kit rejects as they are. {@link #unresolvedInDeclarations} reads the values that the parser gave
 * the constants and variables, which the workspace does not hold.
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
     *     for the view configuration, {@code model}, {@code workspace} or {@code configuration}
     * @param name the name in {@code ${name}}
     */
    record Reference(String place, String name) {
    }

    /** Every unresolved reference in the parsed workspace, once per place and name, sorted. */
    static List<Reference> unresolved(Workspace workspace, ElementNames names) {
        List<Reference> found = new ArrayList<>();
        find("workspace", Stream.concat(Stream.of(workspace.getName(), workspace.getDescription(),
                workspace.getVersion(), workspace.getThumbnail(), workspace.getLastModifiedUser(),
                workspace.getLastModifiedAgent()), properties(workspace)), found);
        find("configuration", workspace.getConfiguration().getUsers().stream().map(User::getUsername), found);
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

    /**
     * Every unresolved reference in the value that the parser gave a declared constant or variable: a reference that
     * only such a value holds, because nothing uses it, reaches no other string of the workspace.
     *
     * @param declared the names the file may declare, with their keyword ({@code !const} or {@code !var})
     * @param valueOf the parser's value for a name; the empty string when the parser defined none
     */
    static List<Reference> unresolvedInDeclarations(Map<String, String> declared, Function<String, String> valueOf) {
        List<Reference> found = new ArrayList<>();
        declared.forEach((name, keyword) -> find(keyword + " " + name, Stream.of(valueOf.apply(name)), found));
        return found.stream().distinct().toList();
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
        List<Stream<String>> texts = new ArrayList<>();
        texts.add(Stream.of(element.getName(), element.getDescription()));
        texts.add(texts((ModelItem) element));
        if (element instanceof GroupableElement groupable) {
            texts.add(Stream.of(groupable.getGroup()));
        }
        if (element instanceof DeploymentElement deployment) {
            texts.add(Stream.of(deployment.getEnvironment()));
        }
        switch (element) {
            case Container container -> texts.add(Stream.of(container.getTechnology()));
            case Component component -> texts.add(Stream.of(component.getTechnology()));
            case CustomElement custom -> texts.add(Stream.of(custom.getMetadata()));
            case DeploymentNode node -> texts.add(Stream.concat(Stream.of(node.getTechnology(), node.getInstances()),
                    node.getDeploymentGroups().stream()));
            case InfrastructureNode node -> texts.add(Stream.of(node.getTechnology()));
            case StaticStructureElementInstance instance -> texts.add(Stream.concat(
                    instance.getDeploymentGroups().stream(),
                    instance.getHealthChecks().stream().flatMap(ParsedStrings::texts)));
            default -> {
                // A person or software system holds no text beyond the above.
            }
        }
        return texts.stream().flatMap(stream -> stream);
    }

    private static Stream<String> texts(Relationship relationship) {
        return Stream.concat(Stream.of(relationship.getDescription(), relationship.getTechnology()),
                texts((ModelItem) relationship));
    }

    private static Stream<String> texts(HttpHealthCheck check) {
        return Stream.concat(Stream.of(check.getName(), check.getUrl()), entries(check.getHeaders()));
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
        List<Stream<String>> texts = new ArrayList<>();
        texts.add(Stream.of(view.getKey(), view.getTitle(), view.getDescription()));
        texts.add(properties(view));
        if (view instanceof ModelView modelView) {
            texts.add(modelView.getRelationships().stream().flatMap(ParsedStrings::texts));
        }
        if (view instanceof DeploymentView deployment) {
            texts.add(Stream.of(deployment.getEnvironment()));
        }
        if (view instanceof FilteredView filtered) {
            texts.add(Stream.concat(Stream.of(filtered.getBaseViewKey()), filtered.getTags().stream()));
        }
        return texts.stream().flatMap(stream -> stream);
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
        Collection<ElementStyle> elementStyles = configuration.getStyles().getElements();
        Collection<RelationshipStyle> relationshipStyles = configuration.getStyles().getRelationships();
        return Stream.of(Stream.of(configuration.getDefaultView()), terminology, properties(configuration),
                        elementStyles.stream().flatMap(style -> Stream.concat(style(style), Stream.of(
                                style.getBackground(), style.getStroke(), style.getColor(), style.getIcon()))),
                        relationshipStyles.stream().flatMap(style -> Stream.concat(style(style),
                                Stream.of(style.getColor()))))
                .flatMap(texts -> texts);
    }

    private static Stream<String> style(AbstractStyle style) {
        return Stream.concat(Stream.of(style.getTag()), properties(style));
    }

    private static Stream<String> properties(PropertyHolder holder) {
        return entries(holder.getProperties());
    }

    private static Stream<String> entries(Map<String, String> map) {
        return map.entrySet().stream().flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()));
    }
}
