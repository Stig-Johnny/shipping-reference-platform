package io.github.stigjohnny.docs.generate;

import static io.github.stigjohnny.docs.generate.MarkdownText.cell;
import static io.github.stigjohnny.docs.generate.MarkdownText.code;
import static io.github.stigjohnny.docs.generate.MarkdownText.yaml;

import io.github.stigjohnny.docs.mermaid.MermaidDiagram;
import io.github.stigjohnny.docs.model.C4Model;
import io.github.stigjohnny.docs.model.C4View;
import io.github.stigjohnny.docs.model.ViewNames;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code docs/architecture/}: one Markdown page per view, with the validated Mermaid definition in a fence that GitHub
 * renders, the legend, the text equivalent and the view's limits, and the same definition as a {@code .mmd} file.
 */
public final class ArchitecturePages {

    public static final String DIRECTORY = "docs/architecture/";
    public static final String NOTICE = "Generated—do not hand-edit";

    private ArchitecturePages() {
    }

    public static Map<String, String> generate(Sources sources, Iterable<MermaidDiagram> diagrams) {
        Map<String, String> files = new TreeMap<>();
        C4Model model = sources.workspace().model();
        for (MermaidDiagram diagram : diagrams) {
            C4View view = model.view(diagram.viewKey());
            String slug = ViewNames.slug(view);
            files.put(DIRECTORY + slug + ".md", page(sources, view, diagram, slug));
            files.put(DIRECTORY + slug + ".mmd", "%% " + NOTICE + ". Source: " + Sources.WORKSPACE + ", view "
                    + view.key() + ".\n" + diagram.definition());
        }
        return files;
    }

    private static String page(Sources sources, C4View view, MermaidDiagram diagram, String slug) {
        C4Model model = sources.workspace().model();
        String title = "C4 view: " + ViewNames.title(view);
        StringBuilder out = new StringBuilder();
        out.append("---\n")
                .append("title: ").append(yaml(title)).append('\n')
                .append("description: ").append(yaml(ViewNames.kind(view) + " of " + scope(view) + ". "
                        + view.description())).append('\n')
                .append("status: accepted\n")
                .append("updated: ").append(sources.documentationDate()).append('\n')
                .append("---\n\n")
                .append("> ").append(NOTICE).append(". Source: ").append(code(Sources.WORKSPACE)).append(", view ")
                .append(code(view.key())).append(". Regenerate with\n> ").append(code("./gradlew :tools:docs:generate"))
                .append("; ").append(code(":tools:docs:checkGenerated")).append(" fails on any difference.\n\n")
                .append("# ").append(title).append("\n\n")
                .append(view.description()).append("\n\n")
                .append("```mermaid\n").append(diagram.definition()).append("```\n\n")
                .append("The same definition is in [").append(slug).append(".mmd](").append(slug).append(".mmd).\n\n")
                .append("## How to read it\n\n");
        for (String sentence : ViewText.legend(diagram, model)) {
            out.append("- ").append(sentence).append('\n');
        }
        out.append("\n## Elements\n\n| ID | Element | Type | Technology | Status | Description |\n")
                .append("|---|---|---|---|---|---|\n");
        for (ViewText.ElementRow row : ViewText.elements(view, model, sources.elementStatus())) {
            out.append("| ").append(row.id()).append(" | ").append(cell(row.name())).append(" | ")
                    .append(cell(row.type())).append(" | ").append(cell(row.technology())).append(" | ")
                    .append(cell(row.status())).append(" | ").append(cell(row.description())).append(" |\n");
        }
        out.append("\n## Relationships\n\n| ").append(view.kind() == C4View.Kind.DYNAMIC ? "Step" : "#")
                .append(" | From | To | Description | Technology | Line |\n|---|---|---|---|---|---|\n");
        for (ViewText.RelationshipRow row : ViewText.relationships(view, model, diagram)) {
            out.append("| ").append(row.number()).append(" | ").append(cell(row.from())).append(" | ")
                    .append(cell(row.to())).append(" | ").append(cell(row.description())).append(" | ")
                    .append(cell(row.technology())).append(" | ").append(cell(row.line())).append(" |\n");
        }
        out.append("\n## What this view cannot show\n\n");
        for (String limit : ViewText.limits(view)) {
            out.append("- ").append(limit).append('\n');
        }
        return out.toString();
    }

    private static String scope(C4View view) {
        return view.scopeName().isEmpty() ? "the workspace" : "the " + view.scopeName();
    }
}
