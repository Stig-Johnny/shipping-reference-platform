package io.github.stigjohnny.build;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.dsl.LockMode;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

/**
 * The conventions every Java project in this repository shares.
 *
 * <ul>
 *   <li>Bytecode for Java 25 ({@code --release 25}), UTF-8 sources, every lint warning an error.
 *   <li>Error Prone 2.51.0 with NullAway 0.14.3 as a {@code javac} plugin, wired without a third-party Gradle plugin,
 *       exactly as their reviews in {@code security/reviews/} require: a forked compiler with the {@code jdk.compiler}
 *       exports, {@code -XDcompilePolicy=simple}, {@code --should-stop=ifError=FLOW}, and NullAway checking only
 *       {@code @NullMarked} code.
 *   <li>Strict dependency locking of every configuration, so a resolved version can only change in a reviewed lockfile.
 *   <li>JUnit Platform for tests.
 * </ul>
 */
public final class JavaConventionsPlugin implements Plugin<Project> {

    /**
     * The {@code jdk.compiler} packages Error Prone needs exported, from its installation guide. With the two opened
     * packages below these are the ten flags its review names; nothing else is exported or opened.
     */
    private static final List<String> JAVAC_EXPORTED_PACKAGES = List.of(
            "api", "file", "main", "model", "parser", "processing", "tree", "util");

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        project.getDependencyLocking().lockAllConfigurations();
        project.getDependencyLocking().getLockMode().set(LockMode.STRICT);

        project.getExtensions().configure(JavaPluginExtension.class, java -> {
            java.withSourcesJar();
            java.withJavadocJar();
        });

        VersionCatalog libs = project.getExtensions().getByType(VersionCatalogsExtension.class).named("libs");
        Configuration errorprone = project.getConfigurations().create("errorprone", c -> {
            c.setDescription("Error Prone and NullAway: the only annotation processors on the compile path.");
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
        });
        project.getDependencies().add("errorprone", libs.findLibrary("errorprone-core").orElseThrow());
        project.getDependencies().add("errorprone", libs.findLibrary("nullaway").orElseThrow());
        project.getConfigurations().named(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
                .configure(c -> c.extendsFrom(errorprone));
        project.getConfigurations().named(JavaPlugin.TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
                .configure(c -> c.extendsFrom(errorprone));

        project.getTasks().withType(JavaCompile.class).configureEach(compile -> {
            compile.getOptions().getRelease().set(25);
            compile.getOptions().setEncoding("UTF-8");
            compile.getOptions().setFork(true);
            for (String pkg : JAVAC_EXPORTED_PACKAGES) {
                compile.getOptions().getForkOptions().getJvmArgs().add(
                        "--add-exports=jdk.compiler/com.sun.tools.javac." + pkg + "=ALL-UNNAMED");
            }
            compile.getOptions().getForkOptions().getJvmArgs().addAll(List.of(
                    "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"));
            compile.getOptions().getCompilerArgs().addAll(List.of(
                    "-Xlint:all,-processing",
                    "-Werror",
                    "-parameters",
                    "-XDcompilePolicy=simple",
                    "--should-stop=ifError=FLOW",
                    "-Xplugin:ErrorProne -XepDisableWarningsInGeneratedCode"
                            + " -Xep:NullAway:ERROR -XepOpt:NullAway:OnlyNullMarked=true"));
        });

        // Downloads and checksums every artifact without compiling or running anything. With --write-locks and
        // --write-verification-metadata sha256 it produces the files a dependency review starts from.
        ConfigurationContainer configurations = project.getConfigurations();
        project.getTasks().register("resolveAllDependencies", task -> {
            task.setGroup("help");
            task.setDescription("Resolves every resolvable configuration (for --write-locks and verification metadata).");
            task.notCompatibleWithConfigurationCache("Resolves every configuration of the project on demand.");
            task.doLast(t -> configurations.stream()
                    .filter(Configuration::isCanBeResolved)
                    .forEach(c -> c.getIncoming().getFiles().getFiles()));
        });

        project.getTasks().withType(Test.class).configureEach(test -> {
            test.useJUnitPlatform();
            test.getTestLogging().events("failed", "skipped");
            test.getTestLogging().setShowStandardStreams(false);
        });
    }
}
