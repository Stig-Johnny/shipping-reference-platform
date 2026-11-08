package io.github.stigjohnny.c4contractkit.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The configuration files are read as strict JSON: whatever is not plain JSON is refused with its line. */
class JsonTest {

    private static Json.Value member(Json.Value object, String name) {
        return Objects.requireNonNull(object.object().get(name), name);
    }

    @Test
    void anObjectKeepsItsMembersInOrderWithTheLineOfEachValue() throws Json.Invalid {
        Json.Value value = Json.parse("""
                {
                  "name": "web",
                  "list": [1, -2.5e3, true, false, null],
                  "nested": { "a": "\\u0041\\n\\"" }
                }
                """);

        assertThat(value.isObject()).isTrue();
        assertThat(value.object().keySet()).containsExactly("name", "list", "nested");
        assertThat(member(value, "name").string()).isEqualTo("web");
        assertThat(member(value, "list").line()).isEqualTo(3);
        assertThat(member(value, "list").array()).extracting(Json.Value::value).containsExactly(
                new BigDecimal("1"), new BigDecimal("-2.5e3"), true, false, Json.Null.NULL);
        assertThat(member(member(value, "nested"), "a").string()).isEqualTo("A\n\"");
        assertThat(Json.parse("[]").array()).isEqualTo(List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{ \"a\": 1, }", "[1, 2,]", "{ // comment\n \"a\": 1 }", "{ /* c */ \"a\": 1 }",
        "{ \"a\": 1, \"a\": 2 }", "{ a: 1 }", "{ 'a': 1 }", "[01]", "[1.]", "[.5]", "[+1]", "[NaN]", "\"\t\"",
        "{ \"a\": 1 } x", "", "[1 2]", "{ \"a\" 1 }", "\"\\x\"", "\"open", "[true1]"})
    void whatIsNotPlainJsonIsRefused(String text) {
        assertThatThrownBy(() -> Json.parse(text)).isInstanceOf(Json.Invalid.class);
    }

    @Test
    void aRefusalNamesItsLine() {
        assertThatThrownBy(() -> Json.parse("{\n  \"a\": 1,\n  \"a\": 2\n}"))
                .isInstanceOf(Json.Invalid.class).hasMessage("the member name \"a\" appears twice")
                .satisfies(e -> assertThat(((Json.Invalid) e).line()).isEqualTo(3));
    }

    @Test
    void nestingDeeperThanSixtyFourLevelsIsRefused() {
        assertThatThrownBy(() -> Json.parse("[".repeat(66) + "]".repeat(66))).isInstanceOf(Json.Invalid.class)
                .hasMessage("more than 64 levels of nesting");
    }
}
