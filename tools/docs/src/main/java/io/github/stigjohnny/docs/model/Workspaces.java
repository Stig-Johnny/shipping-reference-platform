package io.github.stigjohnny.docs.model;

import com.structurizr.Workspace;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.dsl.StructurizrDslParserException;
import com.structurizr.export.Diagram;
import com.structurizr.export.mermaid.MermaidDiagramExporter;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.Element;
import com.structurizr.model.Person;
import com.structurizr.model.Relationship;
import com.structurizr.model.SoftwareSystem;
import com.structurizr.view.ComponentView;
import com.structurizr.view.ContainerView;
import com.structurizr.view.DynamicView;
import com.structurizr.view.ElementView;
import com.structurizr.view.ModelView;
import com.structurizr.view.RelationshipView;
import com.structurizr.view.SystemContextView;
import com.structurizr.view.View;
import com.structurizr.view.ViewSet;
import io.github.stigjohnny.docs.DocsException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Reads a C4 workspace from a repository-owned DSL file through {@link RestrictedStructurizr}, rejects what the docs
 * site must not render, and exports each view with Structurizr's {@link MermaidDiagramExporter}, the only exporter
 * this tool uses. The exporter's raw output is handed on as {@link RawMermaid} and never published as it is.
 */
public final class Workspaces {

    private static final String MERMAID_PROPERTY_PREFIX = "mermaid.";
    private static final String REFERENCE = "${";

    private Workspaces() {
    }

