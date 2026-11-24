package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.repo.ApiContract;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.ProblemType;
import io.github.stigjohnny.docs.repo.Sources;
import java.util.Optional;

/**
 * The capabilities that name each HTTP operation and problem type (ADR 0025). A capability's {@code operations} are
 * operations of the contract tagged with its module, and its {@code problems} are types that the contract's registry
 * gives its module. Every operation, and every problem type that the registry gives a module, is named by at least one
 * capability, so that its state is known: it is planned while every capability that names it is planned
 * ({@link Capabilities#plannedOperation}). How a list is written is read with the capabilities.
 */
final class OperationBindings {

    private static final String ADR = " (ADR 0025)";

    private OperationBindings() {
    }

    static void check(Sources sources, Problems problems) {
        ApiContract contract = sources.contract();
        Capabilities capabilities = sources.capabilities();
        for (Capability capability : capabilities.capabilities()) {
            String where = Capabilities.PATH + " " + capability.id() + ": ";
            for (String id : capability.operations()) {
                Optional<ApiContract.Operation> operation = contract.operation(id);
                if (operation.isEmpty()) {
                    problems.add(where + "operation '" + id + "' is no operation of " + ApiContract.PATH + ADR);
                } else if (!operation.get().tags().contains(capability.module())) {
                    problems.add(where + "operation '" + id + "' is tagged " + String.join(", ",
                            operation.get().tags()) + ", not " + capability.module() + ADR);
                }
            }
            for (String type : capability.problems()) {
                Optional<ProblemType> registered = contract.problemType(type);
                if (registered.isEmpty()) {
                    problems.add(where + "problem type '" + type + "' is no type of the contract's problem registry"
                            + ADR);
                } else if (!registered.get().modules().contains(capability.module())) {
                    problems.add(where + "the registry gives the problem type '" + type + "' to "
                            + owners(registered.get()) + ", not to " + capability.module() + ADR);
                }
            }
        }
        for (ApiContract.Operation operation : contract.operations()) {
            if (capabilities.naming(operation.id()).isEmpty()) {
                problems.add(ApiContract.PATH + " " + operation.id() + ": no capability of " + Capabilities.PATH
                        + " names this operation, so its state is unknown" + ADR);
            }
        }
        for (ProblemType type : contract.problemTypes()) {
            if (!type.modules().isEmpty() && capabilities.namingProblem(type.type()).isEmpty()) {
                problems.add(ApiContract.PATH + ": no capability of " + Capabilities.PATH + " names the problem type `"
                        + type.type() + "`, which the registry gives to " + owners(type)
                        + ", so its state is unknown" + ADR);
            }
        }
    }

    private static String owners(ProblemType type) {
        return type.anyModule() ? "any module" : type.modules().isEmpty() ? "the HTTP edge"
                : String.join(", ", type.modules());
    }
}
