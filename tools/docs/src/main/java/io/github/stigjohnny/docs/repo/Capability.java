package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import java.util.List;

/**
 * One entry of {@code docs/capabilities/capabilities.yaml}: a capability of a module or a control.
 *
 * @param module the C4 component's {@code contract.id} or {@code web}; empty for controls
 * @param where for controls, the paths that implement them; empty for capabilities
 * @param check for controls, the CI check that runs their tests; empty for capabilities
 * @param evidence test names that exist and pass at this commit; empty means "none yet"
 * @param acceptanceTests the tests that must exist and pass before the status may be shipped
 */
public record Capability(String id, boolean control, String module, String where, String check, String title,
        Status status, String target, List<String> evidence, List<String> acceptanceTests, String docs,
        String negativeFixture) {

    public Capability {
        evidence = List.copyOf(evidence);
        acceptanceTests = List.copyOf(acceptanceTests);
    }

    /** The truth at the current commit, as capabilities.yaml defines it. */
    public enum Status {
        /** Every acceptance test exists and passes. */
        SHIPPED("shipped", "Shipped"),
        /** At least one acceptance test exists and passes. */
        PARTIAL("partial", "Partial"),
        /** No acceptance test exists yet. */
        PLANNED("planned", "Planned");

        private final String value;
        private final String words;

        Status(String value, String words) {
            this.value = value;
            this.words = words;
        }

        public String value() {
            return value;
        }

        /** The status in words, for badges: a status is never shown by colour alone. */
        public String words() {
            return words;
        }

        public static Status of(String value, String where) {
            for (Status status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            throw new DocsException(where + ": unknown status '" + value
                    + "' (shipped, partial or planned)");
        }
    }
}
