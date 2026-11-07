package io.github.stigjohnny.shipping.scenarios.support;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaModifier;
import io.github.stigjohnny.shipping.architecture.ClassDirectories;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.aop.framework.Advised;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Makes the recorder's coverage verifiable, so that what it cannot see is a finding and never silence.
 *
 * <p>Statically, every call and method or constructor reference from one module's main classes to another module's
 * classes is classified: a call of an {@code api} interface method, which the call observer can wrap; a use of a value
 * (a record, an enum, a sealed outcome or an exception), which is not an interaction; or anything else, which no proxy
 * can intercept and is a gap. At run time, every bean that implements an {@code api} interface must be wrapped; no
 * field of a module's bean may hold another module's {@code api} implementation unwrapped, or an event publisher that
 * is not the application context; a probe event published through the context must reach the publication observer;
 * and a listener method in a module's bean is a gap, because listener executions are not observed yet.
 */
public final class Coverage {

    /** A cross-module call site in the main classes and how the recorder covers it. */
    public record CallSite(String from, String to, String target, Category category, String location) {
    }

    /** How the recorder covers a call site. */
    public enum Category {
        INTERCEPTED, VALUE, NOT_INTERCEPTABLE
    }

    /** One verification and its result, for the report. */
    public record Check(String name, boolean passed, String detail) {
    }

    /** Everything coverage verification found for one run. */
    public record Result(List<CallSite> callSites, List<Check> checks, List<String> gaps, Set<String> observedApis,
            boolean publicationsObserved) {

        public Result {
            callSites = List.copyOf(callSites);
            checks = List.copyOf(checks);
            gaps = List.copyOf(gaps);
            observedApis = Set.copyOf(observedApis);
        }
    }

    private static List<CallSite> census;

    private Coverage() {
    }

    /** The call sites of the main classes; read once per test JVM, since the classes do not change during it. */
    static synchronized List<CallSite> callSites(ScenarioModel model, List<Path> mainClassDirectories) {
        if (census == null) {
            JavaClasses classes = ClassDirectories.read(mainClassDirectories);
            List<CallSite> sites = new ArrayList<>();
            for (JavaClass origin : classes) {
                Optional<String> from = model.moduleOf(origin.getName());
                if (from.isEmpty()) {
                    continue;
                }
                for (JavaCodeUnitAccess<?> access : origin.getCodeUnitAccessesFromSelf()) {
                    site(model, from.get(), access).ifPresent(sites::add);
                }
            }
            sites.sort(Comparator.comparing(CallSite::from).thenComparing(CallSite::to).thenComparing(CallSite::target)
                    .thenComparing(CallSite::location));
            census = List.copyOf(sites);
        }
        return census;
    }

    private static Optional<CallSite> site(ScenarioModel model, String from, JavaCodeUnitAccess<?> access) {
        JavaClass owner = access.getTargetOwner();
        Optional<String> to = model.moduleOf(owner.getName());
        if (to.isEmpty() || to.get().equals(from)) {
            return Optional.empty();
        }
        Category category;
        try {
            Class<?> type = Class.forName(owner.getName(), false, Coverage.class.getClassLoader());
            boolean value = type.isRecord() || type.isEnum() || Throwable.class.isAssignableFrom(type)
                    || (type.isInterface() && type.isSealed());
            boolean instanceMethod = access.getTarget().resolveMember()
                    .filter(m -> !m.getModifiers().contains(JavaModifier.STATIC) && !m.isConstructor()).isPresent();
            if (value) {
                category = Category.VALUE;
            } else if (type.isInterface() && instanceMethod && model.apiModuleOf(type).isPresent()) {
                category = Category.INTERCEPTED;
            } else {
                category = Category.NOT_INTERCEPTABLE;
            }
        } catch (ClassNotFoundException e) {
            category = Category.NOT_INTERCEPTABLE;
        }
        String location = access.getOrigin().getFullName() + " (" + access.getSourceCodeLocation().getSourceFileName()
                + ":" + access.getLineNumber() + ")";
        return Optional.of(new CallSite(from, to.get(), owner.getName() + "#" + access.getName(), category,
                location));
    }

