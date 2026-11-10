package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.yaml.YamlInputException;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import io.github.stigjohnny.docs.yaml.YamlValue.YNumber;
import io.github.stigjohnny.docs.yaml.YamlValue.YString;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Submitted phase records, not authenticated billing or a claim that all repository changes were recorded. */
public record ModelRuns(String coverage, List<Run> records) {

    public static final String PATH = "docs/contributions/runs.md";
    private static final Set<String> TEXT_FIELDS = Set.of("id", "date", "role", "tool", "model", "checkpoint",
            "effort", "base", "outcome", "scope", "environment", "result", "limitations", "evidence", "correctionOf");
    private static final Set<String> ROLES = Set.of("planning", "implementation", "integration", "validation",
            "independent-review", "human-decision");
    private static final Set<String> OUTCOMES = Set.of("completed", "partial", "rejected", "failed", "blocked",
            "abandoned");

    public ModelRuns {
        records = List.copyOf(records);
    }

    public record Usage(String state, String input, String output, String scope, String receipt) {
    }

    public record Cost(String basis, String amount, String currency, String scope, String method, String receipt) {
    }

    public record Run(int issue, Map<String, String> fields, Usage usage, Cost cost) {

        public Run {
            fields = Map.copyOf(fields);
        }

        public String field(String name) {
            return Objects.requireNonNull(fields.get(name));
        }
    }

    public static ModelRuns read(Sources sources) {
        return sources.doc(PATH).map(ModelRuns::parse)
                .orElseGet(() -> new ModelRuns("No structured records supplied", List.of()));
    }

    /** DocFile uses the existing bounded SafeYaml parser; this layer rejects coercions and accounting ambiguity. */
    public static ModelRuns parse(DocFile doc) {
        try {
            YMap root = doc.frontmatter();
            keys(root, Set.of("title", "description", "status", "updated", "schemaVersion", "coverage", "records"));
            require(number(root.get("schemaVersion"), "schemaVersion", 1) == 1, "schemaVersion must be 1");
            String coverage = text(root, "coverage");
            List<YamlValue> values = root.list("records");
            require(values.size() <= 1000, "at most 1000 records per register");
            List<Run> records = new ArrayList<>();
            Map<String, Integer> issuesById = new LinkedHashMap<>();
            for (YamlValue value : values) {
                YMap item = value.asMap("record");
                Set<String> expected = new java.util.HashSet<>(TEXT_FIELDS);
                expected.addAll(Set.of("issue", "usage", "cost"));
                keys(item, expected);
                Map<String, String> fields = new LinkedHashMap<>();
                for (String name : TEXT_FIELDS) {
                    fields.put(name, text(item, name));
                }
                String id = text(item, "id");
                require(id.matches("[a-z][a-z0-9-]{2,79}"), "id must be a stable slug of 3 to 80 characters");
                int issue = (int) number(item.get("issue"), "issue", 999_999_999);
                require(issue > 0, "issue must be positive");
                require(!issuesById.containsKey(id), "duplicate record id");
                choice(text(item, "role"), ROLES, "role");
                choice(text(item, "outcome"), OUTCOMES, "outcome");
                date(text(item, "date"));
                String base = text(item, "base");
                require(base.equals("unknown") || base.matches("[0-9a-f]{40}"), "base must be a full SHA or unknown");
                if (text(item, "role").equals("human-decision")) {
                    for (String field : List.of("model", "checkpoint", "effort")) {
                        require(text(item, field).equals("not-applicable"), field + " must be not-applicable for a human");
                    }
                }
                String correction = text(item, "correctionOf");
                require(correction.equals("none") || Objects.equals(issuesById.get(correction), issue),
                        "correctionOf must name an earlier record of the same issue or none");
                records.add(new Run(issue, fields, usage(item.map("usage")), cost(item.map("cost"))));
                issuesById.put(id, issue);
            }
            return new ModelRuns(coverage, records);
        } catch (YamlInputException e) {
            throw new DocsException(PATH + ": " + e.getMessage(), e);
        }
    }

