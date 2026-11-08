package io.github.stigjohnny.shipping.scenarios.support;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import io.github.stigjohnny.shipping.architecture.ClassDirectories;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Detects coverage gaps in a bounded census and object walk before and after the run. This is not a universal
 * receiver or task-lifetime proof; the unexamined cases are listed in docs/gaps.md. The checks:
 *
 * <ul>
 *   <li>Every singleton that implements an {@code api} interface is wrapped by the call observer, and no module
 *       object is a singleton registered as an object, which no post-processor sees.
 *   <li>Every object reachable from a module bean or from a static field of a class the run uses is a value, an
 *       object of the same module, an observer's proxy or the application context ({@link Reachability}).
 *   <li>The census reads every class of a module that the run reaches, from the main classes and from the test
 *       classes (altered implementations, stores, fixtures); a class it cannot find on disk is a gap. In those
 *       classes, every use of another module is a call of an {@code api} interface method whose parameters and
 *       result are values only (the walk separately checks reachable receivers), or a
 *       value use: a constructor, a record accessor, {@code equals}, {@code hashCode}, {@code toString} or an enum's
 *       built-in method on a type that holds values only. Anything else is a gap, and so are a method other than a
 *       constructor that takes another module's {@code api} service as a parameter, and any use of reflection,
 *       method handles, Spring AOP or bean lookup, which could reach an implementation behind its proxy.
 *   <li>A probe event published through the context reaches the publication observer, and no module bean has an
 *       event listener, since listener executions are not observed yet.
 * </ul>
 */
public final class Coverage {

    /** A cross-module use in a class of the run and how the recorder covers it. */
    public record CallSite(String from, String to, String target, Category category, String location) {
    }

    /** How the recorder covers a use. */
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

