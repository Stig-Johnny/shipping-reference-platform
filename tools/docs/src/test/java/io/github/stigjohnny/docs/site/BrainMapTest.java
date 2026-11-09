package io.github.stigjohnny.docs.site;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.generate.Diagrams;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrainMapTest {
    @TempDir Path dir;

    @Test
    void mapPublishesTypedSourceableRelationsAndSeparateStatuses() {
        String html = brain(Fixture.copy(dir).passingEvidence());
        assertThat(html).contains("data-brain-node=\"shipments\"", "data-kind=\"module\"",
                "data-from=\"shipments\" data-to=\"quotes\" data-relation=\"dependency\"",
                "data-relation=\"decision\"", "data-relation=\"evidence\"",
                "Module status: built", "Decision status: accepted", "architecture/workspace.dsl",
                "data-brain-node=\"shipments.book-from-offer\"")
                .doesNotContain("data-brain-node=\"web.");
    }

    @Test
    void evidenceEdgesRequirePassingReportsInThisBuild() {
        String html = brain(Fixture.copy(dir));
        assertThat(html).contains("data-relation=\"dependency\"")
                .doesNotContain("data-relation=\"evidence\"");
    }

    @Test
    void editedModelDescriptionFlowsThroughToTheMap() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .edit("architecture/workspace.dsl", text -> text.replace("Books only against an existing, unexpired offer", "Reads the chosen offer"));
        assertThat(brain(fixture)).contains("Reads the chosen offer");
    }

    @Test
    void failedEvidenceCannotAcquireAnArrow() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .report("io.github.stigjohnny.docs.generate.BrainGeneratorTest", 1, 1, 0, 0);
        assertThat(brain(fixture)).contains("data-relation=\"evidence\"")
                .doesNotContain("data-to=\"test-BrainGeneratorTest\"");
    }

    @Test
    void removingAModelDependencyRemovesItsMapArrow() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().edit("architecture/workspace.dsl",
                text -> text.replaceFirst("(?s)        returns -> shipments \"Will create.*?\\n        }", ""));
        assertThat(brain(fixture)).doesNotContain(
                "data-from=\"returns\" data-to=\"shipments\" data-relation=\"dependency\"");
    }

    private static String brain(Fixture fixture) {
        Sources sources = Sources.read(fixture.root(), List.of(fixture.reports()));
        return BrainMap.html(new SiteContext(sources, SiteMap.of(sources),
                Diagrams.of(sources.workspace(), sources.elementStatus()),
                "0123456789abcdef0123456789abcdef01234567"));
    }
}
