package io.github.stigjohnny.c4contractkit.bytecode;

import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.ANNOTATION;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.ANNOTATION_MEMBER;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.CATCH;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.CLASS_LITERAL;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.CONSTRUCTOR_CALL;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.CONSTRUCTOR_REFERENCE;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.EXTENDS;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.FIELD_READ;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.FIELD_TYPE;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.FIELD_TYPE_ARGUMENT;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.FIELD_WRITE;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.IMPLEMENTS;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.INSTANCEOF;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.INTERFACE_TYPE_ARGUMENT;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.METHOD_CALL;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.METHOD_REFERENCE;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.PARAMETER_ANNOTATION;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.PARAMETER_ANNOTATION_MEMBER;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.PARAMETER_TYPE;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.PARAMETER_TYPE_ARGUMENT;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.RETURN_TYPE;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.RETURN_TYPE_ARGUMENT;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.SUPERCLASS_TYPE_ARGUMENT;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.THROWS;
import static io.github.stigjohnny.c4contractkit.bytecode.ReferenceKind.TYPE_PARAMETER_BOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.c4contractkit.testing.ClassPathLabel;
import io.github.stigjohnny.c4contractkit.testing.FixtureCompiler;
import io.github.stigjohnny.c4contractkit.testing.FixtureSource;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Evidence comes from class files only, exactly as ArchUnit reports each class's direct dependencies: signatures,
 * inheritance, annotations, generics, code inside lambdas, and the member and line behind each reference.
 */
class BytecodeEvidenceTest {

    private static final FixtureSource TARGET = new FixtureSource("fx/app/b/Target.java", """
            package fx.app.b;

            public class Target {
                public static int counter;
                public int value;

                public Target() {
                }

                public Target(int value) {
                    this.value = value;
                }

                public static Target create() {
                    return new Target();
                }

                public static void touch() {
                }

                public int read() {
                    return value;
                }
            }
            """);
    private static final FixtureSource CONTRACT = new FixtureSource("fx/app/b/Contract.java", """
            package fx.app.b;

            public interface Contract {
                void run();
            }
            """);
    private static final FixtureSource BASE = new FixtureSource("fx/app/b/Base.java", """
            package fx.app.b;

            public class Base<T> {
            }
            """);
    private static final FixtureSource BOX = new FixtureSource("fx/app/b/Box.java", """
            package fx.app.b;

            public interface Box<T> {
            }
            """);
    private static final FixtureSource LEVEL = new FixtureSource("fx/app/b/Level.java", """
            package fx.app.b;

            public enum Level {
                LOW,
                HIGH
            }
            """);
    private static final FixtureSource FAILURE = new FixtureSource("fx/app/b/Failure.java", """
            package fx.app.b;

            public class Failure extends Exception {
                private static final long serialVersionUID = 1L;
            }
            """);
    private static final FixtureSource MARKER = new FixtureSource("fx/app/b/Marker.java", """
            package fx.app.b;

            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;

            @Retention(RetentionPolicy.CLASS)
            public @interface Marker {
            }
            """);
    private static final FixtureSource TAGGED = new FixtureSource("fx/app/b/Tagged.java", """
            package fx.app.b;

            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;

            @Retention(RetentionPolicy.RUNTIME)
            public @interface Tagged {
                Class<?> value();

                Level level();
            }
            """);

    private static final FixtureSource SIGNATURES = new FixtureSource("fx/app/a/Signatures.java", """
            package fx.app.a;

            import fx.app.b.Failure;
            import fx.app.b.Target;

            public class Signatures {
                Target field;
                Target[][] grid;
                int[] numbers;
                Signatures self;

                public Signatures(Target target) throws Failure {
                }

                Target produce(int count, Target[] input, Signatures other) {
                    return input[count];
                }
            }
            """);

