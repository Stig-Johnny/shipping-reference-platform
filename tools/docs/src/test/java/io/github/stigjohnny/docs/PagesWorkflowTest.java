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
 * The publishing workflow, .github/workflows/pages.yml, against the go-public review's requirements and the conditions
 * of the Pages actions' reviews: who may run it, what each job may do, the order of the build job's steps, where the
 * dictionary goes, and which actions run, by full SHA.
 */
class PagesWorkflowTest {

    private static final String PATH = ".github/workflows/pages.yml";
    private static final String TEXT = Repository.read(PATH);
    private static final YMap WORKFLOW = SafeYaml.yaml(TEXT, PATH).asMap(PATH);
    private static final YMap BUILD = WORKFLOW.map("jobs").map("build");
    private static final YMap DEPLOY = WORKFLOW.map("jobs").map("deploy");
    private static final String MAIN_ONLY = "${{ github.ref == 'refs/heads/main' }}";
    private static final String CHECKOUT = "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1";
    private static final String SETUP_JAVA = "actions/setup-java@77dd5b2ce225ea11469928ac317850245a74ae78";
    private static final String UPLOAD = "actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9";
    private static final String DEPLOY_PAGES = "actions/deploy-pages@368f82528645a54fb793d4d04e342629a3f51346";

    @Test
    void itRunsOnPushesToMainAndByHandOnlyNeverOnPullRequests() {
        YMap on = WORKFLOW.map("on");

        assertThat(on.entries().keySet()).containsExactlyInAnyOrder("push", "workflow_dispatch");
        assertThat(on.map("push").strings("branches")).containsExactly("main");
        assertThat(TEXT).doesNotContain("pull_request", "workflow_run");
        assertThat(BUILD.string("if")).isEqualTo(MAIN_ONLY);
        assertThat(DEPLOY.string("if")).isEqualTo(MAIN_ONLY);
    }

    @Test
    void permissionsAreEmptyExceptReadForTheBuildAndPagesAndOidcForTheDeploy() {
        assertThat(WORKFLOW.map("permissions").entries()).isEmpty();
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
    }

    @Test
    void theBuildChecksBeforeUseThenBuildsScansReChecksGuardsAndOnlyThenUploads() {
        List<YMap> steps = steps(BUILD);

        assertThat(steps.getFirst().string("uses")).isEqualTo(CHECKOUT);
        assertThat(texts(steps.getFirst().map("with"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "ref", "${{ github.sha }}", "persist-credentials", "false"));
        assertThat(order(steps, "stage-temurin.sh", SETUP_JAVA, "PolicyCheck.java . | tee -a \"$GITHUB_STEP_SUMMARY\"",
                "gradle-wrapper.jar\" | sha256sum --check --strict", ":tools:docs:check :tools:docs:site",
                ":tools:docs:scanSite", "mermaid.min.js\" | sha256sum --check --strict", "-type l", UPLOAD))
                .isSorted();
        assertThat(steps.getLast().string("uses")).isEqualTo(UPLOAD);
        assertThat(texts(steps.getLast().map("with"))).containsExactly(Map.entry("path", "tools/docs/build/site"));
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

        assertThat(header).contains("trusted bootstrap", "already on main")
                .doesNotContain("before any of its code runs");
        assertThat(TEXT).doesNotContain("before any of its code runs");
    }

    @Test
    void theDictionaryReachesTheScanStepAndNoOther() {
        List<YMap> withDictionary = steps(BUILD).stream()
                .filter(step -> step.find("env").map(env -> env.asMap("env").has("PUBLIC_SCAN_DICTIONARY"))
                        .orElse(false))
                .toList();

        assertThat(withDictionary).singleElement().satisfies(step -> {
            assertThat(step.string("run")).isEqualTo("./gradlew --no-daemon --console=plain :tools:docs:scanSite");
            assertThat(step.map("env").string("PUBLIC_SCAN_DICTIONARY"))
                    .isEqualTo("${{ secrets.PUBLIC_SCAN_DICTIONARY }}");
        });
        assertThat(count(TEXT, "PUBLIC_SCAN_DICTIONARY:")).isEqualTo(1);
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

        assertThat(actions).containsExactly(CHECKOUT, SETUP_JAVA, UPLOAD, DEPLOY_PAGES);
        assertThat(TEXT).doesNotContain("configure-pages", "include-hidden-files", "retention-days");
        assertThat(count(TEXT, "ref:")).isEqualTo(1);
    }

    @Test
    void theJdkIsInstalledFromTheStagedArchiveOnly() {
        YMap setupJava = steps(BUILD).stream().filter(step -> SETUP_JAVA.equals(step.optionalString("uses")
                .orElse(""))).findFirst().orElseThrow();

        assertThat(texts(setupJava.map("with"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "distribution", "jdkfile", "jdk-file", "${{ steps.jdk.outputs.jdk-file }}", "java-version", "25.0.5",
                "architecture", "x64", "token", ""));
    }

    private static List<YMap> steps(YMap job) {
        return job.list("steps").stream().map(step -> step.asMap("step")).toList();
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