    /** Verifies the observers of a refreshed context, before the scenario starts. */
    static Result verify(GenericApplicationContext context, Recorder recorder, ScenarioModel model,
            List<CallSite> callSites) {
        List<Check> checks = new ArrayList<>();
        List<String> gaps = new ArrayList<>(recorder.wrappingGaps());
        Set<String> observed = new TreeSet<>();
        List<String> wrapped = new ArrayList<>();
        List<Object> moduleBeans = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(name);
            Object target = target(bean, recorder);
            List<Class<?>> api = recorder.apiInterfaces(target.getClass());
            if (!api.isEmpty()) {
                if (recorder.observes(bean)) {
                    api.forEach(i -> observed.add(i.getName()));
                    wrapped.add(name + " (" + String.join(", ", api.stream().map(Class::getSimpleName).toList()) + ")");
                } else {
                    gaps.add("bean '" + name + "' implements " + api.stream().map(Class::getName).toList()
                            + ", but no observer wraps it");
                }
            }
            if (model.moduleOf(target.getClass().getName()).isPresent()) {
                moduleBeans.add(target);
            }
        }
        checks.add(new Check("Beans that implement an api interface are wrapped by the call observer",
                gaps.isEmpty(), wrapped.isEmpty() ? "none" : String.join("; ", wrapped)));

        int before = gaps.size();
        int fields = 0;
        for (Object bean : moduleBeans) {
            fields += bypasses(bean, context, recorder, model, gaps);
        }
        checks.add(new Check("No field of a module's bean bypasses an observer", gaps.size() == before,
                fields + " fields of " + moduleBeans.size() + " module beans read"));

        context.publishEvent(new Recorder.Probe());
        boolean publications = recorder.probeSeen();
        if (!publications) {
            gaps.add("publications are not observed: a probe event published through the application context did not "
                    + "reach the publication observer");
        }
        checks.add(new Check("A probe event published through the context reaches the publication observer",
                publications, publications ? "observed" : "not observed"));

        before = gaps.size();
        for (Object bean : moduleBeans) {
            ReflectionUtils.doWithMethods(bean.getClass(), method -> {
                if (AnnotatedElementUtils.hasAnnotation(method, EventListener.class)) {
                    gaps.add("listener " + bean.getClass().getName() + "#" + method.getName() + ": listener "
                            + "executions are not observed yet");
                }
            });
        }
        checks.add(new Check("No module bean has an event listener that the recorder cannot observe",
                gaps.size() == before, "listener methods are found by @EventListener and its meta-annotations"));

        List<CallSite> unwrappable = callSites.stream().filter(s -> s.category() == Category.NOT_INTERCEPTABLE)
                .toList();
        unwrappable.forEach(s -> gaps.add("call site " + s.location() + " uses " + s.target()
                + ", which no proxy can intercept"));
        long intercepted = callSites.stream().filter(s -> s.category() == Category.INTERCEPTED).count();
        checks.add(new Check("Every cross-module call site in the main classes calls an api interface or uses a value",
                unwrappable.isEmpty(), "call sites: " + callSites.size() + "; api interface calls: " + intercepted
                        + "; value uses: " + (callSites.size() - intercepted - unwrappable.size())
                        + "; not interceptable: " + unwrappable.size()));
        return new Result(callSites, checks, gaps, observed, publications);
    }

    private static Object target(Object bean, Recorder recorder) {
        if (recorder.observes(bean)) {
            try {
                Object target = ((Advised) bean).getTargetSource().getTarget();
                if (target != null) {
                    return target;
                }
            } catch (Exception e) {
                throw new IllegalStateException("the target of an observed bean cannot be read", e);
            }
        }
        return bean;
    }

    /** The bypasses held in one module bean's fields; returns how many fields were read. */
    private static int bypasses(Object bean, GenericApplicationContext context, Recorder recorder,
            ScenarioModel model, List<String> gaps) {
        String owner = model.moduleOf(bean.getClass().getName()).orElseThrow();
        int read = 0;
        for (Class<?> type = bean.getClass(); type != null && type.getName().startsWith(model.root() + ".");
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                read++;
                ReflectionUtils.makeAccessible(field);
                Object value = ReflectionUtils.getField(field, bean);
                if (value == null) {
                    continue;
                }
                String where = type.getName() + "." + field.getName();
                boolean otherModule = recorder.apiInterfaces(value.getClass()).stream()
                        .anyMatch(i -> !model.apiModuleOf(i).orElseThrow().equals(owner));
                if (otherModule && !recorder.observes(value)) {
                    gaps.add("bypass: " + where + " holds an implementation of another module's api interface that "
                            + "no observer wraps");
                }
                if (value instanceof ApplicationEventPublisher && !context.equals(value)) {
                    gaps.add("bypass: " + where + " publishes through an event publisher that is not the application "
                            + "context, so its publications never reach the publication observer");
                }
            }
        }
        return read;
    }
}
