/**
 * Internal: reads the source files of a web root as data, with a deliberately restricted import grammar, and resolves
 * each import to a file of the root or to a package. Nothing is executed, compiled or loaded; whatever the grammar does
 * not cover is refused with its line, never skipped. Not part of the public API.
 */
@NullMarked
package io.github.stigjohnny.c4contractkit.sources;

import org.jspecify.annotations.NullMarked;
