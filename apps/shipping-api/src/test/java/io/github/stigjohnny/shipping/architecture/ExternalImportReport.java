package io.github.stigjohnny.shipping.architecture;

import io.github.stigjohnny.shipping.architecture.ExternalImportPolicy.Result;
import io.github.stigjohnny.shipping.architecture.ExternalImportPolicy.Use;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A policy result as JSON and Markdown: one row per class outside the API per class that uses it, with its provider,
 * scope, rule and review record. Rows are sorted, lines end in LF, and nothing depends on the time or the machine but
 * the inputs, which the reports name by their SHA-256.
 */
final class ExternalImportReport {

    static final Path DIRECTORY = Path.of("build/reports/external-imports");

    private ExternalImportReport() {
    }

    /** Writes {@code <name>.json} and {@code <name>.md} into the directory, before any assertion can fail. */
    static void write(Result result, Path directory, Map<String, String> inputs) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(result.name() + ".json"), json(result, inputs), StandardCharsets.UTF_8);
            Files.writeString(directory.resolve(result.name() + ".md"), markdown(result, inputs),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The SHA-256 of a file, as the reports name their inputs. */
    static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String json(Result result, Map<String, String> inputs) {
        StringBuilder json = new StringBuilder("{\n  \"schema\": \"external-imports/1\",\n  \"name\": ")
                .append(quote(result.name())).append(",\n  \"scope\": ").append(quote(result.scope().label()))
                .append(",\n  \"inputs\": {").append(inputs.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(input -> "\n    " + quote(input.getKey()) + ": " + quote(input.getValue()))
                        .collect(Collectors.joining(",")))
                .append("\n  },\n  \"uses\": ").append(result.uses().size()).append(",\n  \"denied\": ")
                .append(result.denied().size()).append(",\n  \"rows\": [");
        String separator = "\n    ";
        for (Use use : result.uses()) {
            json.append(separator).append("{\"outcome\": ").append(quote(use.outcome()))
                    .append(", \"caller\": ").append(quote(use.caller()))
                    .append(", \"area\": ").append(quote(use.area()))
                    .append(", \"target\": ").append(quote(use.target()))
                    .append(", \"provider\": ").append(quote(use.provider()))
                    .append(", \"scope\": ").append(quote(use.scope())).append(", \"rule\": ").append(quote(use.rule()))
                    .append(", \"review\": ").append(quote(use.review()))
                    .append(", \"message\": ").append(quote(use.message())).append('}');
            separator = ",\n    ";
        }
        return json.append("\n  ]\n}\n").toString();
    }

    static String markdown(Result result, Map<String, String> inputs) {
        List<Use> denied = result.denied();
        StringBuilder md = new StringBuilder("# External imports: ").append(result.name()).append("\n\n")
                .append("Every class outside `").append(ExternalImportPolicy.API).append("` that these classes use, ")
                .append("decided by ExternalImportPolicyTest (").append(result.scope().label()).append("). Inputs: ")
                .append(inputs.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(input -> input.getKey() + " `" + input.getValue() + "`").collect(Collectors.joining(", ")))
                .append(".\n\n").append(result.uses().size()).append(" uses, ").append(denied.size())
                .append(" denied.\n\n## Denied\n\n");
        if (denied.isEmpty()) {
            md.append("None.\n");
        }
        denied.forEach(use -> md.append("- `").append(use.caller()).append("` uses `").append(use.target())
                .append("`, rule ").append(use.rule()).append(": ").append(cell(use.message())).append('\n'));
        md.append("\n## Uses\n\n| Outcome | Caller | Area | Target | Provider | Scope | Rule | Review record |\n")
                .append("|---|---|---|---|---|---|---|---|\n");
        for (Use use : result.uses()) {
            md.append("| ").append(String.join(" | ", use.outcome(), "`" + use.caller() + "`", use.area(),
                    "`" + use.target() + "`", cell(use.provider()), use.scope(), cell(use.rule()),
                    cell(use.review()))).append(" |\n");
        }
        return md.toString();
    }

    private static String cell(String text) {
        return text.replace("|", "\\|");
    }

    private static String quote(String text) {
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                default -> json.append(c < 0x20 ? "\\u%04x".formatted((int) c) : String.valueOf(c));
            }
        }
        return json.append('"').toString();
    }
}
