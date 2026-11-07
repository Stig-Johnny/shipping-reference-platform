package io.github.stigjohnny.c4contractkit.api;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.github.stigjohnny.c4contractkit.testing.ClassRoots;
import io.github.stigjohnny.c4contractkit.testing.RepositoryFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The conditions of security/reviews/maven--archunit--1.5.1.md and maven--structurizr-dsl--6.2.4.md, and the
 * boundaries of libs/c4-contract-kit/AGENTS.md, checked with ArchUnit on the library's own class files.
 *
 * <p>A rule on a forbidden method or constructor matches its owner and name in a call or in a method or constructor
 * reference. A call through {@code java.lang.invoke}, a method handle looked up by name, is outside these rules; only
 * {@code java.lang.reflect} is banned outright. No production class uses {@code java.lang.invoke} today, and
 * policy/risk.json raises an added {@code MethodHandles.} in production code to level 3. docs/gaps.md section 5
 * records this gap.
 */
class ReviewConditionsTest {

    private static final String KIT = "io.github.stigjohnny.c4contractkit";
    private static final JavaClasses LIBRARY =
            new ClassFileImporter().importPath(ClassRoots.of(Conformance.class.getName()));

    private static final Set<String> FILE_PATH_IMPORTS = Set.of("importPath", "importPaths", "importLocations");

    private static final ArchRule NO_REFLECT = noClasses().should().accessTargetWhere(codeUnitAccess(
            "a reflect() method of ArchUnit",
            access -> access.getName().equals("reflect") && access.getTargetOwner().getPackageName()
                    .startsWith("com.tngtech.archunit")));

    private static final ArchRule FILE_PATH_IMPORTS_ONLY = noClasses().should().accessTargetWhere(codeUnitAccess(
            "a ClassFileImporter import other than a file path",
            access -> access.getTargetOwner().getName().equals("com.tngtech.archunit.core.importer.ClassFileImporter")
                    && access.getName().startsWith("import") && !FILE_PATH_IMPORTS.contains(access.getName())));

    private static final ArchRule ONE_PARSER_FACTORY = noClasses()
            .that().doNotHaveFullyQualifiedName(KIT + ".model.RestrictedDslParser")
            .should().accessTargetWhere(codeUnitAccess("a StructurizrDslParser constructor",
                    access -> isTarget(access, "com.structurizr.dsl.StructurizrDslParser",
                            JavaConstructor.CONSTRUCTOR_NAME)));

    private static final ArchRule NO_FEATURE_OR_URL_REOPENED = noClasses().should().accessTargetWhere(codeUnitAccess(
            "HttpClient.allow, setRestricted, or enabling a feature",
            access -> isTarget(access, "com.structurizr.http.HttpClient", "allow")
                    || isTarget(access, "com.structurizr.dsl.StructurizrDslParser", "setRestricted")
                    || isTarget(access, "com.structurizr.dsl.Features", "enable")
                    || isTarget(access, "com.structurizr.dsl.Features", "configure")
                    || isTarget(access, "com.structurizr.util.Features", "enable")
                    || isTarget(access, "com.structurizr.util.Features", "configure")));

    private static final ArchRule NO_CLASS_LOADING_ENVIRONMENT_OR_PROCESS = noClasses().should().accessTargetWhere(
            codeUnitAccess("class loading, the environment or a process",
                    access -> isTarget(access, "java.lang.Class", "forName")
                            || isTarget(access, "java.lang.ClassLoader", "loadClass")
                            || isTarget(access, "java.lang.System", "getenv")
                            || isTarget(access, "java.lang.Runtime", "exec")));

    private static final ArchRule NO_OBJECT_INPUT_STREAM =
            noClasses().should().dependOnClassesThat().haveFullyQualifiedName("java.io.ObjectInputStream");

    private static final ArchRule NO_PROCESS_BUILDER =
            noClasses().should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder");

    @Test
    void noReflectMethodOfArchUnitIsCalled() {
        NO_REFLECT.check(LIBRARY);
    }

    @Test
    void classesAreImportedFromExplicitFilePathsOnly() {
        FILE_PATH_IMPORTS_ONLY.check(LIBRARY);
    }