    private static final FixtureSource INHERITANCE = new FixtureSource("fx/app/a/Child.java", """
            package fx.app.a;

            import fx.app.b.Base;
            import fx.app.b.Box;
            import fx.app.b.Contract;
            import fx.app.b.Target;

            public class Child extends Base<Target> implements Contract, Box<Target[]>, Runnable {
                @Override
                public void run() {
                }
            }
            """);
    private static final FixtureSource NARROW = new FixtureSource("fx/app/a/Narrow.java", """
            package fx.app.a;

            import fx.app.b.Contract;

            public interface Narrow extends Contract {
            }
            """);

    private static final FixtureSource ANNOTATED = new FixtureSource("fx/app/a/Annotated.java", """
            package fx.app.a;

            import fx.app.b.Level;
            import fx.app.b.Marker;
            import fx.app.b.Tagged;
            import fx.app.b.Target;

            @Marker
            @Tagged(value = Target.class, level = Level.HIGH)
            public class Annotated {
                @Marker
                Object field;

                @Marker
                void method(@Tagged(value = Object.class, level = Level.LOW) int parameter) {
                }
            }
            """);

    private static final FixtureSource GENERIC = new FixtureSource("fx/app/a/Generic.java", """
            package fx.app.a;

            import fx.app.b.Contract;
            import fx.app.b.Failure;
            import fx.app.b.Level;
            import fx.app.b.Target;
            import java.util.List;
            import java.util.function.Supplier;

            public class Generic<T extends Target> {
                List<Target> targets;
                List<? extends Contract> contracts;

                <C extends Contract> List<Level> levels(Supplier<Failure> supplier, C contract) {
                    return List.of();
                }
            }
            """);

    private static final FixtureSource LAMBDAS = new FixtureSource("fx/app/a/Lambdas.java", """
            package fx.app.a;

            import fx.app.b.Target;
            import java.util.function.Supplier;

            public class Lambdas {
                Supplier<Object> supplier() {
                    return () -> new Target(1); // L:lambda
                }

                Supplier<Object> methodReference() {
                    return Target::create; // L:method-reference
                }

                Supplier<Object> constructorReference() {
                    return Target::new; // L:constructor-reference
                }

                Runnable nested() {
                    return () -> {
                        Runnable inner = () -> Target.touch(); // L:nested
                        inner.run();
                    };
                }
            }
            """);

    private static final FixtureSource CODE = new FixtureSource("fx/app/a/Code.java", """
            package fx.app.a;

            import fx.app.b.Contract;
            import fx.app.b.Failure;
            import fx.app.b.Level;
            import fx.app.b.Target;

            public class Code {
                static Object cached = Target.create(); // L:static

                Object use(Object value) {
                    Target.touch(); // L:call
                    int count = Target.counter; // L:read
                    Target.counter = count + 1; // L:write
                    Target target = new Target(); // L:new
                    target.value = target.read(); // L:instance
                    Target.touch(); // L:call-again
                    if (value instanceof Contract) { // L:instanceof
                        return Level.class; // L:literal
                    }
                    try {
                        throw new Failure(); // L:throw
                    } catch (Failure failure) {
                        return failure;
                    }
                }
            }
            """);

    private static final FixtureSource OUTER = new FixtureSource("fx/app/a/Outer.java", """
            package fx.app.a;

            import fx.app.b.Target;

            public class Outer {
                class Inner {
                    Target target;
                }

                static class Nested {
                    Object create() {
                        return Target.create(); // L:nested
                    }
                }

                Runnable anonymous() {
                    return new Runnable() {
                        @Override
                        public void run() {
                            Target.touch(); // L:anonymous
                        }
                    };
                }
            }
            """);

    private static final FixtureSource PACKAGE_INFO = new FixtureSource("fx/app/a/package-info.java", """
            @Marker
            package fx.app.a;

            import fx.app.b.Marker;
            """);

    private static final FixtureSource INITIALISER = new FixtureSource("fx/app/a/Initialiser.java", """
            package fx.app.a;

            public class Initialiser {
                static {
                    System.setProperty("c4-contract-kit.fixture.initialised", "true");
                }
            }
            """);

