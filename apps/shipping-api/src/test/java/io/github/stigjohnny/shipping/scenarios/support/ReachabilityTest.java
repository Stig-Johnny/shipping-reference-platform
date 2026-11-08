package io.github.stigjohnny.shipping.scenarios.support;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.shipping.architecture.ClassDirectories;
import io.github.stigjohnny.shipping.quotes.internal.application.ScenarioAlterations.SharedReference;
import io.github.stigjohnny.shipping.tenants.internal.application.ScenarioReference;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

/** The same receiver and containers must be checked separately for each owning module, in either root order. */
class ReachabilityTest {

    @Test
    void aSharedReceiverIsCheckedForEachOwnerInBothTraversalOrders() {
        assertGapInBothOrders(new ThreadTenantContext());
    }

    @Test
    void sharedContainersAreCheckedForEachOwnerInBothTraversalOrders() {
        List<Object> shared = new ArrayList<>();
        shared.add(shared); // A cycle must terminate without hiding the receiver on the other ownership path.
        shared.add(Map.of("receiver", Optional.of(new Object[] {new AtomicReference<>(new ThreadTenantContext())})));
        assertGapInBothOrders(shared);
    }

    private static void assertGapInBothOrders(Object held) {
        Path path = Path.of("../../architecture/workspace.dsl");
        ScenarioModel model = ScenarioModel.read(path, Conformance.check(path, ClassDirectories.main()));
        Map<String, List<String>> gaps = new LinkedHashMap<>();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            Recorder recorder = new Recorder(model, Set.of(), true);
            for (boolean tenantsFirst : List.of(true, false)) {
                Map<String, Object> roots = new LinkedHashMap<>();
                if (tenantsFirst) {
                    roots.put("tenants", new ScenarioReference(held));
                }
                roots.put("quotes", new SharedReference(held));
                roots.putIfAbsent("tenants", new ScenarioReference(held));
                gaps.put(tenantsFirst ? "tenants first" : "quotes first",
                        new Reachability(model, recorder, context).walk(roots, List.of()).gaps());
            }
        }
        assertThat(gaps).allSatisfy((order, found) -> assertThat(found).as(order).singleElement()
                .asString().startsWith("bypass: bean 'quotes' -> held")
                .endsWith("reaches " + ThreadTenantContext.class.getName()
                        + ", an object of module tenants that no observer wraps"));
    }
}
