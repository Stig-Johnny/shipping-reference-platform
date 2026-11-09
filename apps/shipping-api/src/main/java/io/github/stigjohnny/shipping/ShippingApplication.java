package io.github.stigjohnny.shipping;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * The application's entry point, and the root from which Spring Modulith finds the modules (ModularityTest). An
 * isolated class of the model: it uses no module, and no module uses it. The demo profile supplies memory ports,
 * or selects the existing JDBC adapters with postgres; the attended launcher serves a configured static build.
 * Tests may replace ports, clocks and identifier generators explicitly.
 */
@SpringBootApplication
public class ShippingApplication {

    /** Starts the application. */
    public static void main(String[] args) {
        SpringApplication.run(ShippingApplication.class, args);
    }

    /** The one clock of the application, in UTC: modules inject it, and tests replace it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * A number or a boolean where the contract wants a string is a wrong type, not text, as the contract's schemas say.
     * The other rules of strict reading are properties in application.yaml.
     */
    @Bean
    JsonMapperBuilderCustomizer stringsAreStrings() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, strings -> strings
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