    private static final FixtureSource LABELLED = new FixtureSource("fx/app/a/Labelled.java", """
            package fx.app.a;

            import io.github.stigjohnny.c4contractkit.testing.ClassPathLabel;

            @ClassPathLabel
            public class Labelled { // L:class
            }
            """);

    private static final FixtureSource[] TARGETS = {TARGET, CONTRACT, BASE, BOX, LEVEL, FAILURE, MARKER, TAGGED};

    @Test
    void signaturesReferenceTheirTypesWithoutALine() {
        List<CodeReference> references = referencesToTargets(SIGNATURES, "fx.app.a.Signatures");

        assertThat(references).containsExactly(
                reference("fx.app.a.Signatures", "<init>(fx.app.b.Target)", "fx.app.b.Failure", "", THROWS, 0),
                reference("fx.app.a.Signatures", "<init>(fx.app.b.Target)", "fx.app.b.Target", "", PARAMETER_TYPE, 0),
                reference("fx.app.a.Signatures", "field", "fx.app.b.Target", "", FIELD_TYPE, 0),
                reference("fx.app.a.Signatures", "grid", "fx.app.b.Target", "", FIELD_TYPE, 0),
                reference("fx.app.a.Signatures", "produce(int,fx.app.b.Target[],fx.app.a.Signatures)",
                        "fx.app.b.Target", "", PARAMETER_TYPE, 0),
                reference("fx.app.a.Signatures", "produce(int,fx.app.b.Target[],fx.app.a.Signatures)",
                        "fx.app.b.Target", "", RETURN_TYPE, 0));
    }

    @Test
    void aClassNeverReferencesItselfOrPrimitives() {
        ImportedClass signatures = imported(SIGNATURES, "fx.app.a.Signatures");

        assertThat(signatures.references()).extracting(CodeReference::targetClass)
                .doesNotContain("fx.app.a.Signatures", "int", "[I");
        assertThat(signatures.references()).extracting(CodeReference::targetClass)
                .contains("java.lang.Object");
    }

    @Test
    void inheritanceIsExtendsForSuperclassesAndImplementsForInterfacesEvenWhenTheInterfaceIsNotImported() {
        List<CodeReference> references = imported(INHERITANCE, "fx.app.a.Child").references().stream()
                .filter(reference -> reference.originMember().isEmpty())
                .toList();

        assertThat(references).containsExactly(
                reference("fx.app.a.Child", "", "fx.app.b.Base", "", EXTENDS, 0),
                reference("fx.app.a.Child", "", "fx.app.b.Box", "", IMPLEMENTS, 0),
                reference("fx.app.a.Child", "", "fx.app.b.Contract", "", IMPLEMENTS, 0),
                reference("fx.app.a.Child", "", "fx.app.b.Target", "", INTERFACE_TYPE_ARGUMENT, 0),
                reference("fx.app.a.Child", "", "fx.app.b.Target", "", SUPERCLASS_TYPE_ARGUMENT, 0),
                reference("fx.app.a.Child", "", "java.lang.Runnable", "", IMPLEMENTS, 0));
    }

    @Test
    void anInterfaceExtendsTheInterfacesItInherits() {
        assertThat(referencesToTargets(NARROW, "fx.app.a.Narrow")).containsExactly(
                reference("fx.app.a.Narrow", "", "fx.app.b.Contract", "", EXTENDS, 0));
    }

    @Test
    void annotationsAndTheTypesOfTheirMembersAreReferences() {
        assertThat(referencesToTargets(ANNOTATED, "fx.app.a.Annotated")).containsExactly(
                reference("fx.app.a.Annotated", "", "fx.app.b.Level", "", ANNOTATION_MEMBER, 0),
                reference("fx.app.a.Annotated", "", "fx.app.b.Marker", "", ANNOTATION, 0),
                reference("fx.app.a.Annotated", "", "fx.app.b.Tagged", "", ANNOTATION, 0),
                reference("fx.app.a.Annotated", "", "fx.app.b.Target", "", ANNOTATION_MEMBER, 0),
                reference("fx.app.a.Annotated", "field", "fx.app.b.Marker", "", ANNOTATION, 0),
                reference("fx.app.a.Annotated", "method(int)", "fx.app.b.Level", "", PARAMETER_ANNOTATION_MEMBER, 0),
                reference("fx.app.a.Annotated", "method(int)", "fx.app.b.Marker", "", ANNOTATION, 0),
                reference("fx.app.a.Annotated", "method(int)", "fx.app.b.Tagged", "", PARAMETER_ANNOTATION, 0));
    }