    @Test
    void noFreezingRuleAndNoPlantUmlDiagram() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("com.tngtech.archunit.library.freeze..",
                "com.tngtech.archunit.library.plantuml..").check(LIBRARY);
        assertThat(ClassLoader.getSystemResource("archunit.properties")).as("archunit.properties").isNull();
        assertThat(RepositoryFiles.library().resolve("src/main/resources/archunit.properties")).doesNotExist();
    }

    @Test
    void archUnitIsUsedInBytecodeOnlyAndStructurizrInModelOnly() {
        noClasses().that().resideOutsideOfPackage(KIT + ".bytecode..")
                .should().dependOnClassesThat().resideInAPackage("com.tngtech.archunit..").check(LIBRARY);
        noClasses().that().resideOutsideOfPackage(KIT + ".model..")
                .should().dependOnClassesThat().resideInAPackage("com.structurizr..").check(LIBRARY);
    }

    @Test
    void oneFactoryCreatesEveryParserAndNothingReopensAFeatureOrAUrl() {
        ONE_PARSER_FACTORY.check(LIBRARY);
        NO_FEATURE_OR_URL_REOPENED.check(LIBRARY);
    }

    @Test
    void noClassIsLoadedNoJsonOrObjectIsDeserialisedAndNothingIsFetched() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.lang.reflect..", "java.net..",
                "javax.net..", "com.fasterxml.jackson..").check(LIBRARY);
        NO_OBJECT_INPUT_STREAM.check(LIBRARY);
        NO_CLASS_LOADING_ENVIRONMENT_OR_PROCESS.check(LIBRARY);
        NO_PROCESS_BUILDER.check(LIBRARY);
    }

    static Stream<Arguments> rulesAndTheTargetsTheyForbid() {
        String importer = "com.tngtech.archunit.core.importer.ClassFileImporter.";
        return Stream.of(
                arguments("no reflect()", NO_REFLECT, List.of("com.tngtech.archunit.core.domain.JavaClass.reflect")),
                arguments("file path imports only", FILE_PATH_IMPORTS_ONLY,
                        List.of(importer + "importClasspath", importer + "importPackages", importer + "importUrl")),
                arguments("one parser factory", ONE_PARSER_FACTORY,
                        List.of("com.structurizr.dsl.StructurizrDslParser.<init>")),
                arguments("no feature or URL reopened", NO_FEATURE_OR_URL_REOPENED, List.of(
                        "com.structurizr.http.HttpClient.allow",
                        "com.structurizr.dsl.StructurizrDslParser.setRestricted",
                        "com.structurizr.dsl.Features.enable", "com.structurizr.dsl.Features.configure",
                        "com.structurizr.util.Features.enable", "com.structurizr.util.Features.configure")),
                arguments("no class loading, environment or process", NO_CLASS_LOADING_ENVIRONMENT_OR_PROCESS,
                        List.of("java.lang.Class.forName", "java.lang.ClassLoader.loadClass",
                                "java.lang.System.getenv", "java.lang.Runtime.exec")),
                arguments("no ObjectInputStream", NO_OBJECT_INPUT_STREAM, List.of("java.io.ObjectInputStream.<init>")),
                arguments("no ProcessBuilder", NO_PROCESS_BUILDER, List.of("java.lang.ProcessBuilder.<init>")));
    }

    /**
     * Each rule is checked on fixtures that break it: {@code Calls} calls every forbidden target, and
     * {@code References} only refers to each one as a method or constructor reference, which ArchUnit records apart
     * from calls. A rule must report exactly its own targets in both, and nothing the other rules forbid.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("rulesAndTheTargetsTheyForbid")
    void eachRuleReportsItsTargetsWhetherCalledOrReferenced(String rule, ArchRule archRule, List<String> targets) {
        assertThat(ForbiddenAccesses.reported(archRule, "Calls")).as("calls")
                .containsExactlyInAnyOrderElementsOf(targets);
        assertThat(ForbiddenAccesses.reported(archRule, "References")).as("references")
                .containsExactlyInAnyOrderElementsOf(targets);
    }

    @Test
    void thePublicApiShowsOnlyApiAndJdkTypes() {
        List<String> leaks = new ArrayList<>();
        for (JavaClass type : LIBRARY) {
            if (!type.getPackageName().equals(KIT + ".api") || !type.getModifiers().contains(JavaModifier.PUBLIC)) {
                continue;
            }
            for (JavaCodeUnit codeUnit : type.getCodeUnits()) {
                if (codeUnit.getModifiers().contains(JavaModifier.PUBLIC)) {
                    codeUnit.getReturnType().getAllInvolvedRawTypes().forEach(raw -> check(codeUnit.getFullName(), raw, leaks));
                    codeUnit.getParameterTypes().forEach(parameter -> parameter.getAllInvolvedRawTypes()
                            .forEach(raw -> check(codeUnit.getFullName(), raw, leaks)));
                }
            }
            for (JavaField field : type.getFields()) {
                if (field.getModifiers().contains(JavaModifier.PUBLIC)) {
                    field.getType().getAllInvolvedRawTypes().forEach(raw -> check(field.getFullName(), raw, leaks));
                }
            }
        }
        assertThat(leaks).isEmpty();
        assertThat(LIBRARY.stream().filter(type -> type.getPackageName().equals(KIT + ".api")))
                .extracting(JavaClass::getSimpleName)
                .contains("Conformance", "ConformanceAssertions", "Report", "ModelException");
    }

    private static void check(String member, JavaClass raw, List<String> leaks) {
        JavaClass type = raw.getBaseComponentType();
        if (!type.isPrimitive() && !type.getPackageName().startsWith("java.")
                && !type.getPackageName().equals(KIT + ".api")) {
            leaks.add(member + " uses " + type.getName());
        }
    }

    /**
     * A call, or a method or constructor reference ({@code Type::name}, {@code Type::new}), whose target matches.
     * ArchUnit records references apart from calls, so a rule on calls alone would miss them.
     */
    private static DescribedPredicate<JavaAccess<?>> codeUnitAccess(String description,
            Predicate<JavaAccess<?>> target) {
        return describe(description,
                (JavaAccess<?> access) -> access instanceof JavaCodeUnitAccess<?> && target.test(access));
    }

    /** Whether the access targets {@code owner.name}; a constructor's name is {@code <init>}. */
    private static boolean isTarget(JavaAccess<?> access, String owner, String name) {
        return access.getTargetOwner().getName().equals(owner) && access.getName().equals(name);
    }
}
