package io.github.stigjohnny.shipping.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * No class of the API lets JSON choose the class to create: no {@code @JsonTypeInfo} with {@code Id.CLASS} or
 * {@code Id.MINIMAL_CLASS} on a type, a member or a parameter. It is the annotation part of a condition of the Jackson
 * reviews (security/reviews/maven--jackson--3.1.8.md, condition 3) that apps/shipping-api/AGENTS.md gives the first
 * module's tests; the other part, no default typing, is configuration that no class-file rule sees.
 */
class JacksonTypingTest {

    private static final String API = "io.github.stigjohnny.shipping";
    private static final String FIXTURE = JacksonTypingTest.class.getPackageName() + ".fixtures.jackson";
    private static final String JSON_TYPE_INFO = "com.fasterxml.jackson.annotation.JsonTypeInfo";
    private static final Set<String> CLASS_IDS = Set.of("CLASS", "MINIMAL_CLASS");

    @Test
    void noClassOfTheApiLetsJsonChooseItsClass() {
        noJsonChosenClasses(API).check(ClassDirectories.read(ClassDirectories.main()));
    }

    @Test
    void aTypeOrAMemberThatLetsJsonChooseItsClassFails() {
        EvaluationResult result = noJsonChosenClasses(FIXTURE).evaluate(ClassDirectories.read(List.of(
                ClassDirectories.fixture(FIXTURE))));

        assertThat(result.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail).startsWith(FIXTURE + ".quotes.api.Payload "))
                .anySatisfy(detail -> assertThat(detail).startsWith(FIXTURE + ".quotes.api.Envelope.content "));
    }

    static ArchRule noJsonChosenClasses(String root) {
        return classes().that().resideInAPackage(root + "..").should(new ArchCondition<>(
                "not let JSON choose a class with @JsonTypeInfo(use = Id.CLASS or Id.MINIMAL_CLASS)") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (String place : places(javaClass)) {
                    events.add(SimpleConditionEvent.violated(javaClass, place + " lets JSON choose the class to "
                            + "create: @JsonTypeInfo with Id.CLASS or Id.MINIMAL_CLASS"));
                }
            }
        });
    }

    /** The class, members and parameters of a class that carry such an annotation, by name. */
    private static List<String> places(JavaClass javaClass) {
        List<String> places = new ArrayList<>();
        if (javaClass.getAnnotations().stream().anyMatch(JacksonTypingTest::letsJsonChooseTheClass)) {
            places.add(javaClass.getName());
        }
        for (JavaMember member : javaClass.getMembers()) {
            if (member.getAnnotations().stream().anyMatch(JacksonTypingTest::letsJsonChooseTheClass)) {
                places.add(member.getFullName());
            }
        }
        for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
            for (JavaParameter parameter : codeUnit.getParameters()) {
                if (parameter.getAnnotations().stream().anyMatch(JacksonTypingTest::letsJsonChooseTheClass)) {
                    places.add(codeUnit.getFullName() + " parameter " + parameter.getIndex());
                }
            }
        }
        return places;
    }

    private static boolean letsJsonChooseTheClass(JavaAnnotation<?> annotation) {
        return annotation.getRawType().getName().equals(JSON_TYPE_INFO) && annotation.get("use")
                .filter(JavaEnumConstant.class::isInstance).map(JavaEnumConstant.class::cast)
                .map(JavaEnumConstant::name).filter(CLASS_IDS::contains).isPresent();
    }
}
