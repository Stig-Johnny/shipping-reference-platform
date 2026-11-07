package io.github.stigjohnny.docs.model;

/**
 * Structurizr's {@code MermaidDiagramExporter} output for one view, exactly as the exporter wrote it. It contains HTML
 * labels and is never published: only {@code io.github.stigjohnny.docs.mermaid.MermaidAdapter} reads it.
 */
public record RawMermaid(String viewKey, String definition) {
}
