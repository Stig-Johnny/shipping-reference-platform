package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.Html;
import io.github.stigjohnny.docs.markdown.RenderedMarkdown;
import io.github.stigjohnny.docs.repo.ApiContract;
import io.github.stigjohnny.docs.repo.ApiContract.Operation;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The API section, generated from {@code contracts/shipping.openapi.yaml}: an operation index with the contract's own
 * conventions and RFC 9457 error table, one page per operation, the schemas, and the contract as a download.
 */
final class ApiPages {

    static final String INDEX = "api/index.html";

    private ApiPages() {
    }

    static List<SitePage> of(SiteContext context) {
        List<SitePage> pages = new ArrayList<>();
        pages.add(index(context));
        for (Operation operation : context.sources().contract().operations()) {
            pages.add(operation(context, operation));
        }
        pages.add(schemas(context));
        return pages;
    }

    private static SitePage index(SiteContext context) {
        ApiContract contract = context.sources().contract();
        HtmlTable table = HtmlTable.of("Operations of the contract (" + contract.operations().size() + ")",
                "Operations, scrolls sideways", "Operation", "Method and path", "Summary", "Module");
        for (Operation operation : contract.operations()) {
            table.row(SiteContext.link(INDEX, page(operation), operation.id()),
                    "<code>" + Html.escape(operation.method() + " " + contract.serverUrl() + operation.path())
                            + "</code>", Html.escape(operation.summary()), modules(context, operation, INDEX));
        }
        YMap info = contract.root().map("info");
        RenderedMarkdown description = context.render(info.optionalString("description").orElse(""),
                ApiContract.PATH, INDEX, false);
        String body = "<p class=\"prose\">The contract is the authority for the HTTP interface (ADR 0004). "
                + SiteContext.link(INDEX, SiteMap.CONTRACT_DOWNLOAD, "Download the contract") + " (OpenAPI "
                + Html.escape(contract.root().string("openapi")) + ", YAML), exactly as committed.</p>\n"
                + "<h2 id=\"operations\">Operations</h2>\n" + table.html()
                + "<h2 id=\"schemas-link\">Schemas</h2>\n<p class=\"prose\">"
                + SiteContext.link(INDEX, SchemaText.SCHEMAS, "Every schema of the contract")
                + ", with its properties and constraints.</p>\n" + description.html();
        List<SitePage.Contents> contents = new ArrayList<>(List.of(new SitePage.Contents("operations", "Operations"),
                new SitePage.Contents("schemas-link", "Schemas")));
        contents.addAll(DocPages.contents(description.headings()));
        for (String id : List.of("operations", "schemas-link")) {
            context.map().anchor(INDEX, id);
        }
        description.headings().forEach(h -> context.map().anchor(INDEX, h.id()));
        return new SitePage(INDEX, "API: " + contract.title() + " " + contract.version(), "Overview",
                SiteSection.API, info.optionalString("summary").orElse(""), status(context, contract.operations()),
                contents, body, context.mentioned(info.optionalString("description").orElse("")
                        + " ADR 0004", INDEX), ApiContract.PATH, false);
    }

