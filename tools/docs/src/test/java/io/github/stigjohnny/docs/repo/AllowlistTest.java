package io.github.stigjohnny.docs.repo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.testing.Repository;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The allowlist as the Trust pages show it, and the reviewed-set digest recomputed the way PolicyCheck does. */
class AllowlistTest {

    private static final Allowlist ALLOWLIST = Allowlist.parse(Repository.read(Allowlist.PATH));

    @Test
    void theRecomputedDigestIsTheOneTheOwnerAccepted() {
        assertThat(ALLOWLIST.reviewedSetDigest(reviews())).isEqualTo(ALLOWLIST.current().reviewedSetSha256())
                .matches("[0-9a-f]{64}");
    }

    @Test
    void changingAnEntryChangesTheDigest() {
        String json = Repository.read(Allowlist.PATH);
        String changed = json.replaceFirst("\"expiresAt\": \"2027-05-06\"", "\"expiresAt\": \"2027-05-05\"");

        assertThat(changed).isNotEqualTo(json);
        assertThat(Allowlist.parse(changed).reviewedSetDigest(reviews()))
                .isNotEqualTo(ALLOWLIST.current().reviewedSetSha256());
    }

    @Test
    void theCurrentAndThePreviousAcceptancesAreRead() {
        assertThat(ALLOWLIST.accepted()).isTrue();
        assertThat(ALLOWLIST.current().record()).isEqualTo("pull request #10");
        assertThat(ALLOWLIST.current().at()).isEqualTo("2026-11-06T20:03:16.131Z");
        assertThat(ALLOWLIST.current().notes()).singleElement().asString().startsWith("Project path confirmed: ");
        assertThat(ALLOWLIST.previous()).extracting(Acceptance::record)
                .containsExactly("pull request #7", "pull request #3");
        assertThat(ALLOWLIST.previous().get(0).reviewedSetSha256()).matches("[0-9a-f]{64}");
        assertThat(ALLOWLIST.previous().get(0).entriesSha256()).matches("[0-9a-f]{64}");
        assertThat(ALLOWLIST.previous().get(1).reviewedSetSha256()).isEmpty();
    }

    @Test
    void entriesKeepTheirVerdictScopeAndConditions() {
        assertThat(ALLOWLIST.entries()).hasSize(308);
        assertThat(ALLOWLIST.approved()).isEqualTo(293);
        assertThat(ALLOWLIST.rejected()).isEqualTo(15);
        AllowlistEntry snakeyaml = ALLOWLIST.entries().stream()
                .filter(e -> e.coordinate().equals("org.snakeyaml:snakeyaml-engine") && e.version().equals("3.2"))
                .findFirst().orElseThrow();
        assertThat(snakeyaml.verdictWords()).isEqualTo("Approved with conditions");
        assertThat(snakeyaml.scope()).isNotBlank();
        assertThat(snakeyaml.conditions()).isNotEmpty();
        assertThat(ALLOWLIST.entries()).filteredOn(AllowlistEntry::rejected)
                .allSatisfy(e -> assertThat(e.verdictWords()).isEqualTo("Rejected"));
    }

    @Test
    void canonicalJsonSortsKeysAndEscapesLikeThePolicyCheck() {
        String json = "{\"b\": [1, null, true], \"a\": \"quote \\\" backslash \\\\ tab \\t é\"}";

        assertThat(Allowlist.canonical(SafeYaml.json(json, "t.json")))
                .isEqualTo("{\"a\":\"quote \\\" backslash \\\\ tab \\t é\",\"b\":[1,null,true]}");
    }

    private static Map<String, byte[]> reviews() {
        Map<String, byte[]> reviews = new TreeMap<>();
        for (String record : ALLOWLIST.reviewRecords()) {
            reviews.put(record, Sources.bytes(Repository.root(), record));
        }
        return reviews;
    }
}