    public static ParsedWorkspace read(Path dsl, String displayPath) {
        if (!Files.isRegularFile(dsl)) {
            throw new DocsException(displayPath + ": the model file does not exist");
        }
        String text;
        try {
            text = Files.readString(dsl, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DocsException(displayPath + ": the model file cannot be read", e);
        }
        RestrictedStructurizr.guard(text, displayPath);
        StructurizrDslParser parser = RestrictedStructurizr.newParser();
        try {
            parser.parse(dsl.toFile());
        } catch (StructurizrDslParserException | RuntimeException e) {
            String message = Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
            Path absolute = dsl.toAbsolutePath();
            Path directory = absolute.getParent();
            message = message.replace(absolute.toString(), displayPath);
            if (directory != null) {
                message = message.replace(directory.toString(), "");
            }
            throw new DocsException(displayPath + ": " + message, e);
        } catch (StackOverflowError e) {
            throw new DocsException(displayPath + ": the model is nested too deeply to parse", e);
        }
        Workspace workspace = parser.getWorkspace();
        if (workspace == null) {
            throw new DocsException(displayPath + ": the file defines no workspace");
        }
        rejectWhatTheSiteMustNotRender(workspace, displayPath);
        List<View> views = views(workspace.getViews(), displayPath);
        C4Model model = new C4Model(workspace.getName(), elements(workspace), relationships(workspace),
                views.stream().map(Workspaces::view).toList());
        return new ParsedWorkspace(model, views.stream().map(Workspaces::export).toList());
    }

    /**
     * Rejects what the parser accepts but the public site must not show: themes and URLs, which renderers would
     * fetch or link, Mermaid export properties (icons would become image tags), and {@code ${NAME}} references.
     */
    private static void rejectWhatTheSiteMustNotRender(Workspace workspace, String displayPath) {
        ViewSet viewSet = workspace.getViews();
        if (viewSet.getConfiguration().getThemes().length > 0) {
            throw new DocsException(displayPath + ": a theme is not allowed: renderers fetch themes");
        }
        rejectMermaidProperties(viewSet.getConfiguration().getProperties(), displayPath);
        for (View view : allViews(viewSet)) {
            rejectMermaidProperties(view.getProperties(), displayPath);
            rejectReferences(Stream.of(view.getKey(), view.getDescription(), view.getTitle()), displayPath);
        }
        for (Element element : workspace.getModel().getElements()) {
            if (element.getUrl() != null) {
                throw new DocsException(displayPath + ": element '" + element.getName()
                        + "' has a URL; the exporter would turn it into a click directive");
            }
            rejectReferences(Stream.concat(Stream.of(element.getName(), element.getDescription(),
                    technology(element), element.getTags()), properties(element.getProperties())), displayPath);
        }
        for (Relationship relationship : workspace.getModel().getRelationships()) {
            if (relationship.getUrl() != null) {
                throw new DocsException(displayPath + ": relationship '" + relationship.getDescription()
                        + "' has a URL; the exporter would turn it into a click directive");
            }
            rejectReferences(Stream.concat(Stream.of(relationship.getDescription(), relationship.getTechnology(),
                    relationship.getTags()), properties(relationship.getProperties())), displayPath);
        }
    }

    private static void rejectMermaidProperties(Map<String, String> properties, String displayPath) {
        for (String key : properties.keySet()) {
            if (key.startsWith(MERMAID_PROPERTY_PREFIX)) {
                throw new DocsException(displayPath + ": property '" + key + "' is not allowed: Mermaid export "
                        + "options are fixed by the docs tool");
            }
        }
    }

    private static void rejectReferences(Stream<@Nullable String> texts, String displayPath) {
        texts.filter(Objects::nonNull).filter(text -> text.contains(REFERENCE)).findFirst().ifPresent(text -> {
            throw new DocsException(displayPath + ": '" + text + "' contains a ${NAME} reference, which is never "
                    + "substituted: environment variables are not read");
        });
    }

    private static Stream<@Nullable String> properties(Map<String, String> properties) {
        return properties.entrySet().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue()));
    }

    private static List<View> allViews(ViewSet viewSet) {
        List<View> all = new ArrayList<>();
        all.addAll(viewSet.getSystemLandscapeViews());
        all.addAll(viewSet.getSystemContextViews());
        all.addAll(viewSet.getContainerViews());
        all.addAll(viewSet.getComponentViews());
        all.addAll(viewSet.getDynamicViews());
        all.addAll(viewSet.getDeploymentViews());
        all.addAll(viewSet.getFilteredViews());
        all.addAll(viewSet.getImageViews());
        all.addAll(viewSet.getCustomViews());
        return all;
    }

    /** The views the site renders, in C4 zoom order and by key within a kind; any other kind of view fails. */
    private static List<View> views(ViewSet viewSet, String displayPath) {
        int supported = viewSet.getSystemContextViews().size() + viewSet.getContainerViews().size()
                + viewSet.getComponentViews().size() + viewSet.getDynamicViews().size();
        if (supported != allViews(viewSet).size()) {
            throw new DocsException(displayPath + ": only system context, container, component and dynamic views "
                    + "can be published");
        }
        Comparator<View> byKey = Comparator.comparing(View::getKey);
        List<View> views = new ArrayList<>();
        viewSet.getSystemContextViews().stream().sorted(byKey).forEach(views::add);
        viewSet.getContainerViews().stream().sorted(byKey).forEach(views::add);
        viewSet.getComponentViews().stream().sorted(byKey).forEach(views::add);
        viewSet.getDynamicViews().stream().sorted(byKey).forEach(views::add);
        return views;
    }

    private static List<ModelElement> elements(Workspace workspace) {
        return workspace.getModel().getElements().stream()
                .sorted(Comparator.comparing(Element::getId, Workspaces::compareIds))
                .map(Workspaces::element).toList();
    }

    private static ModelElement element(Element element) {
        String type = switch (element) {
            case Person ignored -> "Person";
            case SoftwareSystem ignored -> "Software System";
            case Container ignored -> "Container";
            case Component ignored -> "Component";
            default -> throw new DocsException("element '" + element.getName() + "' is a "
                    + element.getClass().getSimpleName() + ", which the docs site does not render");
        };
        Element parent = element.getParent();
        return new ModelElement(element.getId(), element.getName(), type, text(element.getDescription()),
                technology(element), tags(element.getTags()), element.getProperties(),
                parent == null ? null : parent.getId());
    }

    private static List<ModelRelationship> relationships(Workspace workspace) {
        return workspace.getModel().getRelationships().stream()
                .sorted(Comparator.comparing(Relationship::getId, Workspaces::compareIds))
                .map(r -> new ModelRelationship(r.getId(), r.getSourceId(), r.getDestinationId(),
                        text(r.getDescription()), text(r.getTechnology()), tags(r.getTags()), r.getProperties()))
                .toList();
    }

    private static C4View view(View view) {
        ModelView modelView = (ModelView) view;
        C4View.Kind kind = switch (view) {
            case SystemContextView ignored -> C4View.Kind.SYSTEM_CONTEXT;
            case ContainerView ignored -> C4View.Kind.CONTAINER;
            case ComponentView ignored -> C4View.Kind.COMPONENT;
            case DynamicView ignored -> C4View.Kind.DYNAMIC;
            default -> throw new DocsException("view '" + view.getKey() + "' cannot be published");
        };
        String scope = switch (view) {
            case SystemContextView context -> context.getSoftwareSystem().getName();
            case ContainerView containers -> containers.getSoftwareSystem().getName();
            case ComponentView components -> components.getContainer().getName();
            case DynamicView dynamic -> dynamic.getElement() == null ? "" : dynamic.getElement().getName();
            default -> "";
        };
        List<String> elementIds = modelView.getElements().stream().map(ElementView::getId)
                .sorted(Workspaces::compareIds).toList();
        List<C4View.Step> steps = new ArrayList<>();
        Comparator<RelationshipView> order = kind == C4View.Kind.DYNAMIC
                ? Comparator.comparing(rv -> text(rv.getOrder()), Workspaces::compareIds)
                : Comparator.comparing(RelationshipView::getId, Workspaces::compareIds);
        modelView.getRelationships().stream().sorted(order).forEach(rv -> steps.add(
                new C4View.Step(rv.getId(),
                        kind == C4View.Kind.DYNAMIC ? text(rv.getOrder()) : "",
                        rv.getDescription() == null || rv.getDescription().isBlank()
                                ? text(rv.getRelationship().getDescription()) : rv.getDescription())));
        return new C4View(view.getKey(), kind, text(view.getDescription()), scope,
                elementIds, steps);
    }

    /** The only exporter this tool uses. Its options are never set, so {@code mermaid.icons} stays off. */
    private static RawMermaid export(View view) {
        MermaidDiagramExporter exporter = new MermaidDiagramExporter();
        Diagram diagram = switch (view) {
            case SystemContextView context -> exporter.export(context);
            case ContainerView containers -> exporter.export(containers);
            case ComponentView components -> exporter.export(components);
            case DynamicView dynamic -> exporter.export(dynamic);
            default -> throw new DocsException("view '" + view.getKey() + "' cannot be exported");
        };
        return new RawMermaid(view.getKey(), diagram.getDefinition());
    }

    private static String technology(Element element) {
        return switch (element) {
            case Container container -> text(container.getTechnology());
            case Component component -> text(component.getTechnology());
            default -> "";
        };
    }

    private static List<String> tags(@Nullable String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        Map<String, Boolean> unique = new LinkedHashMap<>();
        for (String tag : tags.split(",", -1)) {
            if (!tag.isBlank()) {
                unique.put(tag.strip(), Boolean.TRUE);
            }
        }
        return List.copyOf(unique.keySet());
    }

    private static String text(@Nullable String text) {
        return text == null ? "" : text;
    }

    /** Orders Structurizr's numeric IDs and dynamic step numbers ("2" before "10", "1.2" after "1.1"). */
    static int compareIds(String a, String b) {
        String[] left = a.split("\\.", -1);
        String[] right = b.split("\\.", -1);
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            int byNumber = compareSegment(left[i], right[i]);
            if (byNumber != 0) {
                return byNumber;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static int compareSegment(String a, String b) {
        boolean numeric = !a.isEmpty() && !b.isEmpty() && a.chars().allMatch(Character::isDigit)
                && b.chars().allMatch(Character::isDigit) && a.length() < 10 && b.length() < 10;
        return numeric ? Integer.compare(Integer.parseInt(a), Integer.parseInt(b)) : a.compareTo(b);
    }
}