    private static Usage usage(YMap map) {
        keys(map, Set.of("state", "input", "output", "scope", "receipt"));
        String state = text(map, "state");
        choice(state, Set.of("reported", "estimated", "unknown", "not-applicable"), "usage.state");
        String input = tokens(map.get("input"));
        String output = tokens(map.get("output"));
        if (state.equals("unknown") || state.equals("not-applicable")) {
            require(input.equals(state) && output.equals(state), "usage state must match both token values");
        } else {
            require(!input.equals("not-applicable") && !output.equals("not-applicable")
                    && !(input.equals("unknown") && output.equals("unknown")),
                    "reported or estimated usage needs at least one count");
            evidence(text(map, "receipt"), "usage.receipt");
        }
        return new Usage(state, input, output, text(map, "scope"), text(map, "receipt"));
    }

    private static Cost cost(YMap map) {
        keys(map, Set.of("basis", "amount", "currency", "scope", "method", "receipt"));
        String basis = text(map, "basis");
        choice(basis, Set.of("billed", "metered-estimate", "api-equivalent-estimate", "estimate", "unknown",
                "not-applicable"), "cost.basis");
        String amount = text(map, "amount");
        String currency = text(map, "currency");
        if (basis.equals("unknown") || basis.equals("not-applicable")) {
            require(amount.equals(basis) && currency.equals(basis), "cost basis must match amount and currency");
        } else {
            require(amount.matches("(?:0|[1-9][0-9]{0,8})(?:\\.[0-9]{1,6})?"),
                    "cost.amount must be a nonnegative quoted decimal, at most 9 integer and 6 fractional digits");
            amount = new BigDecimal(amount).toPlainString();
            require(currency.matches("[A-Z]{3}"), "cost.currency must be a three-letter uppercase currency code");
            evidence(text(map, "receipt"), "cost.receipt");
            evidence(text(map, "method"), "cost.method");
        }
        return new Cost(basis, amount, currency, text(map, "scope"), text(map, "method"), text(map, "receipt"));
    }

    private static String tokens(YamlValue value) {
        if (value instanceof YString string) {
            choice(string.value(), Set.of("unknown", "not-applicable"), "token sentinel");
            return string.value();
        }
        return Long.toString(number(value, "token count", 1_000_000_000_000L));
    }

    private static long number(YamlValue value, String name, long max) {
        if (!(value instanceof YNumber number) || !number.text().matches("0|[1-9][0-9]{0,12}")) {
            throw new YamlInputException(name + " must be a bounded nonnegative integer");
        }
        long result = Long.parseLong(number.text());
        require(result <= max, name + " is above its supported bound");
        return result;
    }

    private static String text(YMap map, String name) {
        if (!(map.get(name) instanceof YString string) || string.value().isBlank()
                || string.value().length() > 2000) {
            throw new YamlInputException(name + " must be a nonblank string of at most 2000 characters");
        }
        return string.value();
    }

    private static void date(String date) {
        if (date.equals("unknown")) {
            return;
        }
        require(date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), "date must be ISO YYYY-MM-DD or unknown");
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new YamlInputException("date must be a real calendar date");
        }
    }

    private static void keys(YMap map, Set<String> keys) {
        require(map.entries().keySet().equals(keys), "record keys must match the documented schema exactly");
    }

    private static void choice(String value, Set<String> choices, String field) {
        require(choices.contains(value), field + " is not a supported value");
    }

    private static void evidence(String value, String field) {
        require(!Set.of("unknown", "not-applicable", "not applicable", "none", "n/a")
                .contains(value.strip().toLowerCase(Locale.ROOT)), field + " needs a reference or method, not a missing marker");
    }

    private static void require(boolean condition, String problem) {
        if (!condition) {
            throw new YamlInputException(problem);
        }
    }
}