    @Test
    void typeArgumentsAndTypeParameterBoundsAreReferences() {
        assertThat(referencesToTargets(GENERIC, "fx.app.a.Generic")).containsExactly(
                reference("fx.app.a.Generic", "", "fx.app.b.Target", "", TYPE_PARAMETER_BOUND, 0),
                reference("fx.app.a.Generic", "contracts", "fx.app.b.Contract", "", FIELD_TYPE_ARGUMENT, 0),
                reference("fx.app.a.Generic", "levels(java.util.function.Supplier,fx.app.b.Contract)",
                        "fx.app.b.Contract", "", PARAMETER_TYPE, 0),
                reference("fx.app.a.Generic", "levels(java.util.function.Supplier,fx.app.b.Contract)",
                        "fx.app.b.Contract", "", TYPE_PARAMETER_BOUND, 0),
                reference("fx.app.a.Generic", "levels(java.util.function.Supplier,fx.app.b.Contract)",
                        "fx.app.b.Failure", "", PARAMETER_TYPE_ARGUMENT, 0),
                reference("fx.app.a.Generic", "levels(java.util.function.Supplier,fx.app.b.Contract)",
                        "fx.app.b.Level", "", RETURN_TYPE_ARGUMENT, 0),
                reference("fx.app.a.Generic", "targets", "fx.app.b.Target", "", FIELD_TYPE_ARGUMENT, 0));
    }

    @Test
    void codeInLambdasBelongsToTheEnclosingMethodAndKeepsItsLine() {
        assertThat(referencesToTargets(LAMBDAS, "fx.app.a.Lambdas")).containsExactly(
                reference("fx.app.a.Lambdas", "constructorReference()", "fx.app.b.Target", "<init>()",
                        CONSTRUCTOR_REFERENCE, LAMBDAS.lineOf("L:constructor-reference")),
                reference("fx.app.a.Lambdas", "methodReference()", "fx.app.b.Target", "create()", METHOD_REFERENCE,
                        LAMBDAS.lineOf("L:method-reference")),
                reference("fx.app.a.Lambdas", "nested()", "fx.app.b.Target", "touch()", METHOD_CALL,
                        LAMBDAS.lineOf("L:nested")),
                reference("fx.app.a.Lambdas", "supplier()", "fx.app.b.Target", "<init>(int)", CONSTRUCTOR_CALL,
                        LAMBDAS.lineOf("L:lambda")));
    }

    @Test
    void codeReferencesCarryTheirMemberAndLine() {
        String use = "use(java.lang.Object)";

        assertThat(referencesToTargets(CODE, "fx.app.a.Code")).containsExactly(
                reference("fx.app.a.Code", "<clinit>()", "fx.app.b.Target", "create()", METHOD_CALL,
                        CODE.lineOf("L:static")),
                reference("fx.app.a.Code", use, "fx.app.b.Contract", "", INSTANCEOF, CODE.lineOf("L:instanceof")),
                reference("fx.app.a.Code", use, "fx.app.b.Failure", "", CATCH, CODE.lineOf("L:throw")),
                reference("fx.app.a.Code", use, "fx.app.b.Failure", "<init>()", CONSTRUCTOR_CALL,
                        CODE.lineOf("L:throw")),
                reference("fx.app.a.Code", use, "fx.app.b.Level", "", CLASS_LITERAL, CODE.lineOf("L:literal")),
                reference("fx.app.a.Code", use, "fx.app.b.Target", "<init>()", CONSTRUCTOR_CALL,
                        CODE.lineOf("L:new")),
                reference("fx.app.a.Code", use, "fx.app.b.Target", "counter", FIELD_READ, CODE.lineOf("L:read")),
                reference("fx.app.a.Code", use, "fx.app.b.Target", "counter", FIELD_WRITE, CODE.lineOf("L:write")),
                reference("fx.app.a.Code", use, "fx.app.b.Target", "read()", METHOD_CALL, CODE.lineOf("L:instance")),
                reference("fx.app.a.Code", use, "fx.app.b.Target", "touch()", METHOD_CALL, CODE.lineOf("L:call")),
                reference("fx.app.a.Code", use, "fx.app.b.Target", "touch()", METHOD_CALL,
                        CODE.lineOf("L:call-again")),
                reference("fx.app.a.Code", use, "fx.app.b.Target", "value", FIELD_WRITE, CODE.lineOf("L:instance")));
    }

