package io.github.stigjohnny.docs.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Which test classes a Java source declares (ADR 0023): the JDK's parser reads it, so only names in code count, read
 * exactly as the compiler reads them, and a source with errors declares nothing.
 */
class JavaDeclarationsTest {

    static Stream<Arguments> sources() {
        return Stream.of(
                Arguments.of("class RealTest {}", List.of("RealTest")),
                Arguments.of("record RealTest() {}\nenum OtherTest {}\ninterface ThirdTest {}\n"
                        + "@interface FourthTest {}", List.of("FourthTest", "OtherTest", "RealTest", "ThirdTest")),
                Arguments.of("class OuterTest { class InnerTest {} void m() { class LocalTest {} } }",
                        List.of("InnerTest", "LocalTest", "OuterTest")),
                Arguments.of("// class GhostTest\nclass RealTest {}", List.of("RealTest")),
                Arguments.of("/* class GhostTest */ class RealTest {}", List.of("RealTest")),
                Arguments.of("class RealTest { String s = \"class GhostTest\"; }", List.of("RealTest")),
                Arguments.of("class RealTest { char c = '\"'; String s = \"class GhostTest\"; }", List.of("RealTest")),
                Arguments.of("class RealTest { String s = \"\"\"\n  \\\"\"\" class GhostTest {}\n  \"\"\"; }",
                        List.of("RealTest")),
                Arguments.of("\\u002f\\u002f class GhostTest {}\nclass RealTest {}", List.of("RealTest")),
                Arguments.of("cl\\u0061ss RealTest {}", List.of("RealTest")),
                Arguments.of("class RealTest { String s = \"\\u005c\\\\u0022; } class OtherTest {}",
                        List.of("OtherTest", "RealTest")),
                Arguments.of("// comment \\u000dclass RealTest {}", List.of("RealTest")),
                Arguments.of("class GhostTest$Helper {}", List.of()),
                Arguments.of("class Real$Test {}", List.of()),
                Arguments.of("", List.of()),
                Arguments.of("subclass GhostTest {}", List.of()),
                Arguments.of("class Broken extends {} class GhostTest {}", List.of()));
    }

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("sources")
    void declaresOnlyTheTestClassesItsCodeDeclares(String source, List<String> names) {
        assertThat(JavaDeclarations.testClasses(source)).containsExactlyElementsOf(names);
    }
}
