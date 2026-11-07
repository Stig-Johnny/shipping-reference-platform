/**
 * The shipments module's public API: the only package of the module that other modules may use (ADR 0003). It holds
 * what consumers of its events need, and no quotes type and no recipient data.
 */
@NamedInterface("api")
@NullMarked
package io.github.stigjohnny.shipping.shipments.api;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
