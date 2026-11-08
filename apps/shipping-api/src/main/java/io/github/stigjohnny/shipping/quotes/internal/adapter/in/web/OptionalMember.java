package io.github.stigjohnny.shipping.quotes.internal.adapter.in.web;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.InvalidNullException;

/**
 * Reads an optional member of a request body, which the contract lets a client leave out but never set to {@code null}
 * (its conventions: absent values are omitted, and no value is null). An absent member is null in the record; an
 * explicit {@code null} fails as Jackson's InvalidNullException, which the HTTP edge reports as
 * {@code 400 validation-failed} naming the member. A present value is read by its type's own deserializer, with the
 * application's strict settings. Jackson's {@code @JsonSetter(nulls = Nulls.FAIL)} cannot do this for a record: it
 * fails an absent creator property as well. Every optional member of a request body carries
 * {@code @JsonDeserialize(using = OptionalMember.class)}.
 */
final class OptionalMember extends ValueDeserializer<Object> {

    private final @Nullable ValueDeserializer<Object> type;
    private final @Nullable BeanProperty member;

    /** The instance that Jackson creates from the annotation; it reads nothing until it has a member. */
    OptionalMember() {
        this(null, null);
    }

    private OptionalMember(@Nullable ValueDeserializer<Object> type, @Nullable BeanProperty member) {
        this.type = type;
        this.member = member;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        return new OptionalMember(context.findContextualValueDeserializer(property.getType(), property), property);
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) {
        return Objects.requireNonNull(type, "an optional member is read with its property").deserialize(parser,
                context);
    }

    /** An explicit {@code null}: never a value of the contract. */
    @Override
    public Object getNullValue(DeserializationContext context) {
        BeanProperty property = Objects.requireNonNull(member, "an optional member is read with its property");
        throw InvalidNullException.from(context, property.getFullName(), property.getType());
    }

    /** An absent member: the record's component is null. */
    @Override
    public @Nullable Object getAbsentValue(DeserializationContext context) {
        return null;
    }
}
