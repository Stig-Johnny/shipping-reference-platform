/**
 * The shipments module (docs/modules/shipments.md): books shipments from quote offers, idempotently per tenant and
 * Idempotency-Key. Other modules use only its {@code api} package. Built so far: booking, reading, listing and the
 * simulated label, in the domain, the application layer and over HTTP, with the JDBC store and tracking numbers under
 * the postgres profile; cancellation and durable event publication are planned.
 */
@ApplicationModule
@NullMarked
package io.github.stigjohnny.shipping.shipments;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
