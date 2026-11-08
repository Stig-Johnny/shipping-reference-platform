package io.github.stigjohnny.shipping.scenarios.support;

import io.github.stigjohnny.shipping.scenarios.support.Sidecar.StepKind;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which kinds of step a run can observe, how, and why it cannot observe the others. A step of a kind without an
 * observer is not evaluated, and the report gives this reason; it is never treated as observed or as missing.
 *
 * @param ignoredApis api interfaces left unwrapped; negative fixtures only
 * @param publications false only in a negative fixture that disables the publication observer
 */
public record Observers(Map<StepKind, String> unobserved, Set<Class<?>> ignoredApis, boolean publications) {

    private static final Map<StepKind, String> HOW = Map.of(
            StepKind.MODULE_API_CALL, "JDK proxies (Spring's ProxyFactory) around every bean that implements an "
                    + "interface of a module's api package; the caller is found on the stack and mapped by "
                    + "contract.packages",
            StepKind.EVENT_PUBLICATION, "a proxy around the application context's event multicaster: a publication "
                    + "attempt is recorded, not a stored publication and not a delivery");

    public Observers {
        unobserved = Map.copyOf(unobserved);
        ignoredApis = Set.copyOf(ignoredApis);
    }

    /** The in-process runs of phase 1: module API calls and publications, on the built modules, without HTTP or SQL. */
    public static Observers inProcess() {
        String database = "no JDBC observation: this run keeps data in an in-memory test double, which is no "
                + "database evidence";
        return new Observers(Map.of(
                StepKind.HTTP_EXCHANGE, "no HTTP observation: this run calls the module's use case directly; the "
                        + "separate HTTP contract tests do not observe this scenario's exchanges",
                StepKind.DATABASE_READ, database,
                StepKind.DATABASE_WRITE, database,
                StepKind.LISTENER_EXECUTION, "listener executions are not observed yet: no module has a listener, "
                        + "and coverage verification reports one as a gap until the observer exists"),
                Set.of(), true);
    }

    /** The same observers with the call observer switched off for one api interface: a disabled observer. */
    public Observers ignoringApi(Class<?> api) {
        Set<Class<?>> ignored = new HashSet<>(ignoredApis);
        ignored.add(api);
        return new Observers(unobserved, ignored, publications);
    }

    /** The same observers without the publication observer. */
    public Observers withoutPublications() {
        return new Observers(unobserved, ignoredApis, false);
    }

    /** Why a kind of step cannot be observed in this run, if it cannot. */
    Optional<String> unobservedReason(StepKind kind) {
        return Optional.ofNullable(unobserved.get(kind));
    }

    /** Every kind that code can perform, with how it is observed or why it is not, for the report. */
    Map<StepKind, Map<String, Object>> describe() {
        Map<StepKind, Map<String, Object>> kinds = new EnumMap<>(StepKind.class);
        for (StepKind kind : StepKind.values()) {
            if (kind == StepKind.USER_ACTION || kind == StepKind.SIMULATED_EXTERNAL) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            Optional<String> reason = unobservedReason(kind);
            entry.put("observed", reason.isEmpty());
            entry.put(reason.isEmpty() ? "how" : "reason", reason.orElseGet(() -> HOW.get(kind)));
            kinds.put(kind, entry);
        }
        return kinds;
    }
}
