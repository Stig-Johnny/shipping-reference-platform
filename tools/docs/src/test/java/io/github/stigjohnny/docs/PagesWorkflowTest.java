package io.github.stigjohnny.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.docs.testing.Repository;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The publishing workflow, .github/workflows/pages.yml, against the go-public review's requirements, the conditions of
 * the Pages actions' reviews and the runner review: who may run it, what each job may do, that the job which runs the
 * tests holds no publication privilege and the jobs that hold one run no test, the order of each job's steps, how the
 * evidence passes from one job to the next, where the dictionary goes, and which actions run, by full SHA.
 */
class PagesWorkflowTest {

    private static final String PATH = ".github/workflows/pages.yml";
    private static final String TEXT = Repository.read(PATH);
    private static final YMap WORKFLOW = SafeYaml.yaml(TEXT, PATH).asMap(PATH);
    private static final YMap TEST = WORKFLOW.map("jobs").map("test");
    private static final YMap BUILD = WORKFLOW.map("jobs").map("build");
    private static final YMap DEPLOY = WORKFLOW.map("jobs").map("deploy");
    private static final String MAIN_ONLY = "${{ github.ref == 'refs/heads/main' }}";
    private static final String CHECKOUT = "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1";
    private static final String SETUP_JAVA = "actions/setup-java@77dd5b2ce225ea11469928ac317850245a74ae78";
    private static final String UPLOAD = "actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9";
    private static final String DEPLOY_PAGES = "actions/deploy-pages@368f82528645a54fb793d4d04e342629a3f51346";
    private static final String DOCS_AND_TESTS = "./gradlew --no-daemon --console=plain :tools:docs:check :tools:docs:site";
    private static final String SITE_FROM_REPORTS = "./gradlew --no-daemon --console=plain :tools:docs:site -x test";

    @Test
    void itRunsOnPushesToMainAndByHandOnlyNeverOnPullRequests() {
        YMap on = WORKFLOW.map("on");

        assertThat(on.entries().keySet()).containsExactlyInAnyOrder("push", "workflow_dispatch");
        assertThat(on.map("push").strings("branches")).containsExactly("main");
        assertThat(TEXT).doesNotContain("pull_request", "workflow_run");
        assertThat(WORKFLOW.map("jobs").entries().keySet()).containsExactly("test", "build", "deploy");
        for (YMap job : List.of(TEST, BUILD, DEPLOY)) {
            assertThat(job.string("if")).isEqualTo(MAIN_ONLY);
        }
    }

    @Test
    void permissionsAreEmptyExceptReadForTestAndBuildAndPagesAndOidcForTheDeploy() {
        assertThat(WORKFLOW.map("permissions").entries()).isEmpty();
        assertThat(texts(TEST.map("permissions"))).containsExactly(Map.entry("contents", "read"));
        assertThat(texts(BUILD.map("permissions"))).containsExactly(Map.entry("contents", "read"));
        assertThat(texts(DEPLOY.map("permissions"))).containsExactlyInAnyOrderEntriesOf(
                Map.of("pages", "write", "id-token", "write"));
    }

    @Test
    void runStepsUseBashWithPipefailAndDeploymentsAreSerialised() {
        assertThat(WORKFLOW.map("defaults").map("run").string("shell")).isEqualTo("bash");
        assertThat(texts(WORKFLOW.map("concurrency"))).containsExactlyInAnyOrderEntriesOf(
                Map.of("group", "pages", "cancel-in-progress", "false"));
    }

    @Test
    void theBuildJobUsesTheEnvironmentWhoseSecretIsTheDictionary() {
        assertThat(BUILD.string("environment")).isEqualTo("github-pages");
        assertThat(BUILD.string("runs-on")).startsWith("ubuntu-");
        assertThat(BUILD.string("needs")).isEqualTo("test");
    }

