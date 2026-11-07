package io.github.stigjohnny.docs.generate;

import io.github.stigjohnny.docs.mermaid.MermaidAdapter;
import io.github.stigjohnny.docs.mermaid.MermaidDiagram;
import io.github.stigjohnny.docs.model.ElementStatus;
import io.github.stigjohnny.docs.model.ParsedWorkspace;
import io.github.stigjohnny.docs.model.RawMermaid;
import java.util.ArrayList;
import java.util.List;

/** The one place where the exporter's raw output is handed to the validating adapter. */
public final class Diagrams {

    private Diagrams() {
    }

    /** Every view of the workspace as a validated diagram, in the model's view order. */
    public static List<MermaidDiagram> of(ParsedWorkspace workspace, ElementStatus status) {
        List<MermaidDiagram> diagrams = new ArrayList<>();
        for (RawMermaid raw : workspace.exports()) {
            diagrams.add(MermaidAdapter.adapt(raw, workspace.model(), status));
        }
        return List.copyOf(diagrams);
    }
}
