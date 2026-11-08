package io.github.stigjohnny.shipping.scenarios.support;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Checks the references reachable at the two run boundaries; this is not a proof of every runtime receiver.
 * From each module bean, and from each static field of the classes the census reads, it follows fields,
 * the arguments a lambda captured, the components of records, and the elements of JDK collections, maps, optionals and
 * arrays. It is a coverage gap when the walk reaches an object of another module that is no observer's proxy and is not
 * a value, an event publisher other than the application context, an application class that belongs to no module (its
 * code is not read by the census), or an object it cannot look into; and when it has to stop early. A field whose
 * declared type can hold values only is not followed.
 */
final class Reachability {

    /** The walk stops, with a gap, when it has seen this many object/owning-module pairs. */
    private static final int LIMIT = 200_000;
    private static final Set<Class<?>> OPAQUE_SAFE = Set.of(AtomicLong.class, AtomicInteger.class, AtomicBoolean.class,
            CountDownLatch.class);

    /** What one walk found: the gaps, and every class of a module that it reached. */
    record Result(List<String> gaps, Set<String> moduleClasses) {

        Result {
            gaps = List.copyOf(gaps);
            moduleClasses = Set.copyOf(moduleClasses);
        }
    }

    private final ScenarioModel model;
    private final Recorder recorder;
    private final ApplicationContext context;
    private final ValueTypes values = new ValueTypes();
    private final IdentityHashMap<Object, Set<String>> seen = new IdentityHashMap<>();
    private int visits;
    private final Set<String> gaps = new TreeSet<>();
    private final Set<String> moduleClasses = new TreeSet<>();

    Reachability(ScenarioModel model, Recorder recorder, ApplicationContext context) {
        this.model = model;
        this.recorder = recorder;
        this.context = context;
    }

    /** Walks from the module beans and from the static fields of the classes given. */
    Result walk(Map<String, Object> moduleBeans, Collection<Class<?>> staticRoots) {
        moduleBeans.forEach((name, bean) -> {
            String module = moduleOf(bean.getClass()).orElseThrow();
            moduleClasses.add(className(bean.getClass()));
            fields(bean, bean.getClass(), "bean '" + name + "'", module);
        });
        for (Class<?> type : staticRoots) {
            Optional<String> module = moduleOf(type);
            if (module.isEmpty()) {
                continue;
            }
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && !values.valueOnly(field.getGenericType())) {
                    read(null, field, type.getName() + "." + field.getName()).ifPresent(value ->
                            visit(value, type.getName() + "." + field.getName(), module.get()));
                }
            }
        }
        return new Result(List.copyOf(gaps), moduleClasses);
    }

    /** The module of a class, of a lambda's host class for a lambda, or empty. */
    Optional<String> moduleOf(Class<?> type) {
        return model.moduleOf(className(type));
    }

    /** A lambda's class is hidden and named after its host class; its code is in the host class. */
    static String className(Class<?> type) {
        String name = type.getName();
        int lambda = name.indexOf("$$Lambda");
        return lambda < 0 ? name : name.substring(0, lambda);
    }

    private void visit(Object value, String path, String owner) {
        if (value == null) {
            return;
        }
        Set<String> owners = seen.get(value);
        if (owners != null && owners.contains(owner)) {
            return;
        }
        if (visits >= LIMIT) {
            gaps.add("the walk stopped after " + LIMIT + " object/owning-module pairs, so what lies beyond them is "
                    + "not checked");
            return;
        }
        seen.computeIfAbsent(value, ignored -> new TreeSet<>()).add(owner);
        visits++;
        Class<?> type = value.getClass();
        if (recorder.observes(value) || context.equals(value) || values.valueOnly(type)
                || OPAQUE_SAFE.contains(type)) {
            return;
        }
        if (value instanceof ApplicationEventPublisher) {
            gaps.add("bypass: " + path + " is an event publisher other than the application context, so its "
                    + "publications never reach the publication observer");
            return;
        }
        String name = className(type);
        if (name.startsWith(model.root() + ".")) {
            Optional<String> module = model.moduleOf(name);
            if (module.isEmpty()) {
                gaps.add(path + " holds " + name + ", a class outside every module: its code is not read by the "
                        + "census");
                return;
            }
            moduleClasses.add(name);
            if (!module.get().equals(owner)) {
                gaps.add("bypass: " + path + " reaches " + name + ", an object of module " + module.get()
                        + " that no observer wraps");
                return;
            }
            fields(value, type, path, owner);
        } else if (type.isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                visit(Array.get(value, i), path + "[" + i + "]", owner);
            }
        } else if (value instanceof Collection<?> collection && type.getName().startsWith("java.util.")) {
            int i = 0;
            for (Object element : collection) {
                visit(element, path + "[" + i++ + "]", owner);
            }
        } else if (value instanceof Map<?, ?> map && type.getName().startsWith("java.util.")) {
            map.forEach((k, v) -> {
                visit(k, path + ".key", owner);
                visit(v, path + ".value", owner);
            });
        } else if (value instanceof Optional<?> optional) {
            optional.ifPresent(v -> visit(v, path + ".value", owner));
        } else if (value instanceof AtomicReference<?> reference) {
            visit(reference.get(), path + ".value", owner);
        } else if (type.getPackageName().startsWith("java.time")) {
            return;
        } else {
            gaps.add(path + " holds a " + type.getName() + ", which the walk cannot look into");
        }
    }

    /** Follows the instance fields of an application object, its superclasses within the application included. */
    private void fields(Object value, Class<?> type, String path, String owner) {
        for (Class<?> c = type; c != null && (c.isHidden() || className(c).startsWith(model.root() + "."));
                c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || values.valueOnly(field.getGenericType())) {
                    continue;
                }
                String where = path + " -> " + (c.isHidden() ? "captured " : "") + field.getName();
                read(value, field, where).ifPresent(v -> visit(v, where, owner));
            }
        }
    }

    private Optional<Object> read(Object target, Field field, String where) {
        try {
            field.setAccessible(true);
            return Optional.ofNullable(field.get(target));
        } catch (IllegalAccessException | InaccessibleObjectException | ExceptionInInitializerError e) {
            gaps.add(where + " cannot be read, so what it holds is not checked");
            return Optional.empty();
        }
    }

    /** The classes whose static fields the walk reads: those given, loaded and initialised by their names. */
    static List<Class<?>> classes(Collection<String> names, List<String> gaps) {
        List<Class<?>> classes = new ArrayList<>();
        Deque<String> todo = new ArrayDeque<>(names);
        while (!todo.isEmpty()) {
            String name = todo.pop();
            try {
                classes.add(Class.forName(name, true, Reachability.class.getClassLoader()));
            } catch (ClassNotFoundException | LinkageError e) {
                gaps.add(name + " cannot be loaded, so its static fields are not checked");
            }
        }
        return classes;
    }
}
