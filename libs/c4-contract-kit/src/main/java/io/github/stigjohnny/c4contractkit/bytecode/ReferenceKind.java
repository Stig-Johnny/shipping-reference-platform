package io.github.stigjohnny.c4contractkit.bytecode;

/** What a reference from one class to another is; {@link #label()} is the word reports use. */
public enum ReferenceKind {
    METHOD_CALL("method call"),
    CONSTRUCTOR_CALL("constructor call"),
    FIELD_READ("field read"),
    FIELD_WRITE("field write"),
    METHOD_REFERENCE("method reference"),
    CONSTRUCTOR_REFERENCE("constructor reference"),
    EXTENDS("extends"),
    IMPLEMENTS("implements"),
    FIELD_TYPE("field type"),
    FIELD_TYPE_ARGUMENT("field type argument"),
    RETURN_TYPE("return type"),
    RETURN_TYPE_ARGUMENT("return type argument"),
    PARAMETER_TYPE("parameter type"),
    PARAMETER_TYPE_ARGUMENT("parameter type argument"),
    SUPERCLASS_TYPE_ARGUMENT("superclass type argument"),
    INTERFACE_TYPE_ARGUMENT("interface type argument"),
    TYPE_PARAMETER_BOUND("type parameter bound"),
    THROWS("throws"),
    CATCH("catch"),
    INSTANCEOF("instanceof"),
    CLASS_LITERAL("class literal"),
    ANNOTATION("annotation"),
    ANNOTATION_MEMBER("annotation member"),
    PARAMETER_ANNOTATION("parameter annotation"),
    PARAMETER_ANNOTATION_MEMBER("parameter annotation member"),
    OTHER("other");

    private final String label;

    ReferenceKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
