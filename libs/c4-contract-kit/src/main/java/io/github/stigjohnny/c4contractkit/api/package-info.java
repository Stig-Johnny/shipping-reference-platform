/**
 * The public API of c4-contract-kit: check compiled Java classes against a C4 model written in Structurizr DSL.
 *
 * <p>{@link io.github.stigjohnny.c4contractkit.api.Conformance#check Conformance.check} parses the model in a
 * restricted mode, reads the class files without loading them, and compares the classes' direct static dependencies
 * with the relationships between the model's components, in both directions.
 * {@link io.github.stigjohnny.c4contractkit.api.ConformanceAssertions#assertConforms
 * ConformanceAssertions.assertConforms} writes the deterministic reports and then fails a test on any violation.
 * This is the only package meant for callers; every other package of the library is internal.
 */
@NullMarked
package io.github.stigjohnny.c4contractkit.api;

import org.jspecify.annotations.NullMarked;
