package io.github.stigjohnny.shipping.architecture;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The running JDK's packages, from its system modules' descriptors ({@link ModuleFinder#ofSystem()}): which module
 * holds a package, and whether it exports the package to every module. A class is a JDK class because its package is
 * in a system module, not because its name starts with {@code java.}: {@code java.awt.peer} is in {@code java.desktop}
 * and not exported, while {@code sun.misc} is exported by {@code jdk.unsupported}.
 */
final class JdkModules {

    private final Map<String, String> moduleOfPackage = new HashMap<>();
    private final Set<String> exported = new HashSet<>();
    private final String version;

    private JdkModules() {
        for (ModuleReference reference : ModuleFinder.ofSystem().findAll()) {
            ModuleDescriptor descriptor = reference.descriptor();
            descriptor.packages().forEach(name -> moduleOfPackage.put(name, descriptor.name()));
            descriptor.exports().stream().filter(exports -> !exports.isQualified())
                    .forEach(exports -> exported.add(exports.source()));
        }
        Runtime.Version runtime = Runtime.version();
        version = runtime.feature() + "." + runtime.interim() + "." + runtime.update()
                + runtime.build().map(build -> "+" + build).orElse("");
    }

    static JdkModules system() {
        return new JdkModules();
    }

    /** The system module that holds the package, if any. */
    Optional<String> module(String packageName) {
        return Optional.ofNullable(moduleOfPackage.get(packageName));
    }

    /** Whether the package's module exports it to every module, not only to named friends. */
    boolean exportsToAll(String packageName) {
        return exported.contains(packageName);
    }

    /** The running JDK's version as the allowlist writes it, such as 25.0.5+8. */
    String version() {
        return version;
    }
}
