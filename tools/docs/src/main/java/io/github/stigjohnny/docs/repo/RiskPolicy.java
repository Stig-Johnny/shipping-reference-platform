package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import io.github.stigjohnny.docs.yaml.YamlInputException;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.util.ArrayList;
import java.util.List;

/** {@code policy/risk.json}: the path rules and signals that give a pull request its risk level. */
public record RiskPolicy(List<Rule> pathRules, List<Rule> behaviourSignals, List<Rule> uncertaintySignals) {

    public static final String PATH = "policy/risk.json";

    public RiskPolicy {
        pathRules = List.copyOf(pathRules);
        behaviourSignals = List.copyOf(behaviourSignals);
        uncertaintySignals = List.copyOf(uncertaintySignals);
    }

    /**
     * A rule or signal.
     *
     * @param level {@code 0} to {@code 3}, or {@code computed}
     * @param patterns the globs of a path rule; for signals the paths they apply to
     */
    public record Rule(String id, String level, String reason, List<String> patterns) {

        public Rule {
            patterns = List.copyOf(patterns);
        }
    }

    public static RiskPolicy parse(String json) {
        try {
            YMap root = SafeYaml.json(json, PATH).asMap(PATH);
            return new RiskPolicy(rules(root.list("pathRules"), "globs"), rules(root.list("behaviourSignals"),
                    "appliesTo"), rules(root.list("uncertaintySignals"), "exceptGlobs"));
        } catch (YamlInputException e) {
            throw new DocsException(e.getMessage(), e);
        }
    }

    private static List<Rule> rules(List<YamlValue> items, String patterns) {
        List<Rule> out = new ArrayList<>();
        for (YamlValue item : items) {
            YMap rule = item.asMap("rule");
            out.add(new Rule(rule.string("id"), rule.string("level"), rule.string("reason"),
                    rule.has(patterns) ? rule.strings(patterns) : List.of()));
        }
        return out;
    }
}