    @Test
    void nestedLocalAndAnonymousClassesAreClassesOfTheirOwn() {
        List<ImportedClass> classes = ClassRootImporter.importRoots(List.of(compile(OUTER)));

        assertThat(classes).extracting(ImportedClass::name).filteredOn(name -> name.startsWith("fx.app.a."))
                .containsExactly("fx.app.a.Outer", "fx.app.a.Outer$1", "fx.app.a.Outer$Inner", "fx.app.a.Outer$Nested");
        assertThat(targetsOf(classes, "fx.app.a.Outer$Inner")).containsExactly(
                reference("fx.app.a.Outer$Inner", "target", "fx.app.b.Target", "", FIELD_TYPE, 0, "Outer.java"));
        assertThat(targetsOf(classes, "fx.app.a.Outer$Nested")).containsExactly(
                reference("fx.app.a.Outer$Nested", "create()", "fx.app.b.Target", "create()", METHOD_CALL,
                        OUTER.lineOf("L:nested"), "Outer.java"));
        assertThat(targetsOf(classes, "fx.app.a.Outer$1")).containsExactly(
                reference("fx.app.a.Outer$1", "run()", "fx.app.b.Target", "touch()", METHOD_CALL,
                        OUTER.lineOf("L:anonymous"), "Outer.java"));
    }

    @Test
    void packageInfoIsMarkedAndItsAnnotationsAreReferences() {
        ImportedClass packageInfo = imported(PACKAGE_INFO, "fx.app.a.package-info");

        assertThat(packageInfo.packageInfo()).isTrue();
        assertThat(packageInfo.references()).filteredOn(reference -> reference.targetClass().startsWith("fx.app.b."))
                .containsExactly(reference("fx.app.a.package-info", "", "fx.app.b.Marker", "", ANNOTATION, 0,
                        "package-info.java"));
        assertThat(imported(CODE, "fx.app.a.Code").packageInfo()).isFalse();
    }

    @Test
    void withoutDebugInformationTheSourceFileIsEmptyAndTheLineIsZero() {
        Path root = FixtureCompiler.compileWithoutDebugInformation(withTargets(CODE));

        assertThat(targetsOf(ClassRootImporter.importRoots(List.of(root)), "fx.app.a.Code"))
                .isNotEmpty()
                .allSatisfy(reference -> {
                    assertThat(reference.sourceFile()).isEmpty();
                    assertThat(reference.line()).isZero();
                });
    }

    @Test
    void classesAreReadAndNeverLoadedOrInitialised() {
        String property = "c4-contract-kit.fixture.initialised";
        System.clearProperty(property);

        ImportedClass initialiser = imported(INITIALISER, "fx.app.a.Initialiser");

        assertThat(initialiser.references()).extracting(CodeReference::originMember).contains("<clinit>()");
        assertThat(System.getProperty(property)).isNull();
    }

