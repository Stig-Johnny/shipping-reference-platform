package io.github.stigjohnny.shipping.scenarios.support;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Which types can carry nothing but values: no reference through which a call could reach a module's behaviour. A
 * value-only type is a primitive, a JDK value class ({@code String}, the boxes, {@code UUID}, {@code BigDecimal},
 * {@code java.time} apart from {@code Clock}, ...), an enum or record whose fields are all value-only, a sealed
 * interface whose permitted types are all value-only, an array of value-only elements, or a JDK container ({@code List},
 * {@code Map}, {@code Optional}, {@code ScopedValue}, ...) whose type arguments are all value-only. A type variable or a
 * wildcard is not: it could hold anything.
 */
final class ValueTypes {

    private static final Set<Class<?>> LEAVES = Set.of(String.class, Boolean.class, Byte.class, Short.class,
            Integer.class, Long.class, Float.class, Double.class, Character.class, UUID.class, BigDecimal.class,
            BigInteger.class, Currency.class, Locale.class, Pattern.class, Class.class);
    private static final Set<Class<?>> CONTAINERS = Set.of(List.class, Set.class, SortedSet.class, Collection.class,
            Map.class, SortedMap.class, Optional.class, ScopedValue.class, ThreadLocal.class, AtomicReference.class);

    private final Map<Type, Boolean> known = new HashMap<>();

    /** Whether values of the type can hold only values; recursive types are value-only unless a part is not. */
    boolean valueOnly(Type type) {
        Boolean cached = known.get(type);
        if (cached != null) {
            return cached;
        }
        known.put(type, true);
        boolean result = switch (type) {
            case Class<?> c -> valueOnlyClass(c);
            case ParameterizedType p -> p.getRawType() instanceof Class<?> raw && CONTAINERS.contains(raw)
                    && Arrays.stream(p.getActualTypeArguments()).allMatch(this::valueOnly);
            case GenericArrayType array -> valueOnly(array.getGenericComponentType());
            default -> false;
        };
        known.put(type, result);
        return result;
    }

    private boolean valueOnlyClass(Class<?> c) {
        if (c.isPrimitive() || LEAVES.contains(c)) {
            return true;
        }
        if (c.getPackageName().equals("java.time")) {
            return !Clock.class.isAssignableFrom(c);
        }
        if (c.isArray()) {
            return valueOnly(c.getComponentType());
        }
        if (c.isRecord()) {
            return Arrays.stream(c.getRecordComponents()).map(RecordComponent::getGenericType).allMatch(this::valueOnly);
        }
        if (c.isEnum()) {
            return instanceFieldsValueOnly(c);
        }
        if (c.isInterface() && c.isSealed()) {
            return Arrays.stream(c.getPermittedSubclasses()).allMatch(this::valueOnly);
        }
        return false;
    }

    private boolean instanceFieldsValueOnly(Class<?> c) {
        for (Field field : c.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !valueOnly(field.getGenericType())) {
                return false;
            }
        }
        return true;
    }
}
