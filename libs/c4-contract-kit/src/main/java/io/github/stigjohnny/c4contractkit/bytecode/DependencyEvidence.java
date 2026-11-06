package io.github.stigjohnny.c4contractkit.bytecode;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaConstructorReference;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.domain.Source;
import com.tngtech.archunit.core.domain.SourceCodeLocation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns the direct dependencies that ArchUnit reports for a class ({@link JavaClass#getDirectDependenciesFromSelf()})
 * into evidence: one {@link CodeReference} per dependency, with the origin member, the target member, the kind and
 * the line. Nothing is added to what ArchUnit reports, and no transitive dependency is ever computed.
 *
 * <p>ArchUnit hands the member and the kind of most dependencies only in its description, such as
 * {@code "Field <a.A.f> has type <b.B> in (A.java:0)"}. The origin part is matched against the descriptions of the
 * origin's own members, and the rest against the phrases ArchUnit 1.5.1 uses. Accesses (calls, field accesses and
 * method references) are read from ArchUnit's access objects instead.
 */
final class DependencyEvidence {

    private static final List<Phrase> PHRASES = List.of(
            new Phrase("has type <", ReferenceKind.FIELD_TYPE),
            new Phrase("has generic type <", ReferenceKind.FIELD_TYPE_ARGUMENT),
            new Phrase("has return type <", ReferenceKind.RETURN_TYPE),
            new Phrase("has generic return type <", ReferenceKind.RETURN_TYPE_ARGUMENT),
            new Phrase("has parameter of type <", ReferenceKind.PARAMETER_TYPE),
            new Phrase("has generic parameter type <", ReferenceKind.PARAMETER_TYPE_ARGUMENT),
            new Phrase("has generic superclass <", ReferenceKind.SUPERCLASS_TYPE_ARGUMENT),
            new Phrase("has generic interface <", ReferenceKind.INTERFACE_TYPE_ARGUMENT),
            new Phrase("has type parameter '", ReferenceKind.TYPE_PARAMETER_BOUND),
            new Phrase("throws type <", ReferenceKind.THROWS),
            new Phrase("catches type <", ReferenceKind.CATCH),
            new Phrase("checks instanceof <", ReferenceKind.INSTANCEOF),
            new Phrase("references class object <", ReferenceKind.CLASS_LITERAL),
            new Phrase("is annotated with <", ReferenceKind.ANNOTATION),
            new Phrase("has annotation member of type <", ReferenceKind.ANNOTATION_MEMBER));

    /** Where a description of an origin member can end: ArchUnit puts every name in angle brackets. */
    private static final Pattern END_OF_DESCRIPTION = Pattern.compile("> ");

    /** ArchUnit repeats every dependency on an array type for its element types; the array is mapped to those. */
    private static final String COMPONENT_TYPE = "depends on component type <";

    private final JavaClass origin;
    private final Map<String, Origin> origins = new HashMap<>();
    private final Map<String, JavaAccess<?>> accesses;

    private DependencyEvidence(JavaClass origin) {
        this.origin = origin;
        origins.put(origin.getDescription(), new Origin("", false));
        origins.put("Interface <" + origin.getName() + ">", new Origin("", false));
        for (JavaField field : origin.getFields()) {
            origins.put(field.getDescription(), new Origin(field.getName(), false));
        }
        for (JavaCodeUnit codeUnit : origin.getCodeUnits()) {
            String signature = signature(codeUnit.getName(), codeUnit.getRawParameterTypes());
            origins.put(codeUnit.getDescription(), new Origin(signature, false));
            for (JavaParameter parameter : codeUnit.getParameters()) {
                origins.put(parameter.getDescription(), new Origin(signature, true));
            }
        }
        this.accesses = origin.getAccessesFromSelf().stream()
                .collect(Collectors.toMap(JavaAccess::getDescription, access -> access, (first, second) -> first));
    }

    static ImportedClass of(JavaClass javaClass) {
        DependencyEvidence evidence = new DependencyEvidence(javaClass);
        List<CodeReference> references = javaClass.getDirectDependenciesFromSelf().stream()
                .map(evidence::reference)
                .flatMap(Optional::stream)
                .distinct()
                .sorted(CodeReference.ORDER)
                .toList();
        return new ImportedClass(javaClass.getName(), javaClass.getSimpleName().equals("package-info"),
                javaClass.getEnclosingClass().map(JavaClass::getName), references);
    }

    private Optional<CodeReference> reference(Dependency dependency) {
        String target = elementClassName(dependency.getTargetClass());
        if (target.isEmpty() || target.equals(origin.getName())) {
            return Optional.empty();
        }
        SourceCodeLocation location = dependency.getSourceCodeLocation();
        String sourceFile = location.getSourceClass().getSource().flatMap(Source::getFileName).orElse("");
        JavaAccess<?> access = accesses.get(dependency.getDescription());
        if (access != null) {
            return Optional.of(new CodeReference(origin.getName(), signature(access.getOrigin()), target,
                    targetMember(access.getTarget()), accessKind(access), sourceFile, location.getLineNumber()));
        }
        String description = dependency.getDescription();
        String originDescription = originDescriptionOf(description);
        Origin member = origins.getOrDefault(originDescription, new Origin("", false));
        String phrase = description.substring(Math.min(description.length(), originDescription.length() + 1));
        if (phrase.startsWith(COMPONENT_TYPE)) {
            return Optional.empty();
        }
        return Optional.of(new CodeReference(origin.getName(), member.signature(), target, "",
                kind(phrase, member.parameter(), dependency.getTargetClass()), sourceFile, location.getLineNumber()));
    }

    /** The longest description of an origin member that the dependency description starts with, followed by " ". */
    private String originDescriptionOf(String description) {
        String found = "";
        Matcher end = END_OF_DESCRIPTION.matcher(description);
        while (end.find()) {
            String candidate = description.substring(0, end.start() + 1);
            if (origins.containsKey(candidate)) {
                found = candidate;
            }
        }
        return found;
    }

    private ReferenceKind kind(String phrase, boolean fromParameter, JavaClass rawTarget) {
        if (phrase.startsWith("extends ") || phrase.startsWith("implements ")) {
            return !origin.isInterface() && origin.getRawInterfaces().contains(rawTarget)
                    ? ReferenceKind.IMPLEMENTS : ReferenceKind.EXTENDS;
        }
        ReferenceKind kind = PHRASES.stream()
                .filter(candidate -> phrase.startsWith(candidate.prefix()))
                .map(Phrase::kind)
                .findFirst()
                .orElse(ReferenceKind.OTHER);
        if (fromParameter && kind == ReferenceKind.ANNOTATION) {
            return ReferenceKind.PARAMETER_ANNOTATION;
        }
        if (fromParameter && kind == ReferenceKind.ANNOTATION_MEMBER) {
            return ReferenceKind.PARAMETER_ANNOTATION_MEMBER;
        }
        return kind;
    }

    private static ReferenceKind accessKind(JavaAccess<?> access) {
        return switch (access) {
            case JavaMethodCall _ -> ReferenceKind.METHOD_CALL;
            case JavaConstructorCall _ -> ReferenceKind.CONSTRUCTOR_CALL;
            case JavaMethodReference _ -> ReferenceKind.METHOD_REFERENCE;
            case JavaConstructorReference _ -> ReferenceKind.CONSTRUCTOR_REFERENCE;
            case JavaFieldAccess field -> field.getAccessType() == JavaFieldAccess.AccessType.SET
                    ? ReferenceKind.FIELD_WRITE : ReferenceKind.FIELD_READ;
            default -> ReferenceKind.OTHER;
        };
    }

    private static String targetMember(AccessTarget target) {
        return target instanceof AccessTarget.CodeUnitAccessTarget codeUnit
                ? signature(codeUnit.getName(), codeUnit.getRawParameterTypes())
                : target.getName();
    }

    private static String signature(JavaCodeUnit codeUnit) {
        return signature(codeUnit.getName(), codeUnit.getRawParameterTypes());
    }

    private static String signature(String name, List<JavaClass> parameterTypes) {
        return parameterTypes.stream().map(DependencyEvidence::typeName)
                .collect(Collectors.joining(",", name + "(", ")"));
    }

    /**
     * The binary name of a type, or of an array type's element; empty for an array of primitives. It is read from the
     * name, because ArchUnit leaves the component type of an array that occurs only in another class's signature
     * unresolved.
     */
    private static String elementClassName(JavaClass type) {
        String name = type.getName();
        if (!name.startsWith("[")) {
            return name;
        }
        String element = name.substring(name.lastIndexOf('[') + 1);
        return element.startsWith("L") ? element.substring(1, element.length() - 1) : "";
    }

    private static String typeName(JavaClass type) {
        return javaName(type.getName());
    }

    /** A binary type name as Java source writes it: {@code int[][]} for {@code [[I}, {@code a.B[]} for {@code [La.B;}. */
    private static String javaName(String binaryName) {
        if (!binaryName.startsWith("[")) {
            return binaryName;
        }
        String element = binaryName.substring(1);
        String elementName = switch (element.charAt(0)) {
            case '[' -> javaName(element);
            case 'L' -> element.substring(1, element.length() - 1);
            case 'Z' -> "boolean";
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'S' -> "short";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'F' -> "float";
            case 'D' -> "double";
            default -> element;
        };
        return elementName + "[]";
    }

    /** A member of the origin class; {@code parameter} when the dependency starts at one of its parameters. */
    private record Origin(String signature, boolean parameter) {
    }

    private record Phrase(String prefix, ReferenceKind kind) {
    }
}
