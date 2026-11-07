package io.github.stigjohnny.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application's entry point, and the root from which Spring Modulith finds the modules (ModularityTest). An
 * isolated class of the model: it uses no module, and no module uses it. The application does not run yet: its web
 * layer, its database configuration and the wiring of the modules are planned.
 */
@SpringBootApplication
public class ShippingApplication {

    /** Starts the application. */
    public static void main(String[] args) {
        SpringApplication.run(ShippingApplication.class, args);
    }
}
