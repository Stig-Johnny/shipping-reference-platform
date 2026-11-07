/**
 * The shipments module (docs/modules/shipments.md): books shipments from quote offers, idempotently per tenant and
 * Idempotency-Key. Other modules use only its {@code api} package. Built so far: booking, in the domain and the
 * application layer; the HTTP operations, the database, cancellation, reading, listing and the label are planned.
 */
@ApplicationModule
@NullMarked
package io.github.stigjohnny.shipping.shipments;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
