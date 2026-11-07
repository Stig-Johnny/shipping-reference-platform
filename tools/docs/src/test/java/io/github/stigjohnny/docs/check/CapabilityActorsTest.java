package io.github.stigjohnny.docs.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.generate.GeneratedFiles;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The actors of the product capabilities in docs/capabilities/capabilities.yaml, checked against the model before
 * anything is generated: each is a person or an external system of architecture/workspace.dsl, named by its identifier
 * there, that the model relates to the capability's module. Every capability states its actors, [] for system
 * behaviour; a control names none.
 */
class CapabilityActorsTest {

    @TempDir
    Path dir;

    private Fixture fixture;

    @BeforeEach
    void copyTheRepository() {
        fixture = Fixture.copy(dir).passingEvidence();
    }

    @Test
    void everyCapabilityOfTheRepositoryNamesActorsOfTheModelAndNoControlNamesAny() {
        Sources sources = sources();

        assertThat(sources.capabilities().capabilities()).allSatisfy(c -> assertThat(c.actors()).as(c.id())
                .isSubsetOf("clerk", "admin", "webshop", "carrier").doesNotHaveDuplicates());
        assertThat(sources.capabilities().controls()).allSatisfy(c -> assertThat(c.actors()).as(c.id()).isEmpty());
        assertThat(GeneratedFiles.generate(sources)).isNotEmpty();
    }

    @Test
    void anActorThatIsNoPersonOrExternalSystemOfTheModelFails() {
        actors("quotes.get-quote", "[clerk]", "[courier]");
        actors("shipments.list-and-read", "[clerk]", "[web]");

        assertFails().hasMessageContaining("quotes.get-quote: actor 'courier' is not a person or external system of "
                + "architecture/workspace.dsl; those are clerk, admin, webshop and carrier")
                .hasMessageContaining("shipments.list-and-read: actor 'web' is not a person or external system");
    }

    @Test
    void anActorThatTheModelDoesNotRelateToTheModuleFails() {
        actors("tenants.demo-authentication", "[clerk]", "[carrier]");

        assertFails().hasMessageContaining("tenants.demo-authentication: actor 'carrier' (Carrier (simulated)) has no "
                + "relationship in the model with Tenants, directly or through an element it uses");
    }

    @Test
    void aCapabilityWithoutActorsFailsAndSaysHowToDeclareNone() {
        fixture.edit(Capabilities.PATH, text -> text.replaceFirst("(id: quotes.quote-expiry\n(?:    .*\n){7})"
                + "    actors: \\[]\n", "$1"));

        assertThatThrownBy(this::sources).isInstanceOf(DocsException.class).hasMessageContaining(
                "quotes.quote-expiry: actors is missing; name the model's people or external systems that take part, "
                        + "or write [] to declare none");
    }

    @Test
    void anActorNamedTwiceFails() {
        actors("shipments.simulated-label", "[clerk, carrier]", "[clerk, carrier, clerk]");

        assertThatThrownBy(this::sources).isInstanceOf(DocsException.class)
                .hasMessageContaining("shipments.simulated-label: actor 'clerk' is named twice");
    }

    @Test
    void aControlNamesNoActors() {
        fixture.edit(Capabilities.PATH, text -> text.replace("    check: build\n",
                "    check: build\n    actors: [clerk]\n"));

        assertThatThrownBy(this::sources).isInstanceOf(DocsException.class)
                .hasMessageContaining("control.publication-scan: a control names no actors");
    }

    /** Replaces the actors of one capability, which must be {@code from} in the registry. */
    private void actors(String id, String from, String to) {
        fixture.edit(Capabilities.PATH, text -> text.replaceFirst("(id: " + id.replace(".", "\\.")
                + "\n(?:    .*\n){7})    actors: " + from.replace("[", "\\[") + "\n", "$1    actors: " + to + "\n"));
    }

    private Sources sources() {
        return Sources.read(fixture.root(), List.of(fixture.reports()));
    }

    private AbstractThrowableAssert<?, ? extends Throwable> assertFails() {
        return assertThatThrownBy(() -> GeneratedFiles.generate(sources())).isInstanceOf(DocsException.class);
    }
}
