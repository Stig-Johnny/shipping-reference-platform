package io.github.stigjohnny.build;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;

/** JDK-only regression harness against Gradle's component API; no additional test dependency. */
public final class ProviderIdentityTest {
    private ProviderIdentityTest() { }

    public static void main(String[] args) {
        int failures = 0;
        for (String build : new String[] {":", ":shadow"}) {
            String actual = ProviderIndexTask.component(new Project(build, ":libs:c4-contract-kit"));
            String expected = "project\t:libs:c4-contract-kit\t\t" + build;
            if (!actual.equals(expected)) {
                System.out.println("FAIL: project identity must retain build " + build);
                failures++;
            } else {
                System.out.println("PASS: project identity retains build " + build);
            }
        }
        if (failures != 0) {
            throw new AssertionError(failures + " project identity checks failed");
        }
    }

    private record Project(String buildPath, String projectPath) implements ProjectComponentIdentifier {
        @Override public BuildIdentifier getBuild() { return () -> buildPath; }
        @Override public String getProjectPath() { return projectPath; }
        @Override public String getBuildTreePath() { return buildPath + projectPath; }
        @Override public String getProjectName() { return "c4-contract-kit"; }
        @Override public String getDisplayName() { return getBuildTreePath(); }
    }
}
