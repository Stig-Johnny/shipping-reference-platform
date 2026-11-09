package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;
import io.github.stigjohnny.shipping.quotes.internal.adapter.out.jdbc.JdbcQuoteStore;
import io.github.stigjohnny.shipping.shipments.internal.adapter.out.jdbc.JdbcShipmentStore;
import io.github.stigjohnny.shipping.shipments.internal.adapter.out.jdbc.JdbcTrackingNumbers;
import io.github.stigjohnny.shipping.tenants.internal.adapter.out.jdbc.JdbcMembershipDirectory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** The local gate verifies opt-in bean registration; existing HTTP tests still start with the in-memory ports. */
class JdbcProfileTest {
    @Test void everyJdbcAdapterRequiresPostgresAndDeclaresATransaction() {
        for (Class<?> adapter : List.of(JdbcMembershipDirectory.class, JdbcQuoteStore.class,
                JdbcShipmentStore.class, JdbcTrackingNumbers.class)) {
            assertThat(adapter.getAnnotation(Profile.class).value()).containsExactly("postgres");
            assertThat(adapter.isAnnotationPresent(Repository.class)).isTrue();
            assertThat(adapter.isAnnotationPresent(Transactional.class)).isTrue();
        }
    }
}
