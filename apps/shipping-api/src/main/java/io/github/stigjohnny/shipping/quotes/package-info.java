/**
 * The quotes module (docs/modules/quotes.md): prices parcels with the synthetic tariff and issues quotes whose offers
 * expire after 30 minutes. Other modules use only its {@code api} package. Built so far: the domain and the
 * application layer; the HTTP operations, the database and the reading of a quote are planned.
 */
@ApplicationModule
@NullMarked
package io.github.stigjohnny.shipping.quotes;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