    private static SitePage operation(SiteContext context, Operation operation) {
        ApiContract contract = context.sources().contract();
        String page = page(operation);
        StringBuilder body = new StringBuilder("<dl class=\"facts\">\n")
                .append(fact("Method", "<code>" + Html.escape(operation.method()) + "</code>"))
                .append(fact("Path", "<code>" + Html.escape(contract.serverUrl() + operation.path()) + "</code>"))
                .append(fact("Module", modules(context, operation, page)))
                .append(fact("Authentication", Html.escape(security(operation, contract))))
                .append("</dl>\n");
        if (!operation.description().isBlank()) {
            body.append(context.render(operation.description(), ApiContract.PATH, page, false).html());
        }
        body.append("<h2 id=\"parameters\">Parameters</h2>\n");
        List<YamlValue> parameters = operation.definition().find("parameters").map(v -> v.asList("parameters"))
                .orElse(List.of());
        if (parameters.isEmpty()) {
            body.append("<p class=\"prose\">None.</p>\n");
        } else {
            HtmlTable table = HtmlTable.of("Parameters of " + operation.id(), "Parameters of " + operation.id()
                    + ", scrolls sideways", "Name", "In", "Required", "Schema", "Description");
            for (YamlValue item : parameters) {
                YMap parameter = resolved(item.asMap("parameter"), contract);
                table.row("<code>" + Html.escape(parameter.string("name")) + "</code>", Html.escape(parameter
                        .string("in")), Html.escape(parameter.optionalString("required").orElse("false")
                        .equals("true") ? "required" : "optional"), SchemaText.describe(parameter.map("schema"), page),
                        context.fragment(parameter.optionalString("description").orElse(""), ApiContract.PATH, page));
            }
            body.append(table.html());
        }
        body.append("<h2 id=\"request-body\">Request body</h2>\n");
        if (operation.definition().has("requestBody")) {
            YMap request = resolved(operation.definition().map("requestBody"), contract);
            body.append("<p class=\"prose\">").append(request.optionalString("required").orElse("false")
                    .equals("true") ? "Required." : "Optional.").append("</p>\n");
            body.append(content(request, page));
        } else {
            body.append("<p class=\"prose\">None.</p>\n");
        }
        body.append("<h2 id=\"responses\">Responses</h2>\n");
        HtmlTable responses = HtmlTable.of("Responses of " + operation.id(), "Responses of " + operation.id()
                + ", scrolls sideways", "Status", "Description", "Content", "Headers");
        StringBuilder examples = new StringBuilder();
        for (Map.Entry<String, YamlValue> entry : operation.definition().map("responses").entries().entrySet()) {
            YMap response = resolved(entry.getValue().asMap(entry.getKey()), contract);
            List<String> headers = response.find("headers").map(h -> h.asMap("headers").entries().keySet().stream()
                    .map(name -> "<code>" + Html.escape(name) + "</code>").toList()).orElse(List.of());
            responses.row("<code>" + Html.escape(entry.getKey()) + "</code>",
                    context.fragment(response.optionalString("description").orElse(""), ApiContract.PATH, page),
                    mediaTypes(response, page), headers.isEmpty() ? "" : String.join(", ", headers));
            examples.append(examples(response, entry.getKey()));
        }
        body.append(responses.html());
        body.append("<p class=\"prose\">Errors are RFC 9457 problem documents (<code>application/problem+json</code>); "
                + "their types are listed on the ").append(SiteContext.link(page, INDEX, "API overview"))
                .append(".</p>\n");
        if (!examples.isEmpty()) {
            body.append("<h2 id=\"examples\">Examples</h2>\n").append(examples);
        }
        List<SitePage.Contents> contents = new ArrayList<>(List.of(new SitePage.Contents("parameters", "Parameters"),
                new SitePage.Contents("request-body", "Request body"), new SitePage.Contents("responses", "Responses")));
        if (!examples.isEmpty()) {
            contents.add(new SitePage.Contents("examples", "Examples"));
        }
        List<SitePage.Related> related = new ArrayList<>();
        related.add(new SitePage.Related(INDEX, "API overview"));
        related.add(new SitePage.Related(SchemaText.SCHEMAS, "Schemas"));
        for (String tag : operation.tags()) {
            context.map().site("docs/modules/" + tag + ".md")
                    .ifPresent(p -> related.add(new SitePage.Related(p, context.titleOf(p))));
        }
        return new SitePage(page, operation.id() + ": " + operation.method() + " " + contract.serverUrl()
                + operation.path(), operation.id(), SiteSection.API, operation.summary(),
                status(context, List.of(operation)), contents, body.toString(), related, ApiContract.PATH + ", operation "
                        + operation.id(), false);
    }

    private static SitePage schemas(SiteContext context) {
        ApiContract contract = context.sources().contract();
        String page = SchemaText.SCHEMAS;
        StringBuilder body = new StringBuilder();
        List<SitePage.Contents> contents = new ArrayList<>();
        for (Map.Entry<String, YamlValue> entry : contract.root().map("components").map("schemas").entries()
                .entrySet()) {
            String name = entry.getKey();
            YMap schema = entry.getValue().asMap(name);
            String id = SchemaText.anchor(name);
            context.map().anchor(page, id);
            contents.add(new SitePage.Contents(id, name));
            body.append("<h2 id=\"").append(id).append("\">").append(Html.escape(name)).append("</h2>\n");
            schema.optionalString("description").ifPresent(d -> body.append(context.render(d, ApiContract.PATH,
                    page, false).html()));
            if (schema.has("properties")) {
                List<String> required = schema.has("required") ? schema.strings("required") : List.of();
                HtmlTable table = HtmlTable.of("Properties of " + name, "Properties of " + name
                        + ", scrolls sideways", "Property", "Schema", "Required", "Description");
                for (Map.Entry<String, YamlValue> property : schema.map("properties").entries().entrySet()) {
                    YMap definition = property.getValue().asMap(property.getKey());
                    table.row("<code>" + Html.escape(property.getKey()) + "</code>",
                            SchemaText.describe(definition, page),
                            Html.escape(required.contains(property.getKey()) ? "required" : "optional"),
                            context.fragment(definition.optionalString("description").orElse(""), ApiContract.PATH,
                                    page));
                }
                body.append("<p class=\"prose\">").append(SchemaText.describe(withoutProperties(schema), page))
                        .append("</p>\n").append(table.html());
            } else {
                body.append("<p class=\"prose\">").append(SchemaText.describe(schema, page)).append("</p>\n");
            }
        }
        return new SitePage(page, "Schemas", "Schemas", SiteSection.API, "Every schema of the contract, with its "
                + "properties and constraints.", status(context, contract.operations()), contents, body.toString(),
                List.of(new SitePage.Related(INDEX, "API overview")), ApiContract.PATH + ", components.schemas", false);
    }