    @Test
    void theResultDependsOnTheRootsOnlyAndNeverOnTheClassPathTheCheckRunsOn() {
        // ClassPathLabel is on the test class path, not in the fixture's root. Its default value names RepositoryFiles,
        // which only an importer that resolves missing classes from the class path would see.
        Path root = FixtureCompiler.compileAgainstTestClassesOf(ClassPathLabel.class.getName(), LABELLED);

        List<CodeReference> references = ClassRootImporter.importRoots(List.of(root)).stream()
                .filter(imported -> imported.name().equals("fx.app.a.Labelled"))
                .flatMap(imported -> imported.references().stream())
                .toList();

        assertThat(references).containsExactly(
                reference("fx.app.a.Labelled", "", ClassPathLabel.class.getName(), "", ANNOTATION, 0),
                reference("fx.app.a.Labelled", "", "java.lang.Object", "", EXTENDS, 0),
                reference("fx.app.a.Labelled", "<init>()", "java.lang.Object", "<init>()", CONSTRUCTOR_CALL,
                        LABELLED.lineOf("L:class")));
    }

    @Test
    void arrayTypesAreWrittenAsInJavaEvenInTheSignaturesOfCalledMethods() {
        FixtureSource arrays = new FixtureSource("fx/app/a/Arrays.java", """
                package fx.app.a;

                import fx.app.b.Target;

                public class Arrays {
                    boolean all(boolean[] a, byte[] b, char[] c, short[] d, int[] e, long[] f, float[] g, double[] h,
                                Target[][] i) {
                        return java.nio.file.Files.exists(java.nio.file.Path.of("x")); // L:exists
                    }
                }
                """);

        assertThat(imported(arrays, "fx.app.a.Arrays").references()).contains(reference("fx.app.a.Arrays",
                "all(boolean[],byte[],char[],short[],int[],long[],float[],double[],fx.app.b.Target[][])",
                "java.nio.file.Files", "exists(java.nio.file.Path,java.nio.file.LinkOption[])", METHOD_CALL,
                arrays.lineOf("L:exists")));
    }

    @Test
    void anArrayOfAClassOutsideTheRootsStillReferencesItsElementType() {
        Path targets = compile();
        FixtureSource shelf = new FixtureSource("fx/app/a/Shelf.java", """
                package fx.app.a;

                import fx.app.b.Target;

                public class Shelf {
                    Target[] targets;

                    Object copy(Target[][] grid) {
                        return grid.clone(); // L:clone
                    }
                }
                """);

        List<CodeReference> references = ClassRootImporter.importRoots(
                        List.of(FixtureCompiler.compileAgainst(targets, shelf))).stream()
                .flatMap(imported -> imported.references().stream())
                .filter(reference -> reference.targetClass().startsWith("fx.app.b."))
                .toList();

        assertThat(references).containsExactly(
                reference("fx.app.a.Shelf", "copy(fx.app.b.Target[][])", "fx.app.b.Target", "", PARAMETER_TYPE, 0),
                reference("fx.app.a.Shelf", "copy(fx.app.b.Target[][])", "fx.app.b.Target", "clone()", METHOD_CALL,
                        shelf.lineOf("L:clone")),
                reference("fx.app.a.Shelf", "targets", "fx.app.b.Target", "", FIELD_TYPE, 0));
    }

    @Test
    void whatTheClassFileDoesNotRecordIsNotADependency() {
        // A known limit (docs/gaps.md): javac copies compile-time constants into the using class, and ArchUnit 1.5.1
        // reports no casts, local variable types, explicit type arguments of calls or array creation. If this test
        // fails, ArchUnit sees more than before: update docs/gaps.md and this test.
        FixtureSource invisible = new FixtureSource("fx/app/a/Invisible.java", """
                package fx.app.a;

                import fx.app.b.Constants;
                import fx.app.b.Target;
                import java.util.List;

                public class Invisible {
                    static final int COPIED = Constants.LIMIT;

                    Object cast(Object value) {
                        return (Target) value;
                    }

                    void local() {
                        Target target = null;
                        List<Target> targets = null;
                    }

                    Object array() {
                        return new Target[3];
                    }

                    Object typeArgument() {
                        return List.<Target>of();
                    }

                    int constant() {
                        return Constants.LIMIT + Constants.NAME.length();
                    }
                }
                """);
        FixtureSource constants = new FixtureSource("fx/app/b/Constants.java", """
                package fx.app.b;

                public class Constants {
                    public static final int LIMIT = 7;
                    public static final String NAME = "seven";
                }
                """);

        assertThat(referencesToTargets(invisible, "fx.app.a.Invisible", constants)).isEmpty();
    }

