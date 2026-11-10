package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.repo.ModelRuns;
import io.github.stigjohnny.docs.repo.ModelRuns.Run;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Compact phase disclosures; monetary values are never summed across accounting boundaries. */
final class ModelRunsPage {

    static final String PAGE = "trust/model-runs.html";

    private ModelRunsPage() {
    }

    static SitePage of(SiteContext context) {
        ModelRuns runs = ModelRuns.read(context.sources());
        StringBuilder body = new StringBuilder("<p class=\"prose\">")
                .append(Html.escape(runs.coverage())).append("</p>\n")
                .append("<p class=\"prose\">Completeness is reviewer-owned. Unknown is not zero. Estimates are not "
                        + "invoices. These observations do not establish a model ranking or total delivery cost.</p>\n");
        List<SitePage.Contents> contents = new ArrayList<>();
        Map<Integer, List<Run>> issues = new TreeMap<>();
        Map<String, List<Run>> corrections = new TreeMap<>();
        for (Run run : runs.records()) {
            issues.computeIfAbsent(run.issue(), ignored -> new ArrayList<>()).add(run);
            if (!run.field("correctionOf").equals("none")) {
                corrections.computeIfAbsent(run.field("correctionOf"), ignored -> new ArrayList<>()).add(run);
            }
        }
        if (issues.isEmpty()) {
            body.append("<p>No structured records supplied.</p>\n");
        }
        for (Map.Entry<Integer, List<Run>> issue : issues.entrySet()) {
            String anchor = "runs-issue-" + issue.getKey();
            context.map().anchor(PAGE, anchor);
            contents.add(new SitePage.Contents(anchor, "Issue " + issue.getKey()));
            body.append("<h2 id=\"").append(anchor).append("\">Issue ").append(issue.getKey()).append("</h2>\n");
            for (Run run : issue.getValue()) {
                phase(body, context, run, corrections.getOrDefault(run.field("id"), List.of()));
            }
        }
        context.sources().doc(ModelRuns.PATH).ifPresent(doc -> {
            var rendered = context.render(doc.body(), ModelRuns.PATH, PAGE, true);
            contents.addAll(DocPages.contents(rendered.headings()));
            body.append(rendered.html());
        });
        return new SitePage(PAGE, "Model runs and costs", "Model runs", SiteSection.TRUST,
                "Which model did the work, what happened, and what the recorded cost can establish. Open an attempt "
                        + "for scope, usage and receipts, including failed work.",
                new StatusPanel(Statuses.design("accepted", "Structured records under the same repository rules."),
                        Badge.NEUTRAL.html("Recorded observations") + " No benchmark or quality score.",
                        Badge.NEUTRAL.html("Structure validated")
                                + " This build does not authenticate receipts or detect missing changes."),
                contents, body.toString(), List.of(new SitePage.Related("trust/contributions.html", "Recording rules"),
                        new SitePage.Related("trust/contribution-history.html", "Contribution history")),
                ModelRuns.PATH, false);
    }

    private static void phase(StringBuilder body, SiteContext context, Run run, List<Run> corrections) {
        String id = "run-" + run.field("id");
        context.map().anchor(PAGE, id);
        String cost = run.cost().basis().equals("unknown") || run.cost().basis().equals("not-applicable")
                ? run.cost().basis() : run.cost().amount() + " " + run.cost().currency() + " (" + run.cost().basis() + ")";
        body.append("<details class=\"module\" id=\"").append(id).append("\"><summary>");
        if (!run.field("correctionOf").equals("none")) {
            body.append("Correction (not an additional charge) · ");
        }
        if (!corrections.isEmpty()) {
            body.append("Amended · ");
        }
        body
                .append(Html.escape(run.field("model") + " · " + run.field("role") + " · "
                        + run.field("outcome") + " · " + cost)).append("</summary>\n")
                .append("<h3>").append(Html.escape(run.field("id"))).append("</h3>\n");
        for (Run correction : corrections) {
            body.append("<p>").append(SiteContext.link(PAGE, PAGE + "#run-" + correction.field("id"),
                    "Amended by " + correction.field("id"))).append("; original assertion retained.</p>\n");
        }
        if (!run.field("correctionOf").equals("none")) {
            body.append("<p>").append(SiteContext.link(PAGE, PAGE + "#run-" + run.field("correctionOf"),
                    "Correction of " + run.field("correctionOf")))
                    .append(". Original retained; this is not an additional charge.</p>\n");
        }
        body.append("<dl class=\"facts\">\n");
        fact(body, "Work date / tool", run.field("date") + " / " + run.field("tool"));
        fact(body, "Configured model / checkpoint / effort", run.field("model") + " / " + run.field("checkpoint")
                + " / " + run.field("effort"));
        fact(body, "Task and base revision", run.field("scope") + " / " + run.field("base"));
        fact(body, "Environment and controls", run.field("environment"));
        fact(body, "Outcome and verification", run.field("result"));
        fact(body, "Usage", run.usage().state() + ": input " + run.usage().input() + ", output " + run.usage().output());
        fact(body, "Usage scope and receipt", run.usage().scope() + " / " + run.usage().receipt());
        fact(body, "Cost", cost);
        fact(body, "Cost scope and method", run.cost().scope() + " / " + run.cost().method());
        fact(body, "Cost receipt", run.cost().receipt());
        fact(body, "Evidence", run.field("evidence"));
        fact(body, "Limits", run.field("limitations"));
        body.append("</dl>\n</details>\n");
    }

    private static void fact(StringBuilder body, String title, String value) {
        body.append("<div><dt>").append(Html.escape(title)).append("</dt><dd>")
                .append(Html.escape(value)).append("</dd></div>\n");
    }
}
