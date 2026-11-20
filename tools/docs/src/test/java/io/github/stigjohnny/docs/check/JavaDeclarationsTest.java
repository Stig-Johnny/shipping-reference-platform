package io.github.stigjohnny.docs.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Which test classes a Java source declares (ADR 0023): only names in code count, as Java reads it, with Unicode
 * escapes translated first and identifiers read whole.
 */
class JavaDeclarationsTest {

    static Stream<Arguments> sources() {
        return Stream.of(
                Arguments.of("class RealTest {}", List.of("RealTest")),
                Arguments.of("record RealTest() {}\nenum OtherTest {}\ninterface ThirdTest {}\n"
                        + "@interface FourthTest {}", List.of("FourthTest", "OtherTest", "RealTest", "ThirdTest")),
                Arguments.of("// class GhostTest\nclass RealTest {}", List.of("RealTest")),
                Arguments.of("/* class GhostTest */ class RealTest {}", List.of("RealTest")),
                Arguments.of("String s = \"class GhostTest\"; class RealTest {}", List.of("RealTest")),
                Arguments.of("char c = '\"'; class RealTest {} String s = \"class GhostTest\";", List.of("RealTest")),
                Arguments.of("String s = \"\"\"\n  \\\"\"\" class GhostTest {}\n  \"\"\"; class RealTest {}",
                        List.of("RealTest")),
                Arguments.of("\\u002f\\u002f class GhostTest {}\nclass RealTest {}", List.of("RealTest")),
                Arguments.of("cl\\u0061ss RealTest {}", List.of("RealTest")),
                Arguments.of("String s = \"\\\\u0022\"; class RealTest {}", List.of("RealTest")),
                Arguments.of("class GhostTest$Helper {}", List.of()),
                Arguments.of("subclass GhostTest {}", List.of()),
                Arguments.of("class Real$Test {}", List.of()),
                Arguments.of("", List.of()));
    }

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("sources")
    void declaresOnlyTheTestClassesItsCodeDeclares(String source, List<String> names) {
        assertThat(JavaDeclarations.testClasses(source)).containsExactlyElementsOf(names);
    }
}
