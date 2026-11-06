package io.github.stigjohnny.c4contractkit.bytecode;

import java.util.List;

/**
 * One class read from a class root.
 *
 * @param name the binary name, for example {@code fx.app.a.Outer$Inner} or {@code fx.app.a.package-info}
 * @param packageInfo whether the class file is a {@code package-info}, which declares a package rather than a class
 * @param references the class's direct references to other classes, sorted by {@link CodeReference#ORDER}
 */
public record ImportedClass(String name, boolean packageInfo, List<CodeReference> references) {

    public ImportedClass {
        references = List.copyOf(references);
    }
}
