package io.github.stigjohnny.docs.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.testing.Repository;
import io.github.stigjohnny.docs.yaml.YamlValue.YBool;
import io.github.stigjohnny.docs.yaml.YamlValue.YList;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import io.github.stigjohnny.docs.yaml.YamlValue.YNull;
import io.github.stigjohnny.docs.yaml.YamlValue.YNumber;
import io.github.stigjohnny.docs.yaml.YamlValue.YString;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.CoreSchema;
import org.snakeyaml.engine.v2.schema.JsonSchema;

/** The conditions of security/reviews/maven--snakeyaml-engine--3.2.md, each as a failing input. */
class SafeYamlTest {

    @Test
    void readsYaml12CoreSchemaScalars() {
        YMap map = SafeYaml.yaml("a: 1\nb: [x, 'y']\nc: true\nd: null\ne: 2026-11-06\nf: on\ng: NO\n", "t.yaml")
                .asMap("root");

        assertThat(map.get("a")).isEqualTo(new YNumber("1"));
        assertThat(map.get("b")).isEqualTo(new YList(List.of(new YString("x"), new YString("y"))));
        assertThat(map.get("c")).isEqualTo(new YBool(true));
        assertThat(map.get("d")).isEqualTo(new YNull());
        // YAML 1.2: no timestamps and no yes/no/on/off booleans, so dates and country codes stay text.
        assertThat(map.get("e")).isEqualTo(new YString("2026-11-06"));
        assertThat(map.get("f")).isEqualTo(new YString("on"));
        assertThat(map.get("g")).isEqualTo(new YString("NO"));
    }

    @Test
    void readsJsonWithTheJsonSchemaInSourceOrder() {
        YMap map = SafeYaml.json("{\"z\": [1, \"x\", null, false], \"a\": {\"k\": \"v\"}}", "t.json").asMap("root");

        assertThat(map.entries().keySet()).containsExactly("z", "a");
        assertThat(map.list("z")).containsExactly(new YNumber("1"), new YString("x"), new YNull(), new YBool(false));
        assertThat(map.map("a").string("k")).isEqualTo("v");
    }

    @Test
    void aDuplicateKeyFails() {
        assertThatThrownBy(() -> SafeYaml.yaml("a: 1\na: 2\n", "dup.yaml"))
                .isInstanceOf(YamlInputException.class).hasMessageContaining("dup.yaml");
        assertThatThrownBy(() -> SafeYaml.json("{\"a\": 1, \"a\": 2}", "dup.json"))
                .isInstanceOf(YamlInputException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a: !foo x\n", "a: !!binary aGVsbG8=\n", "a: !!set {x}\n", "a: !!str x\n",
            "a: !!merge x\n", "!!map {a: b}\n", "a: !<tag:example.com,2026:x> y\n", "a: !!seq [x]\n"})
    void anExplicitTagFails(String input) {
        assertThatThrownBy(() -> SafeYaml.yaml(input, "tag.yaml"))
                .isInstanceOf(YamlInputException.class).hasMessageContaining("tag.yaml");
    }

    @Test
    void aMergeKeyFails() {
        assertThatThrownBy(() -> SafeYaml.yaml("a:\n  <<: {x: 1}\n  y: 2\n", "merge.yaml"))
                .isInstanceOf(YamlInputException.class).hasMessageContaining("<<");
    }

    @Test
    void anAliasToACollectionIsOverTheLimitOfZero() {
        assertThatThrownBy(() -> SafeYaml.yaml("a: &x [1, 2]\nb: *x\n", "alias.yaml"))
                .isInstanceOf(YamlInputException.class).hasMessageContaining("alias.yaml");
    }

    @Test
    void anInputOverTheCodePointLimitFails() {
        String input = "a: " + "x".repeat(SafeYaml.CODE_POINT_LIMIT) + "\n";

        assertThatThrownBy(() -> SafeYaml.yaml(input, "big.yaml")).isInstanceOf(YamlInputException.class);
        assertThatThrownBy(() -> SafeYaml.json("\"" + "x".repeat(SafeYaml.CODE_POINT_LIMIT) + "\"", "big.json"))
                .isInstanceOf(YamlInputException.class);
    }

