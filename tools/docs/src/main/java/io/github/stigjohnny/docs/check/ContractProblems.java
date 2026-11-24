package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.repo.ApiContract;
import io.github.stigjohnny.docs.repo.ProblemType;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The problem types of the contract's responses (ADR 0025). The contract names a response's types only in its prose,
 * so every response of an operation with status 400 or above starts its description with them: code spans joined by
 * commas and "or", each with an optional note in parentheses, ended by a colon, a full stop or the end
 * ({@link ApiContract#problemTypesNamed}). Each must be a type of the problem registry, with the response's status;
 * code spans after them are prose. A response is keyed by its status code, and its description is written where it
 * is defined, never beside a {@code $ref}, so that each use gives the same types. The registry itself is read when the
 * contract is ({@link ApiContract#problemTypes}).
 */
final class ContractProblems {

    private static final String ADR = " (ADR 0025)";

    private ContractProblems() {
    }

    static void check(ApiContract contract, Problems problems) {
        for (ApiContract.Operation operation : contract.operations()) {
            for (Map.Entry<String, YamlValue> entry : operation.definition().map("responses").entries().entrySet()) {
                String where = ApiContract.PATH + " " + operation.id() + " " + entry.getKey() + ": ";
                if (!entry.getKey().matches("[1-5][0-9]{2}")) {
                    problems.add(where + "a response is keyed by its status code; default and ranges such as 4XX are "
                            + "not read" + ADR);
                    continue;
                }
                YMap use = entry.getValue().asMap(where);
                if (use.has("$ref") && use.has("description")) {
                    problems.add(where + "write the description in the response it references; a description beside "
                            + "a response's $ref is not read" + ADR);
                    continue;
                }
                int status = Integer.parseInt(entry.getKey());
                if (status < 400) {
                    continue;
                }
                YMap response = contract.ref(use).map(contract::resolve).orElse(use);
                List<String> types = ApiContract.problemTypesNamed(response.optionalString("description").orElse(""));
                if (types.isEmpty()) {
                    problems.add(where + "start the description of a problem response with its problem types, such as "
                            + "`quote-not-found` or `offer-not-found`" + ADR);
                }
                for (String type : types) {
                    Optional<ProblemType> registered = contract.problemType(type);
                    if (registered.isEmpty()) {
                        problems.add(where + "`" + type + "` is no type of the problem registry" + ADR);
                    } else if (registered.get().status() != status) {
                        problems.add(where + "the registry gives `" + type + "` the status "
                                + registered.get().status() + ADR);
                    }
                }
            }
        }
    }
}
