package io.github.stigjohnny.shipping.scenarios.support;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Records every interaction that crosses a module boundary while a scenario runs, independently of what the scenario
 * expects; the comparison comes afterwards and reads only this record.
 *
 * <p>Two observers record. Every bean that implements an interface of a module's {@code api} package is replaced, as
 * it is created, by a JDK proxy (Spring's {@link ProxyFactory}) around the real implementation, which records each
 * call of an {@code api} method. The application context's event multicaster is wrapped the same way, which records
 * each event of a module's {@code api} package that is published through the context. The caller of a call or
 * publication is the first frame on the stack that belongs to the application: its class is mapped to a module by the
 * model's {@code contract.packages}. A call from a module to itself crosses no boundary and is not recorded; a call
 * by the test itself is recorded as the driver's, never as a module's; a caller that maps to no module is recorded as
 * unattributed, which the comparison reports as a coverage gap. Nothing here knows the scenario's steps.
 */
public final class Recorder {

    /** What was seen. */
    public enum Seen {
        MODULE_API_CALL, EVENT_PUBLICATION, DRIVER_CALL, UNATTRIBUTED
    }

    /**
     * One recorded interaction. No argument, return value or payload is kept: the operation is a type and a method.
     *
     * @param run the stimulus it belongs to, {@code setup}, or empty when it happened outside every stimulus
     * @param causedBy the interaction it happened inside, on the same thread, or 0
     * @param caller the application frame that made it: class and method
     */
    public record Interaction(int seq, String run, String thread, Seen kind, String from, String to,
            String operation, int causedBy, String caller) {
    }

    /** A stimulus: what the test did to start one run of the scenario, on which thread, and between which records. */
    public record Stimulus(String run, String description, String thread, int after, int until) {
    }

    /** The run name of everything a test does to prepare a scenario. */
    public static final String SETUP = "setup";

    private final ScenarioModel model;
    private final String application;
    private final String driver;
    private final String support;
    private final Set<Class<?>> ignored;
    private final boolean publications;
    private final MethodInterceptor calls = this::call;
    private final MethodInterceptor multicasts = this::multicast;
    private final List<Interaction> interactions = new ArrayList<>();
    private final List<Stimulus> stimuli = new ArrayList<>();
    private final List<String> wrappingGaps = new ArrayList<>();
    private final Map<Thread, String> threads = new HashMap<>();
    private final ThreadLocal<String> run = new ThreadLocal<>();
    private final ThreadLocal<Deque<Integer>> open = ThreadLocal.withInitial(ArrayDeque::new);
    private final List<String> late = new ArrayList<>();
    private final Set<Thread> stimulusThreads = ConcurrentHashMap.newKeySet();
    private Set<Thread> threadsAtStart = Set.of();
    private volatile State state = State.PREPARING;
    private volatile Thread outcomeThread;
    private volatile boolean probeSeen;

    /** Where a run is: preparing, recording, evaluating its outcomes (on one thread), or done. */
    private enum State {
        PREPARING, RECORDING, OUTCOMES, DONE
    }

    /**
     * A recorder for one run.
     *
     * @param ignored {@code api} interfaces whose implementations are deliberately left unwrapped: only negative
     *     fixtures pass any, to show that coverage verification notices a disabled observer
     * @param publications false only in a negative fixture that leaves the multicaster unwrapped
     */
    Recorder(ScenarioModel model, Set<Class<?>> ignored, boolean publications) {
        this.model = model;
        this.application = model.root() + ".";
        this.driver = Recorder.class.getPackageName().replaceFirst("\\.support$", "") + ".";
        this.support = Recorder.class.getPackageName() + ".";
        this.ignored = Set.copyOf(ignored);
        this.publications = publications;
    }

    /** The bean post-processor that puts the observers in place; register it before the context refreshes. */
    BeanPostProcessor observers() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                return observe(bean, name);
            }
        };
    }

    private Object observe(Object bean, String name) {
        if (bean instanceof ApplicationEventMulticaster
                && AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME.equals(name)) {
            return publications ? proxy(bean, multicasts) : bean;
        }
        List<Class<?>> api = apiInterfaces(bean.getClass());
        if (api.isEmpty() || api.stream().anyMatch(ignored::contains)) {
            return bean;
        }
        List<Class<?>> sealed = Arrays.stream(ClassUtils.getAllInterfacesForClass(bean.getClass()))
                .filter(Class::isSealed).toList();
        if (!sealed.isEmpty()) {
            wrappingGaps.add("bean '" + name + "' implements the sealed interface " + sealed.getFirst().getName()
                    + ", which a JDK proxy cannot implement");
            return bean;
        }
        return proxy(bean, calls);
    }

    private static Object proxy(Object bean, MethodInterceptor interceptor) {
        ProxyFactory factory = new ProxyFactory(bean);
        factory.setProxyTargetClass(false);
        factory.addAdvice(interceptor);
        return factory.getProxy(bean.getClass().getClassLoader());
    }

    /** The interfaces of a module's {@code api} package that a class implements. */
    List<Class<?>> apiInterfaces(Class<?> type) {
        return Arrays.stream(ClassUtils.getAllInterfacesForClass(type))
                .filter(i -> model.apiModuleOf(i).isPresent()).toList();
    }

    /** Whether the object is a proxy that this recorder's call observer put in place. */
    boolean observes(Object bean) {
        return bean instanceof Advised advised && Arrays.stream(advised.getAdvisors()).map(Advisor::getAdvice)
                .anyMatch(calls::equals);
    }

    List<String> wrappingGaps() {
        return List.copyOf(wrappingGaps);
    }

    boolean probeSeen() {
        return probeSeen;
    }

    void begin() {
        threadsAtStart = Set.copyOf(Thread.getAllStackTraces().keySet());
        state = State.RECORDING;
    }

    /** Ends the recording; the outcomes are evaluated next, on the calling thread, and are not recorded. */
    void end() {
        outcomeThread = Thread.currentThread();
        state = State.OUTCOMES;
    }

    /** After the outcomes: from now on every interaction is late. */
    void done() {
        state = State.DONE;
    }

    /** Interactions after the recording ended, other than the outcome assertions' own: asynchronous work. */
    synchronized List<String> late() {
        return List.copyOf(late);
    }

    /** Threads that started during the run, ran no stimulus and are still alive; JDK system threads aside. */
    List<String> threadsLeftRunning() {
        ThreadGroup system = systemGroup();
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && !threadsAtStart.contains(t) && !stimulusThreads.contains(t))
                .filter(t -> t.getThreadGroup() != null && !t.getThreadGroup().equals(system))
                .map(Thread::getName).sorted().toList();
    }

    private static ThreadGroup systemGroup() {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        while (group != null && group.getParent() != null) {
            group = group.getParent();
        }
        return group;
    }

    /** Runs a stimulus of the scenario under its run name, on the calling thread. */
    <T> T stimulus(String name, String description, Supplier<T> work) {
        stimulusThreads.add(Thread.currentThread());
        String previous = run.get();
        int after = last();
        run.set(name);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                run.remove();
            } else {
                run.set(previous);
            }
            synchronized (this) {
                stimuli.add(new Stimulus(name, description, thread(), after, last()));
            }
        }
    }

    synchronized List<Interaction> interactions() {
        return List.copyOf(interactions);
    }

    synchronized List<Stimulus> stimuli() {
        return List.copyOf(stimuli);
    }

    private synchronized int last() {
        return interactions.size();
    }

    private Object call(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Optional<String> callee = model.apiModuleOf(method.getDeclaringClass());
        if (callee.isEmpty() || !recordable(method.getDeclaringClass().getName() + "#" + method.getName())) {
            return invocation.proceed();
        }
        Optional<Integer> seq = record(Seen.MODULE_API_CALL, callee.get(),
                method.getDeclaringClass().getName() + "#" + method.getName());
        if (seq.isEmpty()) {
            return invocation.proceed();
        }
        open.get().push(seq.get());
        try {
            return invocation.proceed();
        } finally {
            open.get().pop();
        }
    }

    private Object multicast(MethodInvocation invocation) throws Throwable {
        if (invocation.getMethod().getName().equals("multicastEvent") && invocation.getArguments().length > 0) {
            Object event = invocation.getArguments()[0];
            Object payload = event instanceof PayloadApplicationEvent<?> published ? published.getPayload() : event;
            if (payload instanceof Probe) {
                probeSeen = true;
            } else if (payload != null && model.apiModuleOf(payload.getClass()).isPresent()
                    && recordable(payload.getClass().getName())) {
                record(Seen.EVENT_PUBLICATION, "", payload.getClass().getName());
            }
        }
        return invocation.proceed();
    }

    /**
     * Whether an interaction is recorded now: during the recording, yes; before it, no; after it, no, but it is kept
     * as late unless it is an outcome assertion's own, on the thread that evaluates the outcomes.
     */
    private boolean recordable(String operation) {
        State now = state;
        if (now == State.RECORDING) {
            return true;
        }
        boolean outcome = now == State.OUTCOMES && Thread.currentThread().equals(outcomeThread);
        if (now != State.PREPARING && !outcome) {
            synchronized (this) {
                late.add(operation + " on thread '" + Thread.currentThread().getName() + "'");
            }
        }
        return false;
    }

    /** Records one interaction with its caller; empty when the caller is in the callee's own module. */
    private Optional<Integer> record(Seen seen, String to, String operation) {
        StackWalker.StackFrame frame = StackWalker.getInstance().walk(frames -> frames
                .filter(f -> f.getClassName().startsWith(application) && !f.getClassName().startsWith(support))
                .findFirst()).orElse(null);
        String caller = frame == null ? "" : frame.getClassName() + "#" + frame.getMethodName();
        Optional<String> module = frame == null ? Optional.empty() : model.moduleOf(frame.getClassName());
        Seen kind = seen;
        String from;
        if (module.isPresent()) {
            from = module.get();
            if (from.equals(to)) {
                return Optional.empty();
            }
        } else if (frame != null && frame.getClassName().startsWith(driver)) {
            kind = Seen.DRIVER_CALL;
            from = "driver";
        } else {
            kind = Seen.UNATTRIBUTED;
            from = "";
        }
        synchronized (this) {
            int seq = interactions.size() + 1;
            Integer cause = open.get().peek();
            interactions.add(new Interaction(seq, run.get() == null ? "" : run.get(), thread(), kind, from, to,
                    operation, cause == null ? 0 : cause, caller));
            return Optional.of(seq);
        }
    }

    /** A label for the current thread that does not depend on its name: T1, T2, ... in the order first seen. */
    private synchronized String thread() {
        return threads.computeIfAbsent(Thread.currentThread(), t -> "T" + (threads.size() + 1));
    }

    /** The event that coverage verification publishes to prove that publications reach the observer. */
    record Probe() {
    }
}
