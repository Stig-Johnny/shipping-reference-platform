package io.github.stigjohnny.docs.model;

/**
 * An element's implementation status, as a word in its diagram label and in words in the element table. A module has
 * its contract status, and a part of the web app its web status; an external system is external, and simulated when
 * its tags say so; a person has none. The platform's other elements, its containers and the system itself, are not
 * covered by module conformance, and are planned while no product capability is built.
 */
public final class ElementStatus {

    private static final String PERSON = "Person";

    private final boolean specificationOnly;

    /** @param specificationOnly whether every product capability is still planned */
    public ElementStatus(boolean specificationOnly) {
        this.specificationOnly = specificationOnly;
    }

    /** The word in parentheses after the C4 type in a diagram label, or empty for none. */
    public String label(ModelElement element) {
        if (element.contractStatus().isPresent()) {
            return element.contractStatus().get();
        }
        if (element.webStatus().isPresent()) {
            return element.webStatus().get();
        }
        if (element.external()) {
            return element.simulated() ? "external, simulated" : "external";
        }
        if (PERSON.equals(element.type())) {
            return "";
        }
        return element.planned() || specificationOnly ? "planned" : "";
    }

    /** The status in the element table. */
    public String words(ModelElement element) {
        if (element.contractStatus().isPresent()) {
            return element.contractStatus().get();
        }
        if (element.webStatus().isPresent()) {
            return element.webStatus().get() + "; checked by the web import check";
        }
        if (element.external()) {
            return element.simulated() ? "external, simulated" : "external";
        }
        if (PERSON.equals(element.type())) {
            return "Role; implementation status not applicable";
        }
        return element.planned() || specificationOnly ? "Planned; not covered by module conformance"
                : "Not covered by module conformance";
    }
}