    @Test
    void classesAndReferencesAreSortedAndTheOrderOfRootsDoesNotMatter() {
        Path targets = compile();
        Path origins = FixtureCompiler.compile(INITIALISER);

        List<ImportedClass> forward = ClassRootImporter.importRoots(List.of(targets, origins));
        List<ImportedClass> backward = ClassRootImporter.importRoots(List.of(origins, targets, origins.resolve(".")));

        assertThat(backward).isEqualTo(forward);
        assertThat(forward).extracting(ImportedClass::name).isSorted();
        assertThat(forward).allSatisfy(imported -> assertThat(imported.references())
                .isSortedAccordingTo(CodeReference.ORDER)
                .doesNotHaveDuplicates());
    }

    @Test
    void aClassRootThatDoesNotExistIsAnError(@TempDir Path dir) {
        Path missing = dir.resolve("classes");

        assertThatThrownBy(() -> ClassRootImporter.importRoots(List.of(missing)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("class root does not exist: " + missing);
    }

    @Test
    void kindsHaveTheWordsReportsUse() {
        assertThat(METHOD_CALL.label()).isEqualTo("method call");
        assertThat(FIELD_TYPE_ARGUMENT.label()).isEqualTo("field type argument");
        assertThat(PARAMETER_ANNOTATION_MEMBER.label()).isEqualTo("parameter annotation member");
    }

    private static List<CodeReference> referencesToTargets(FixtureSource origin, String className,
                                                           FixtureSource... more) {
        FixtureSource[] sources = new FixtureSource[more.length + 1];
        sources[0] = origin;
        System.arraycopy(more, 0, sources, 1, more.length);
        return ClassRootImporter.importRoots(List.of(compile(sources))).stream()
                .filter(imported -> imported.name().equals(className))
                .flatMap(imported -> imported.references().stream())
                .filter(reference -> reference.targetClass().startsWith("fx.app.b."))
                .toList();
    }

    private static List<CodeReference> targetsOf(List<ImportedClass> classes, String className) {
        return classes.stream()
                .filter(imported -> imported.name().equals(className))
                .flatMap(imported -> imported.references().stream())
                .filter(reference -> reference.targetClass().startsWith("fx.app.b."))
                .toList();
    }

    private static ImportedClass imported(FixtureSource origin, String className) {
        return ClassRootImporter.importRoots(List.of(compile(origin))).stream()
                .filter(imported -> imported.name().equals(className))
                .findFirst()
                .orElseThrow();
    }

    private static Path compile(FixtureSource... origins) {
        return FixtureCompiler.compile(withTargets(origins));
    }

    private static FixtureSource[] withTargets(FixtureSource... origins) {
        FixtureSource[] all = new FixtureSource[TARGETS.length + origins.length];
        System.arraycopy(TARGETS, 0, all, 0, TARGETS.length);
        System.arraycopy(origins, 0, all, TARGETS.length, origins.length);
        return all;
    }

    private static CodeReference reference(String originClass, String originMember, String targetClass,
                                           String targetMember, ReferenceKind kind, int line) {
        String simpleName = originClass.substring(originClass.lastIndexOf('.') + 1);
        return reference(originClass, originMember, targetClass, targetMember, kind, line, simpleName + ".java");
    }

    private static CodeReference reference(String originClass, String originMember, String targetClass,
                                           String targetMember, ReferenceKind kind, int line, String sourceFile) {
        return new CodeReference(originClass, originMember, targetClass, targetMember, kind, sourceFile, line);
    }
}
