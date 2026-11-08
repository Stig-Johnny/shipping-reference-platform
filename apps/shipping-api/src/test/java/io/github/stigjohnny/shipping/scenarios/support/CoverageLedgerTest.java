package io.github.stigjohnny.shipping.scenarios.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.shipping.scenarios.support.Comparison.Finding;
import io.github.stigjohnny.shipping.scenarios.support.Comparison.FindingType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverageLedgerTest {

    private static final String ACCESS = "call site example.Controller.reply() [()V] (line 12) uses "
            + "org.springframework.http.ResponseEntity#status(I)Lorg/springframework/http/ResponseEntity$BodyBuilder; "
            + "[JavaMethodCall], which could reach an implementation behind its proxy";
    private static final String ROW = "HTTP response/problem assembly\tphase 3 / option 2\t" + ACCESS;

    @Test
    void onlyAnExactFindingRemainsKnownAndStillRefusesConformance(@TempDir Path temp) throws Exception {
        CoverageLedger.Assessment ledger = ledger(temp, ROW).assess(List.of(ACCESS));
        Comparison.Result comparison = comparison(List.of(ACCESS));
        assertThat(comparison.conforms()).isFalse();
        assertThat(comparison.failing()).hasSize(1);
        assertThat(ledger.known()).hasSize(1);
        assertThat(ledger.blocking(comparison)).isEmpty();
    }

    @Test
    void aNewUnledgeredSpringAccessFails(@TempDir Path temp) throws Exception {
        String added = ACCESS.replace("#status(I)", "#status(Lorg/springframework/http/HttpStatusCode;)");
        List<String> actual = List.of(ACCESS, added);
        assertThat(ledger(temp, ROW).assess(actual).blocking(comparison(actual)))
                .extracting(Finding::message).containsExactly(added);
    }

    @Test
    void aRemovedLedgerEntryFails(@TempDir Path temp) throws Exception {
        assertThat(ledger(temp, "").assess(List.of(ACCESS)).blocking(comparison(List.of(ACCESS))))
                .extracting(Finding::message).containsExactly(ACCESS);
    }

    @Test
    void aChangedDescriptorFailsEvenWhenTheCountOwnerMemberAndLineStayTheSame(@TempDir Path temp) throws Exception {
        String changed = ACCESS.replace("#status(I)", "#status(Lorg/springframework/http/HttpStatusCode;)");
        assertThat(ledger(temp, ROW).assess(List.of(changed)).blocking(comparison(List.of(changed))))
                .extracting(Finding::message).containsExactly(changed, "ledger entry no longer detected: " + ACCESS);
    }

    @Test
    void aDisappearedGapFailsUntilItsLedgerEntryIsUpdated(@TempDir Path temp) throws Exception {
        assertThat(ledger(temp, ROW).assess(List.of()).blocking(comparison(List.of())))
                .extracting(Finding::message).containsExactly("ledger entry no longer detected: " + ACCESS);
    }

    @Test
    void duplicateGapsAndDuplicateLedgerEntriesCannotCollapseIntoASet(@TempDir Path temp) throws Exception {
        List<String> twice = List.of(ACCESS, ACCESS);
        assertThat(ledger(temp, ROW).assess(twice).blocking(comparison(twice)))
                .extracting(Finding::message).containsExactly(ACCESS);
        assertThatThrownBy(() -> ledger(temp, ROW + "\n" + ROW)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate finding");
    }

    @Test
    void malformedClassificationAndMissingFollowUpFailClosed(@TempDir Path temp) {
        for (String row : List.of(ROW.replace("HTTP response/problem assembly", "safe"),
                ROW.replace("phase 3 / option 2", ""))) {
            assertThatThrownBy(() -> ledger(temp, row)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static CoverageLedger ledger(Path temp, String rows) throws Exception {
        Path file = temp.resolve("ledger.tsv");
        Files.writeString(file, "classification\tfollow-up\tfinding\n" + rows + (rows.isEmpty() ? "" : "\n"));
        return CoverageLedger.read(file);
    }

    private static Comparison.Result comparison(List<String> gaps) {
        List<Finding> findings = new ArrayList<>();
        gaps.forEach(g -> findings.add(new Finding(FindingType.COVERAGE_GAP, "", "", 0, g)));
        return new Comparison.Result(List.of(), findings, Map.of());
    }
}