    private static YMap withoutProperties(YMap schema) {
        Map<String, YamlValue> entries = new LinkedHashMap<>(schema.entries());
        entries.remove("properties");
        entries.remove("required");
        entries.remove("description");
        return new YMap(entries);
    }

    private static String content(YMap holder, String page) {
        StringBuilder out = new StringBuilder("<dl class=\"facts\">\n");
        for (Map.Entry<String, YamlValue> media : holder.map("content").entries().entrySet()) {
            YMap definition = media.getValue().asMap(media.getKey());
            out.append(fact("<code>" + Html.escape(media.getKey()) + "</code>", definition.has("schema")
                    ? SchemaText.describe(definition.map("schema"), page) : "no schema"));
        }
        out.append("</dl>\n");
        for (Map.Entry<String, YamlValue> media : holder.map("content").entries().entrySet()) {
            YMap definition = media.getValue().asMap(media.getKey());
            definition.find("examples").ifPresent(examples -> {
                for (Map.Entry<String, YamlValue> example : examples.asMap("examples").entries().entrySet()) {
                    out.append(exampleBlock("Example " + example.getKey() + " (" + media.getKey() + ")",
                            example.getValue().asMap(example.getKey())));
                }
            });
        }
        return out.toString();
    }

    private static String examples(YMap response, String status) {
        StringBuilder out = new StringBuilder();
        response.find("content").ifPresent(content -> {
            for (Map.Entry<String, YamlValue> media : content.asMap("content").entries().entrySet()) {
                media.getValue().asMap(media.getKey()).find("examples").ifPresent(examples -> {
                    for (Map.Entry<String, YamlValue> example : examples.asMap("examples").entries().entrySet()) {
                        out.append(exampleBlock("Response " + status + ", example " + example.getKey(),
                                example.getValue().asMap(example.getKey())));
                    }
                });
            }
        });
        return out.toString();
    }

    private static String exampleBlock(String title, YMap example) {
        return "<details class=\"example\">\n<summary>" + Html.escape(title) + "</summary>\n<pre><code>"
                + Html.escape(JsonText.pretty(example.get("value"))) + "</code></pre>\n</details>\n";
    }

    private static String mediaTypes(YMap response, String page) {
        if (!response.has("content")) {
            return "none";
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, YamlValue> media : response.map("content").entries().entrySet()) {
            YMap definition = media.getValue().asMap(media.getKey());
            out.add("<code>" + Html.escape(media.getKey()) + "</code>" + (definition.has("schema")
                    ? ": " + SchemaText.describe(definition.map("schema"), page) : ""));
        }
        return String.join("<br>", out);
    }

    private static YMap resolved(YMap value, ApiContract contract) {
        return value.has("$ref") ? contract.resolve(value.string("$ref")) : value;
    }

    private static String security(Operation operation, ApiContract contract) {
        YamlValue security = operation.definition().find("security").orElse(contract.root().find("security")
                .orElse(new YamlValue.YList(List.of())));
        List<String> schemes = new ArrayList<>();
        for (YamlValue requirement : security.asList("security")) {
            schemes.addAll(requirement.asMap("security requirement").entries().keySet());
        }
        return schemes.isEmpty() ? "none" : String.join(", ", schemes)
                + " (demo bearer tokens; see the overview's Authentication section)";
    }

    private static String modules(SiteContext context, Operation operation, String from) {
        List<String> links = new ArrayList<>();
        for (String tag : operation.tags()) {
            links.add(context.map().site("docs/modules/" + tag + ".md")
                    .map(p -> SiteContext.link(from, p, tag)).orElse(Html.escape(tag)));
        }
        return String.join(", ", links);
    }

    private static String fact(String term, String description) {
        return "<div><dt>" + term + "</dt><dd>" + description + "</dd></div>\n";
    }

    static String page(Operation operation) {
        return "api/" + operation.id() + ".html";
    }

    private static StatusPanel status(SiteContext context, List<Operation> operations) {
        List<Capability> capabilities = new ArrayList<>();
        operations.stream().flatMap(o -> o.tags().stream()).distinct()
                .forEach(tag -> capabilities.addAll(context.sources().capabilities().ofModule(tag)));
        List<Capability> contractControls = context.sources().capabilities().controls().stream()
                .filter(c -> c.id().equals("control.http-contract")).toList();
        return new StatusPanel(Statuses.design("accepted", "Contract-first (ADR 0004): OpenAPI "
                + context.sources().contract().root().string("openapi") + ", API version "
                + context.sources().contract().version() + "."), Statuses.implementation(capabilities),
                Statuses.evidence(contractControls, context.sources().evidence()));
    }
}
