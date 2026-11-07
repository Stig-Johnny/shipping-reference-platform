package io.github.stigjohnny.c4contractkit.bytecode;

import java.util.List;
import java.util.Optional;

/**
 * One class read from a class root.
 *
 * @param name the binary name, for example {@code fx.app.a.Outer$Inner} or {@code fx.app.a.package-info}
 * @param packageInfo whether the class file is a {@code package-info}, which declares a package rather than a class
 * @param enclosingClass the class that this member, local or anonymous class is declared in, as the class file
 *     records it; empty for a top-level class, whatever {@code $} its name contains
 * @param references the class's direct references to other classes, sorted by {@link CodeReference#ORDER}
 */
public record ImportedClass(String name, boolean packageInfo, Optional<String> enclosingClass,
                            List<CodeReference> references) {

    public ImportedClass {
        references = List.copyOf(references);
    }
}