    @Test
    void theTestJobHasNoEnvironmentNoSecretAndTheRunnerTheEngineReviewCovers() {
        assertThat(TEST.has("environment")).isFalse();
        assertThat(TEST.has("needs")).isFalse();
        assertThat(TEST.string("runs-on")).isEqualTo("ubuntu-24.04");
        assertThat(steps(TEST)).allSatisfy(step -> assertThat(text(step)).doesNotContain("secrets."));
        assertThat(texts(TEST.map("outputs"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "reports", "${{ steps.handover.outputs.reports }}",
                "reports-sha256", "${{ steps.handover.outputs.reports-sha256 }}",
                "site-sha256", "${{ steps.handover.outputs.site-sha256 }}"));
    }

    @Test
    void theTestJobChecksBeforeUseThenPreparesTheEngineRunsEveryTestAndOnlyThenHandsOverTheReports() {
        List<YMap> steps = steps(TEST);

        assertCheckedOut(steps.getFirst());
        assertThat(order(steps, "stage-temurin.sh", SETUP_JAVA, "PolicyCheck.java . | tee -a \"$GITHUB_STEP_SUMMARY\"",
                "gradle-wrapper.jar\" | sha256sum --check --strict", "container-engine.sh prepare",
                "container-engine.sh stage", DOCS_AND_TESTS, "container-engine.sh verify", "site-handover.sh send"))
                .isSorted();
        assertThat(steps.getLast().string("id")).isEqualTo("handover");
        assertThat(steps.getLast().string("run")).isEqualTo("tools/ci/site-handover.sh send");
        YMap verify = step(steps, "container-engine.sh verify");
        assertThat(verify.string("if")).isEqualTo("${{ !cancelled() && steps.stage.outcome == 'success' }}");
        assertThat(step(steps, "container-engine.sh stage").string("id")).isEqualTo("stage");
    }

    @Test
    void theTestsRunUnderTheRunnerReviewsConditions() {
        YMap tests = step(steps(TEST), DOCS_AND_TESTS);

        assertThat(tests.string("run")).isEqualTo(DOCS_AND_TESTS);
        assertThat(texts(tests.map("env"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "GRADLE_USER_HOME", "${{ runner.temp }}/gradle-user-home",
                "SRP_CONTAINER_ENGINE", "github-ubuntu-24.04",
                "DOCKER_HOST", "unix:///var/run/docker.sock",
                "DOCKER_CONFIG", "${{ runner.temp }}/docker-config",
                "TESTCONTAINERS_RYUK_DISABLED", "true",
                "TESTCONTAINERS_CHECKS_DISABLE", "true"));
        for (String command : List.of("prepare", "stage", "verify")) {
            assertThat(texts(step(steps(TEST), "container-engine.sh " + command).map("env")))
                    .containsExactlyInAnyOrderEntriesOf(Map.of("DOCKER_HOST", "unix:///var/run/docker.sock",
                            "DOCKER_CONFIG", "${{ runner.temp }}/docker-config"));
        }
    }

    @Test
    void theBuildChecksBeforeUseTakesOverTheReportsBuildsComparesScansReChecksGuardsAndOnlyThenUploads() {
        List<YMap> steps = steps(BUILD);

        assertCheckedOut(steps.getFirst());
        assertThat(order(steps, "stage-temurin.sh", SETUP_JAVA, "PolicyCheck.java . | tee -a \"$GITHUB_STEP_SUMMARY\"",
                "gradle-wrapper.jar\" | sha256sum --check --strict", "site-handover.sh receive", SITE_FROM_REPORTS,
                "site-handover.sh compare", ":tools:docs:scanSite", "mermaid.min.js\" | sha256sum --check --strict",
                "-type l", UPLOAD)).isSorted();
        assertThat(steps.getLast().string("uses")).isEqualTo(UPLOAD);
        assertThat(texts(steps.getLast().map("with"))).containsExactly(Map.entry("path", "tools/docs/build/site"));
    }

    @Test
    void theBuildRunsNoTestAndTakesItsEvidenceFromTheTestJobOfTheSameRun() {
        List<YMap> steps = steps(BUILD);
        List<String> gradle = steps.stream().map(step -> step.optionalString("run").orElse(""))
                .filter(run -> run.contains("./gradlew")).toList();

        assertThat(gradle).containsExactly(SITE_FROM_REPORTS,
                "./gradlew --no-daemon --console=plain :tools:docs:scanSite");
        assertThat(texts(step(steps, "site-handover.sh receive").map("env"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "REPORTS", "${{ needs.test.outputs.reports }}",
                "REPORTS_SHA256", "${{ needs.test.outputs.reports-sha256 }}"));
        assertThat(texts(step(steps, "site-handover.sh compare").map("env"))).containsExactly(
                Map.entry("SITE_SHA256", "${{ needs.test.outputs.site-sha256 }}"));
        assertThat(steps).noneSatisfy(step -> assertThat(text(step)).contains("container-engine.sh"));
        assertThat(TEXT).doesNotContain("download-artifact", "upload-artifact@");
    }

    @Test
    void theGuardRefusesSymbolicLinksHardLinksAndHiddenFilesRightBeforeTheUpload() {
        List<YMap> steps = steps(BUILD);
        String guard = steps.get(steps.size() - 2).string("run");

        assertThat(guard).contains("find \"$site\" -type l", "find \"$site\" -type f -links +1",
                "-name '.*'", "exit 1");
    }

    @Test
    void eachSearchOfTheGuardRunsAloneAndASearchThatFailsFailsTheJobWithoutPrintingAPath() {
        List<YMap> steps = steps(BUILD);
        String guard = steps.get(steps.size() - 2).string("run");

        assertThat(guard).contains("if ! links=\"$(find \"$site\" -type l -print -quit 2>/dev/null)\"; then",
                "if ! hard_links=\"$(find \"$site\" -type f -links +1 -print -quit 2>/dev/null)\"; then",
                "if ! hidden=\"$(find \"$site\" -mindepth 1 -name '.*' -print -quit 2>/dev/null)\"; then")
                .doesNotContain("[[ -n \"$(find");
        assertThat(count(guard, "could not be searched")).isEqualTo(3);
    }

    @Test
    void theHeaderNamesTheStagingAndTheAllowlistCheckAsTheTrustedBootstrap() {
        String header = TEXT.substring(0, TEXT.indexOf("name: pages"));

        assertThat(header).contains("trusted bootstrap", "already on main", "no publication privilege")
                .doesNotContain("before any of its code runs");
        assertThat(TEXT).doesNotContain("before any of its code runs");
    }

    @Test
    void theDictionaryReachesTheScanStepAndNoOther() {
        List<YMap> withDictionary = new ArrayList<>();
        for (YMap job : List.of(TEST, BUILD, DEPLOY)) {
            steps(job).stream().filter(step -> step.find("env").map(env -> env.asMap("env")
                    .has("PUBLIC_SCAN_DICTIONARY")).orElse(false)).forEach(withDictionary::add);
        }

        assertThat(withDictionary).singleElement().satisfies(step -> {
            assertThat(step.string("run")).isEqualTo("./gradlew --no-daemon --console=plain :tools:docs:scanSite");
            assertThat(step.map("env").string("PUBLIC_SCAN_DICTIONARY"))
                    .isEqualTo("${{ secrets.PUBLIC_SCAN_DICTIONARY }}");
        });
        assertThat(steps(BUILD)).contains(withDictionary.getFirst());
        assertThat(count(TEXT, "PUBLIC_SCAN_DICTIONARY:")).isEqualTo(1);
        assertThat(count(TEXT, "secrets.")).isEqualTo(1);
        assertThat(TEXT).doesNotContain("secrets.GITHUB_TOKEN", "github.token", "set -x");
    }

    @Test
    void theDeployJobNeedsTheBuildAndHasOneStepWithDefaultInputs() {
        assertThat(DEPLOY.string("needs")).isEqualTo("build");
        assertThat(DEPLOY.map("environment").string("name")).isEqualTo("github-pages");
        assertThat(DEPLOY.map("environment").string("url")).isEqualTo("${{ steps.deployment.outputs.page_url }}");
        assertThat(steps(DEPLOY)).singleElement().satisfies(step -> {
            assertThat(step.string("uses")).isEqualTo(DEPLOY_PAGES);
            assertThat(step.has("with")).isFalse();
            assertThat(step.has("run")).isFalse();
        });
        assertThat(TEXT).doesNotContain("always()");
    }

    @Test
    void onlyTheFourReviewedActionsRunByFullShaAndConfigurePagesIsNotOne() {
        Matcher uses = Pattern.compile("uses:\\s*(\\S+)").matcher(TEXT);
        List<String> actions = new ArrayList<>();
        while (uses.find()) {
            actions.add(uses.group(1));
        }

        // The test job and the build job each check out and install the JDK; only the build job uploads.
        assertThat(actions).containsExactly(CHECKOUT, SETUP_JAVA, CHECKOUT, SETUP_JAVA, UPLOAD, DEPLOY_PAGES);
        assertThat(TEXT).doesNotContain("configure-pages", "include-hidden-files", "retention-days");
        assertThat(count(TEXT, "ref:")).isEqualTo(2);
    }

    @Test
    void theJdkIsInstalledFromTheStagedArchiveOnly() {
        for (YMap job : List.of(TEST, BUILD)) {
            YMap setupJava = step(steps(job), SETUP_JAVA);

            assertThat(texts(setupJava.map("with"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "distribution", "jdkfile", "jdk-file", "${{ steps.jdk.outputs.jdk-file }}", "java-version",
                    "25.0.5", "architecture", "x64", "token", ""));
        }
    }

    private static void assertCheckedOut(YMap step) {
        assertThat(step.string("uses")).isEqualTo(CHECKOUT);
        assertThat(texts(step.map("with"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "ref", "${{ github.sha }}", "persist-credentials", "false"));
    }

    private static List<YMap> steps(YMap job) {
        return job.list("steps").stream().map(step -> step.asMap("step")).toList();
    }

    /** The step's run and uses text, for searching. */
    private static String text(YMap step) {
        return step.optionalString("run").orElse("") + " " + step.optionalString("uses").orElse("") + " "
                + step.find("env").map(env -> texts(env.asMap("env")).toString()).orElse("");
    }

    /** The first step whose run or uses text contains the marker. */
    private static YMap step(List<YMap> steps, String marker) {
        return steps.get(order(steps, marker).getFirst());
    }

    /** The position of the first step whose text contains each marker, in the order given. */
    private static List<Integer> order(List<YMap> steps, String... markers) {
        List<Integer> positions = new ArrayList<>();
        for (String marker : markers) {
            int position = -1;
            for (int i = 0; i < steps.size() && position < 0; i++) {
                YMap step = steps.get(i);
                String text = step.optionalString("run").orElse("") + " " + step.optionalString("uses").orElse("");
                if (text.contains(marker)) {
                    position = i;
                }
            }
            assertThat(position).as("a step with " + marker).isNotNegative();
            positions.add(position);
        }
        return positions;
    }

    private static Map<String, String> texts(YMap map) {
        Map<String, String> texts = new LinkedHashMap<>();
        for (Map.Entry<String, YamlValue> entry : map.entries().entrySet()) {
            texts.put(entry.getKey(), entry.getValue().asText(entry.getKey()));
        }
        return texts;
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }
}