    @Test
    void tenThousandDeepNestingFails() {
        String flow = "[".repeat(10_000) + "]".repeat(10_000);

        assertThatThrownBy(() -> SafeYaml.yaml(flow, "deep.yaml"))
                .isInstanceOf(YamlInputException.class).hasMessageContaining("deep.yaml");
        assertThatThrownBy(() -> SafeYaml.json(flow, "deep.json")).isInstanceOf(YamlInputException.class);
    }

    @Test
    void nestingIsLimitedBelowTheStackEvenWhenShallowerThanTenThousand() {
        String deep = "[".repeat(SafeYaml.MAX_NESTING + 1) + "]".repeat(SafeYaml.MAX_NESTING + 1);
        String allowed = "[".repeat(SafeYaml.MAX_NESTING) + "]".repeat(SafeYaml.MAX_NESTING);

        assertThatThrownBy(() -> SafeYaml.yaml(deep, "deep.yaml")).isInstanceOf(YamlInputException.class);
        assertThat(SafeYaml.yaml(allowed, "ok.yaml")).isInstanceOf(YList.class);
    }

    @Test
    void onlyOneDocumentMayBeRead() {
        assertThatThrownBy(() -> SafeYaml.yaml("a: 1\n---\nb: 2\n", "two.yaml"))
                .isInstanceOf(YamlInputException.class);
        assertThatThrownBy(() -> SafeYaml.yaml("", "empty.yaml")).isInstanceOf(YamlInputException.class);
    }

    @Test
    void aNonScalarKeyFails() {
        assertThatThrownBy(() -> SafeYaml.yaml("? [a, b]\n: c\n", "key.yaml")).isInstanceOf(YamlInputException.class);
    }

    @Test
    void environmentReferencesStayText() {
        assertThat(SafeYaml.yaml("a: ${HOME}\n", "env.yaml").asMap("root").string("a")).isEqualTo("${HOME}");
    }

    @Test
    void theSettingsAreExplicitAndStrict() throws IOException {
        long largestInput = Files.size(Repository.file("security/allowlist.json"));

        for (LoadSettings settings : List.of(SafeYaml.yamlSettings(), SafeYaml.jsonSettings())) {
            assertThat(settings.getAllowDuplicateKeys()).isFalse();
            assertThat(settings.getAllowRecursiveKeys()).isFalse();
            assertThat(settings.getAllowNonScalarKeys()).isFalse();
            assertThat(settings.getParseComments()).isFalse();
            assertThat(settings.getMaxAliasesForCollections()).isZero();
            assertThat(settings.getEnvConfig()).isEmpty();
            assertThat(settings.getTagConstructors()).isEmpty();
            assertThat(settings.getCodePointLimit()).isEqualTo(SafeYaml.CODE_POINT_LIMIT)
                    .isGreaterThan((int) largestInput).isLessThanOrEqualTo(3 * 1024 * 1024);
        }
        assertThat(SafeYaml.yamlSettings().getSchema()).isExactlyInstanceOf(CoreSchema.class);
        assertThat(SafeYaml.jsonSettings().getSchema()).isExactlyInstanceOf(JsonSchema.class);
    }

    @Test
    void theRepositorysOwnInputsAreReadable() {
        assertThat(SafeYaml.json(Repository.read("security/allowlist.json"), "security/allowlist.json")
                .asMap("root").list("entries")).hasSizeGreaterThan(100);
        assertThat(SafeYaml.json(Repository.read("policy/risk.json"), "policy/risk.json").asMap("root")
                .list("pathRules")).isNotEmpty();
        assertThat(SafeYaml.yaml(Repository.read("docs/capabilities/capabilities.yaml"), "capabilities.yaml")
                .asMap("root").list("capabilities")).isNotEmpty();
        assertThat(SafeYaml.yaml(Repository.read("contracts/shipping.openapi.yaml"), "contract").asMap("root")
                .string("openapi")).isEqualTo("3.1.2");
    }

    @Test
    void errorsNameTheSourceAndNeverAnAbsolutePath() {
        assertThatThrownBy(() -> SafeYaml.yaml("a: [\n", "docs/broken.yaml"))
                .isInstanceOf(YamlInputException.class)
                .hasMessageContaining("docs/broken.yaml")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(Repository.root().toString()));
    }
}
