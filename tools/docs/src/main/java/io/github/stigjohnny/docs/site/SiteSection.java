package io.github.stigjohnny.docs.site;

/** The site's navigation sections, in the order of the information architecture. */
public enum SiteSection {
    START("Start", "index.html", "What this is, its state, and where to begin."),
    DEMOS("Demos", "demos/index.html", "What each pull request changed, shown for readers who do not read code."),
    MODEL("Model", "model/index.html", "The C4 views, from context to one flow through the modules."),
    MODULES("Modules", "modules/index.html", "The seven modules and the web app, with what each must do."),
    DECISIONS("Decisions", "decisions/index.html", "The architecture decision records."),
    API("API", "api/index.html", "The HTTP contract: operations, schemas and errors."),
    TRUST("Trust", "trust/index.html", "Reviewed dependencies, risk levels, known gaps and notices."),
    BRAIN("Brain", "brain/index.html", "What is shipped and what is planned, with evidence, for agents and people.");

    private final String label;
    private final String index;
    private final String purpose;

    SiteSection(String label, String index, String purpose) {
        this.label = label;
        this.index = index;
        this.purpose = purpose;
    }

    public String label() {
        return label;
    }

    /** The section's index page. */
    public String index() {
        return index;
    }

    public String purpose() {
        return purpose;
    }
}
