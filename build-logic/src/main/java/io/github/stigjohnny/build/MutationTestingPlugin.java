package io.github.stigjohnny.build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Mutation testing with PIT, run as a plain {@link JavaExec} task instead of a third-party Gradle plugin, as the PIT
 * review in {@code security/reviews/} requires.
 *
 * <p>The task's own classpath is only the {@code pitest} configuration: PIT, its JUnit 5 plugin and the JUnit
 * Platform launcher. The code under test reaches PIT only through {@code --classPath}, {@code --targetClasses} and
 * {@code --sourceDirs}. Reports go under {@code build/reports/pitest} without timestamps, so two runs on the same
 * commit produce the same files.
 *
 * <p>PIT's parent JVM accepts its workers' connection without authentication. Run it only on a disposable CI worker or
 * on a host whose inbound connections to {@code java} are blocked.
 */
public final class MutationTestingPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaConventionsPlugin.class);
        MutationTestingExtension extension =
                project.getExtensions().create("mutationTesting", MutationTestingExtension.class);
        extension.getMutationThreshold().convention(90);

        VersionCatalog libs = project.getExtensions().getByType(VersionCatalogsExtension.class).named("libs");
        Configuration pitest = project.getConfigurations().create("pitest", c -> {
            c.setDescription("PIT and its JUnit 5 plugin: the mutation-testing tool's own classpath.");
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
        });
        project.getDependencies().add("pitest", libs.findLibrary("pitest-command-line").orElseThrow());
        project.getDependencies().add("pitest", libs.findLibrary("pitest-junit5-plugin").orElseThrow());
        project.getDependencies().add("pitest",
                project.getDependencies().platform(libs.findLibrary("junit-bom").orElseThrow()));
        project.getDependencies().add("pitest", libs.findLibrary("junit-platform-launcher").orElseThrow());

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet test = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
        FileCollection codeUnderTest = test.getRuntimeClasspath();
        FileCollection sourceDirs = main.getJava().getSourceDirectories();
        Provider<Directory> reportDir = project.getLayout().getBuildDirectory().dir("reports/pitest");
        Property<String> targetClasses = extension.getTargetClasses();
        Property<Integer> threshold = extension.getMutationThreshold();

        project.getTasks().register("pitest", JavaExec.class, task -> {
            task.setGroup("verification");
            task.setDescription("Runs PIT mutation testing on this project's production classes.");
            task.dependsOn(test.getClassesTaskName());
            task.setClasspath(pitest);
            task.getMainClass().set("org.pitest.mutationtest.commandline.MutationCoverageReport");
            task.getInputs().files(codeUnderTest).withPropertyName("codeUnderTest");
            task.getInputs().files(sourceDirs).withPropertyName("sourceDirs");
            task.getOutputs().dir(reportDir).withPropertyName("reportDir");
            task.getArgumentProviders().add(() -> {
                List<String> args = new ArrayList<>();
                args.add("--reportDir=" + reportDir.get().getAsFile().getAbsolutePath());
                args.add("--targetClasses=" + targetClasses.get());
                args.add("--sourceDirs=" + join(sourceDirs));
                args.add("--classPath=" + join(codeUnderTest));
                args.add("--outputFormats=HTML,XML");
                args.add("--timestampedReports=false");
                args.add("--mutationThreshold=" + threshold.get());
                args.add("--threads=2");
                return args;
            });
        });
    }

    private static String join(FileCollection files) {
        return files.getFiles().stream().map(File::getAbsolutePath).sorted().collect(Collectors.joining(","));
    }
}
