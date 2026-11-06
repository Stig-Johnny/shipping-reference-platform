package io.github.stigjohnny.c4contractkit.api;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.stigjohnny.c4contractkit.testing.ClassRoots;
import io.github.stigjohnny.c4contractkit.testing.RepositoryFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The conditions of security/reviews/maven--archunit--1.5.1.md and maven--structurizr-dsl--6.2.4.md, and the
 * boundaries of libs/c4-contract-kit/AGENTS.md, checked with ArchUnit on the library's own class files.
 */
class ReviewConditionsTest {

    private static final String KIT = "io.github.stigjohnny.c4contractkit";
    private static final JavaClasses LIBRARY =
            new ClassFileImporter().importPath(ClassRoots.of(Conformance.class.getName()));

    @Test
    void noReflectMethodOfArchUnitIsCalled() {
        noClasses().should().callMethodWhere(describe("a reflect() method of ArchUnit",
                        (JavaMethodCall call) -> call.getName().equals("reflect") && inArchUnit(call)))
                .check(LIBRARY);
    }

    @Test
    void classesAreImportedFromExplicitFilePathsOnly() {
        Set<String> allowed = Set.of("importPath", "importPaths", "importLocations");
        noClasses().should().callMethodWhere(describe("a ClassFileImporter import other than a file path",
                        (JavaMethodCall call) -> call.getTargetOwner().getName()
                                .equals("com.tngtech.archunit.core.importer.ClassFileImporter")
                                && call.getName().startsWith("import") && !allowed.contains(call.getName())))
                .check(LIBRARY);
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
        noClasses().that().doNotHaveFullyQualifiedName(KIT + ".model.RestrictedDslParser")
                .should().callConstructorWhere(describe("a StructurizrDslParser constructor",
                        call -> call.getTargetOwner().getName().equals("com.structurizr.dsl.StructurizrDslParser")))
                .check(LIBRARY);
        noClasses().should().callMethodWhere(describe("HttpClient.allow, setRestricted, or enabling a feature",
                        (JavaMethodCall call) -> isCall(call, "com.structurizr.http.HttpClient", "allow")
                                || isCall(call, "com.structurizr.dsl.StructurizrDslParser", "setRestricted")
                                || isCall(call, "com.structurizr.dsl.Features", "enable")
                                || isCall(call, "com.structurizr.dsl.Features", "configure")
                                || isCall(call, "com.structurizr.util.Features", "enable")
                                || isCall(call, "com.structurizr.util.Features", "configure")))
                .check(LIBRARY);
    }

    @Test
    void noClassIsLoadedNoJsonOrObjectIsDeserialisedAndNothingIsFetched() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.lang.reflect..", "java.net..",
                "javax.net..", "com.fasterxml.jackson..").check(LIBRARY);
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("java.io.ObjectInputStream")
                .check(LIBRARY);
        noClasses().should().callMethodWhere(describe("class loading, the environment or a process",
                        (JavaMethodCall call) -> isCall(call, "java.lang.Class", "forName")
                                || isCall(call, "java.lang.ClassLoader", "loadClass")
                                || isCall(call, "java.lang.System", "getenv")
                                || isCall(call, "java.lang.Runtime", "exec")))
                .check(LIBRARY);
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder").check(LIBRARY);
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

    private static boolean inArchUnit(JavaMethodCall call) {
        return call.getTargetOwner().getPackageName().startsWith("com.tngtech.archunit");
    }

    private static boolean isCall(JavaMethodCall call, String owner, String name) {
        return call.getTargetOwner().getName().equals(owner) && call.getName().equals(name);
    }
}
