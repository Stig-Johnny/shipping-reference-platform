package io.github.stigjohnny.c4contractkit.model;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** The contract properties of a model (the header of architecture/workspace.dsl) and how list values are split. */
final class ContractProperties {

    static final String PREFIX = "contract.";
    static final String ROOT = "contract.root";
    static final String ISOLATED = "contract.isolated";
    static final String ID = "contract.id";
    static final String PACKAGES = "contract.packages";
    static final String STATUS = "contract.status";
    static final String MODE = "contract.mode";

    static final String PLANNED_TAG = "Planned";
    static final String REQUIRED_TAG = "Required";

    private static final Pattern COMMA = Pattern.compile(",");

    private ContractProperties() {
    }

    /** The entries of a comma-separated value, each stripped of surrounding whitespace; empty entries stay. */
    static List<String> entries(String value) {
        return Arrays.stream(COMMA.split(value, -1)).map(String::strip).toList();
    }

    /** Whether {@code name} is the package {@code root} itself or lies below it. */
    static boolean contains(String root, String name) {
        return name.equals(root) || name.startsWith(root + ".");
    }
}