        /** The same result with more gaps and checks, found after the run. */
        Result with(List<Check> more, List<String> moreGaps) {
            List<Check> allChecks = new ArrayList<>(checks);
            allChecks.addAll(more);
            List<String> allGaps = new ArrayList<>(gaps);
            moreGaps.stream().filter(g -> !allGaps.contains(g)).forEach(allGaps::add);
            return new Result(callSites, allChecks, allGaps, observedApis, publicationsObserved);
        }
    }

    private static final List<String> FORBIDDEN = List.of("java.lang.reflect.", "java.lang.invoke.",
            "org.springframework.");
    /** The one use of Spring that module code makes: publishing an event through the context. */
    private static final String PUBLISH = "org.springframework.context.ApplicationEventPublisher#publishEvent";
    private static final Set<String> VALUE_METHODS = Set.of("equals", "hashCode", "toString");
    private static final Set<String> ENUM_METHODS = Set.of("values", "valueOf", "name", "ordinal", "compareTo",
            "getDeclaringClass", "describeConstable");
    /** These Thread operations cannot start or enqueue work; all other uses of Thread and its builders fail closed. */
    private static final Set<String> THREAD_INSPECTION = Set.of("currentThread", "interrupt", "interrupted",
            "isInterrupted", "isVirtual", "threadId", "getName", "getState", "isAlive", "join", "sleep", "yield",
            "onSpinWait");
    /** Synchronization primitives used by the fixtures; other concurrent APIs are outside this synchronous slice. */
    private static final Set<String> SYNCHRONOUS_CONCURRENT = Set.of("java.util.concurrent.CountDownLatch",
            "java.util.concurrent.TimeUnit");

    private static JavaClasses main;
    private static JavaClasses test;

    private Coverage() {
    }

    /** Verifies the observers of a refreshed context, before the scenario starts. */
    static Result verify(GenericApplicationContext context, Recorder recorder, ScenarioModel model) {
        List<Check> checks = new ArrayList<>();
        List<String> gaps = new ArrayList<>(recorder.wrappingGaps());
        Set<String> observed = new TreeSet<>();
        Map<String, Object> moduleBeans = new LinkedHashMap<>();
        int before = gaps.size();
        List<String> wrapped = singletons(context, recorder, model, observed, moduleBeans, gaps);
        checks.add(new Check("Every singleton that implements an api interface is wrapped by the call observer, and no "
                + "module object is a singleton registered as an object", gaps.size() == before,
                wrapped.isEmpty() ? "none" : String.join("; ", wrapped)));

        Reachability.Result reached = new Reachability(model, recorder, context).walk(moduleBeans, List.of());
        Census census = census(model, reached.moduleClasses(), gaps, checks);
        walk(context, recorder, model, moduleBeans, census.scope(), gaps, checks, "before the run");

        context.publishEvent(new Recorder.Probe());
        boolean publications = recorder.probeSeen();
        if (!publications) {
            gaps.add("publications are not observed: a probe event published through the application context did not "
                    + "reach the publication observer");
        }
        checks.add(new Check("A probe event published through the context reaches the publication observer",
                publications, publications ? "observed" : "not observed"));

        before = gaps.size();
        for (Object bean : moduleBeans.values()) {
            ReflectionUtils.doWithMethods(bean.getClass(), method -> {
                if (AnnotatedElementUtils.hasAnnotation(method, EventListener.class)) {
                    gaps.add("listener " + bean.getClass().getName() + "#" + method.getName() + ": listener "
                            + "executions are not observed yet");
                }
            });
        }
        checks.add(new Check("No module bean has an event listener that the recorder cannot observe",
                gaps.size() == before, "listener methods are found by @EventListener and its meta-annotations"));
        return new Result(census.sites(), checks, gaps, observed, publications);
    }

    /**
     * Verifies again after the run: the singletons, what the run can reach now, the census of any class that has
     * become reachable, and the threads the run left behind.
     */
    static Result afterRun(Result before, GenericApplicationContext context, Recorder recorder, ScenarioModel model) {
        List<Check> checks = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        Map<String, Object> moduleBeans = new LinkedHashMap<>();
        singletons(context, recorder, model, new TreeSet<>(), moduleBeans, gaps);
        Reachability.Result reached = new Reachability(model, recorder, context).walk(moduleBeans, List.of());
        Census census = census(model, reached.moduleClasses(), gaps, new ArrayList<>());
        walk(context, recorder, model, moduleBeans, census.scope(), gaps, checks, "after the run");
        List<String> threads = recorder.threadsLeftRunning();
        threads.forEach(t -> gaps.add("thread '" + t + "' started during the run and is still running: work it does "
                + "after the recording ends would not be seen"));
        checks.add(new Check("No new non-system platform thread outside the stimuli remains at this snapshot",
                threads.isEmpty(),
                threads.isEmpty() ? "none" : String.join(", ", threads)));
        return before.with(checks, gaps);
    }

    private static List<String> singletons(GenericApplicationContext context, Recorder recorder, ScenarioModel model,
            Set<String> observed, Map<String, Object> moduleBeans, List<String> gaps) {
        DefaultListableBeanFactory factory = context.getDefaultListableBeanFactory();
        List<String> wrapped = new ArrayList<>();
        for (String name : factory.getSingletonNames()) {
            Object bean = factory.getSingleton(name);
            if (bean == null) {
                continue;
            }
            Object target = target(bean, recorder);
            List<Class<?>> api = recorder.apiInterfaces(target.getClass());
            boolean moduleClass = model.moduleOf(Reachability.className(target.getClass())).isPresent();
            if (!factory.containsBeanDefinition(name) && (moduleClass || !api.isEmpty())) {
                gaps.add("singleton '" + name + "' was registered as an object, so no post-processor, and no observer, "
                        + "saw it");
            }
            if (!api.isEmpty()) {
                if (recorder.observes(bean)) {
                    api.forEach(i -> observed.add(i.getName()));
                    wrapped.add(name + " (" + String.join(", ", api.stream().map(Class::getSimpleName).toList()) + ")");
                } else {
                    gaps.add("bean '" + name + "' implements " + api.stream().map(Class::getName).toList()
                            + ", but no observer wraps it");
                }
            }
            if (moduleClass) {
                moduleBeans.put(name, target);
            }
        }
        return wrapped;
    }

    private static void walk(GenericApplicationContext context, Recorder recorder, ScenarioModel model,
            Map<String, Object> moduleBeans, Set<String> scope, List<String> gaps, List<Check> checks, String when) {
        List<String> loading = new ArrayList<>();
        List<Class<?>> statics = Reachability.classes(scope, loading);
        Reachability.Result walk = new Reachability(model, recorder, context).walk(moduleBeans, statics);
        gaps.addAll(loading);
        gaps.addAll(walk.gaps());
        checks.add(new Check("The bounded receiver walk " + when + " found no bypass, unreadable object or "
                + "traversal-limit gap",
                walk.gaps().isEmpty() && loading.isEmpty(), moduleBeans.size() + " module beans, "
                        + statics.size() + " classes' static fields"));
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

    private static synchronized JavaClasses imported(boolean mainClasses) {
        if (main == null) {
            main = ClassDirectories.read(ClassDirectories.main());
            test = ClassDirectories.read(ClassDirectories.test());
        }
        return mainClasses ? main : test;
    }

    /**
     * The census of the run: every main class of a module, and every test class of a module that the run reached or
     * that such a class depends on, read from its class file.
     */
    private static Census census(ScenarioModel model, Set<String> reached, List<String> gaps, List<Check> checks) {
        JavaClasses mainClasses = imported(true);
        JavaClasses testClasses = imported(false);
        Map<String, JavaClass> scope = new LinkedHashMap<>();
        mainClasses.stream().filter(c -> model.moduleOf(c.getName()).isPresent()).forEach(c -> scope.put(c.getName(),
                c));
        List<String> todo = new ArrayList<>(reached);
        int before = gaps.size();
        while (!todo.isEmpty()) {
            String name = todo.removeLast();
            if (scope.containsKey(name)) {
                continue;
            }
            Optional<JavaClass> found = testClasses.stream().filter(c -> c.getName().equals(name)).findFirst();
            if (found.isEmpty()) {
                gaps.add("class " + name + " takes part in the run, but no class file of it is in the main or test "
                        + "class directories, so the census cannot read it");
                continue;
            }
            scope.put(name, found.get());
            for (Dependency dependency : found.get().getDirectDependenciesFromSelf()) {
                String target = dependency.getTargetClass().getBaseComponentType().getName();
                if (model.moduleOf(target).isPresent() && !scope.containsKey(target)) {
                    todo.add(target);
                }
            }
        }
        boolean found = gaps.size() == before;
        List<CallSite> sites = new ArrayList<>();
        ValueTypes values = new ValueTypes();
        for (JavaClass origin : scope.values()) {
            String from = model.moduleOf(origin.getName()).orElseThrow();
            for (JavaCodeUnitAccess<?> access : origin.getCodeUnitAccessesFromSelf()) {
                forbidden(access.getTargetOwner().getName(), access.getName(), access.getOrigin(),
                        access.getLineNumber(), "(" + access.getTarget().getRawParameterTypes().stream()
                                .map(Coverage::descriptor).collect(java.util.stream.Collectors.joining()) + ")"
                                + descriptor(access.getTarget().getRawReturnType()),
                        access.getClass().getSimpleName(), gaps);
                site(model, values, from, access).ifPresent(sites::add);
            }
            for (JavaFieldAccess access : origin.getFieldAccessesFromSelf()) {
                forbidden(access.getTargetOwner().getName(), access.getName(), access.getOrigin(),
                        access.getLineNumber(), descriptor(access.getTarget().getRawType()),
                        "field " + access.getAccessType(), gaps);
                field(model, values, from, access).ifPresent(sites::add);
            }
            for (JavaCodeUnit unit : origin.getCodeUnits()) {
                if (!unit.isConstructor()) {
                    serviceParameters(model, values, from, unit, gaps);
                }
            }
        }
        sites.sort(Comparator.comparing(CallSite::from).thenComparing(CallSite::to).thenComparing(CallSite::target)
                .thenComparing(CallSite::location));
        List<CallSite> unwrappable = sites.stream().filter(s -> s.category() == Category.NOT_INTERCEPTABLE).toList();
        unwrappable.forEach(s -> gaps.add("call site " + s.location() + " uses " + s.target()
                + ", which no proxy can intercept"));
        long intercepted = sites.stream().filter(s -> s.category() == Category.INTERCEPTED).count();
        checks.add(new Check("The census reads all main module classes and reached test module classes with their "
                + "module dependencies", found,
                scope.size() + " classes: " + mainClasses.stream().filter(c -> model.moduleOf(c.getName())
                        .isPresent()).count() + " main, the rest from the test classes"));
        checks.add(new Check("The examined call sites use supported cross-module signatures and have no forbidden "
                + "access or unsupported async dispatch", unwrappable.isEmpty() && gaps.size() == before,
                "call sites: " + sites.size() + "; api interface calls: " + intercepted + "; value uses: "
                        + (sites.size() - intercepted - unwrappable.size()) + "; not interceptable: "
                        + unwrappable.size()));
        return new Census(sites, scope.keySet());
    }

    /** The census's call sites, and the classes it read. */
    private record Census(List<CallSite> sites, Set<String> scope) {

        Census {
            sites = List.copyOf(sites);
            scope = Set.copyOf(scope);
        }
    }

    private static void forbidden(String owner, String member, JavaCodeUnit origin, int line,
            String descriptor, String kind, List<String> gaps) {
        if (unsupportedDispatch(owner, member)) {
            gaps.add("call site " + origin.getFullName() + " (line " + line + ") uses " + owner + "#" + member
                    + ": asynchronous dispatch is unsupported; task completion cannot be proven by this recorder");
        }
        if (FORBIDDEN.stream().anyMatch(owner::startsWith) && !PUBLISH.equals(owner + "#" + member)) {
            gaps.add("call site " + origin.getFullName() + " [" + origin.getDescriptor() + "] (line " + line
                    + ") uses " + owner + "#" + member + descriptor + " [" + kind
                    + "], which could reach an implementation behind its proxy");
        }
    }

    /** Refuse task-capable API families even when the task happens to complete before the recording ends. */
    private static boolean unsupportedDispatch(String owner, String member) {
        Optional<Class<?>> type = load(owner);
        if (type.filter(Thread.class::isAssignableFrom).isPresent()) {
            return !THREAD_INSPECTION.contains(member);
        }
        if (owner.startsWith("java.lang.Thread$Builder")
                || type.filter(t -> java.util.concurrent.Executor.class.isAssignableFrom(t)
                        || java.util.concurrent.ThreadFactory.class.isAssignableFrom(t)
                        || java.util.concurrent.Future.class.isAssignableFrom(t)
                        || java.util.concurrent.CompletionStage.class.isAssignableFrom(t)
                        || java.util.Timer.class.isAssignableFrom(t)
                        || java.util.TimerTask.class.isAssignableFrom(t)).isPresent()) {
            return true;
        }
        if (owner.startsWith("java.util.concurrent.") && !SYNCHRONOUS_CONCURRENT.contains(owner)
                && !owner.startsWith("java.util.concurrent.atomic.")
                && !owner.startsWith("java.util.concurrent.locks.")) {
            return true;
        }
        return (member.equals("parallel") || member.equals("parallelStream"))
                || owner.equals("java.util.stream.StreamSupport")
                || (owner.equals("java.util.Arrays") && member.startsWith("parallel"))
                || owner.startsWith("java.nio.channels.Asynchronous")
                || (owner.startsWith("java.net.http.") && member.equals("sendAsync"));
    }

    private static Optional<CallSite> site(ScenarioModel model, ValueTypes values, String from,
            JavaCodeUnitAccess<?> access) {
        String owner = access.getTargetOwner().getName();
        Optional<String> to = model.moduleOf(owner);
        if (to.isEmpty() || to.get().equals(from)) {
            return Optional.empty();
        }
        Category category = Category.NOT_INTERCEPTABLE;
        Optional<Class<?>> type = load(owner);
        if (type.isPresent() && model.apiModuleOf(type.get()).filter(to.get()::equals).isPresent()) {
            String member = access.getName();
            boolean constructor = "<init>".equals(member);
            List<String> parameters = access.getTarget().getRawParameterTypes().stream().map(JavaClass::getName)
                    .toList();
            Optional<Method> method = method(type.get(), member, parameters);
            if (values.valueOnly(type.get()) && valueUse(type.get(), member, constructor, parameters)) {
                category = Category.VALUE;
            } else if (type.get().isInterface() && !type.get().isSealed() && !constructor
                    && method.filter(m -> !Modifier.isStatic(m.getModifiers())).filter(m -> values.valueOnly(
                            m.getGenericReturnType()) && Arrays.stream(m.getGenericParameterTypes())
                                    .allMatch(values::valueOnly)).isPresent()) {
                category = Category.INTERCEPTED;
            }
        }
        String location = access.getOrigin().getFullName() + " (" + access.getSourceCodeLocation().getSourceFileName()
                + ":" + access.getLineNumber() + ")";
        return Optional.of(new CallSite(from, to.get(), owner + "#" + access.getName(), category, location));
    }

    private static Optional<CallSite> field(ScenarioModel model, ValueTypes values, String from,
            JavaFieldAccess access) {
        String owner = access.getTargetOwner().getName();
        Optional<String> to = model.moduleOf(owner);
        if (to.isEmpty() || to.get().equals(from)) {
            return Optional.empty();
        }
        Optional<Class<?>> type = load(owner);
        boolean enumConstant = type.filter(Class::isEnum).filter(values::valueOnly)
                .map(t -> Arrays.stream(t.getEnumConstants()).map(e -> ((Enum<?>) e).name())
                        .anyMatch(access.getName()::equals)).orElse(false);
        String location = access.getOrigin().getFullName() + " (" + access.getSourceCodeLocation().getSourceFileName()
                + ":" + access.getLineNumber() + ")";
        return Optional.of(new CallSite(from, to.get(), owner + "." + access.getName(),
                enumConstant ? Category.VALUE : Category.NOT_INTERCEPTABLE, location));
    }

    /** A constructor, a record accessor, equals, hashCode, toString, an enum's built-ins, a throwable's methods. */
    private static boolean valueUse(Class<?> type, String member, boolean constructor, List<String> parameters) {
        if (constructor || VALUE_METHODS.contains(member)) {
            return true;
        }
        if (type.isRecord() && parameters.isEmpty()) {
            return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).anyMatch(member::equals);
        }
        if (type.isEnum()) {
            return ENUM_METHODS.contains(member);
        }
        return Throwable.class.isAssignableFrom(type) && Arrays.stream(Throwable.class.getMethods())
                .map(Method::getName).anyMatch(member::equals);
    }

    /** The public method of the type with this name and these parameter types, resolved by reflection. */
    private static Optional<Method> method(Class<?> type, String member, List<String> parameters) {
        return Arrays.stream(type.getMethods()).filter(m -> m.getName().equals(member))
                .filter(m -> Arrays.stream(m.getParameterTypes()).map(Class::getName).toList().equals(parameters))
                .findFirst();
    }

    /** A method of a module that takes another module's api service: its receiver cannot be proven a proxy. */
    private static void serviceParameters(ScenarioModel model, ValueTypes values, String from, JavaCodeUnit unit,
            List<String> gaps) {
        for (int index = 0; index < unit.getRawParameterTypes().size(); index++) {
            JavaClass parameter = unit.getRawParameterTypes().get(index);
            Optional<Class<?>> type = load(parameter.getName());
            boolean service = type.filter(t -> t.isInterface() && !values.valueOnly(t)).flatMap(model::apiModuleOf)
                    .filter(m -> !m.equals(from)).isPresent();
            if (service) {
                gaps.add("method " + unit.getFullName() + " [" + unit.getDescriptor() + "] (line "
                        + unit.getSourceCodeLocation().getLineNumber() + ") takes another module's api service as "
                        + "parameter " + index + ": " + parameter.getName() + " [" + descriptor(parameter)
                        + "], so the receiver of a call on it cannot be proven to be an observer's proxy");
            }
        }
    }

    private static String descriptor(JavaClass type) {
        return switch (type.getName()) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "short" -> "S";
            case "char" -> "C";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> type.isArray() ? type.getName().replace('.', '/')
                    : ClassDesc.of(type.getName()).descriptorString();
        };
    }

    private static Optional<Class<?>> load(String name) {
        try {
            return Optional.of(Class.forName(name, false, Coverage.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
    }
}
