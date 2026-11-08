/**
 * Internal: reads the source files of a web root as data, with a deliberately restricted import grammar, and resolves
 * each detected import to a file of the root or to a package. Nothing is executed, compiled or loaded. This is partial
 * static lint: recognized unsupported forms are refused, but imports and unsupported constructs can be missed
 * silently, including loaders after spread syntax (ADR 0014). Not part of the public API.
 */
@NullMarked
package io.github.stigjohnny.c4contractkit.sources;

import org.jspecify.annotations.NullMarked;
