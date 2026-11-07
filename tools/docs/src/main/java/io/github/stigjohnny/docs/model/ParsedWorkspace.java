package io.github.stigjohnny.docs.model;

import java.util.List;

/** The model as records, and the raw Mermaid export of each of its views in the model's view order. */
public record ParsedWorkspace(C4Model model, List<RawMermaid> exports) {

    public ParsedWorkspace {
        exports = List.copyOf(exports);
    }
}
