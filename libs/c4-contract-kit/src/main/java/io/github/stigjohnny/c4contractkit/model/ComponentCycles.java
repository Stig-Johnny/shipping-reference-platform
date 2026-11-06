package io.github.stigjohnny.c4contractkit.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds the cycles in the component relationship graph. The graph has one node per component, so a reachability
 * matrix is small; components that reach each other form one cycle, reported once.
 */
final class ComponentCycles {

    private ComponentCycles() {
    }

    /** Each cycle as the sorted names of its components, for edges given as name to target names. */
    static List<List<String>> in(Map<String, ? extends Collection<String>> edges) {
        List<String> nodes = new ArrayList<>(new TreeSet<>(nodes(edges)));
        int size = nodes.size();
        boolean[][] reaches = new boolean[size][size];
        edges.forEach((source, targets) -> targets.forEach(
                target -> reaches[nodes.indexOf(source)][nodes.indexOf(target)] = true));
        for (int via = 0; via < size; via++) {
            for (int from = 0; from < size; from++) {
                for (int to = 0; to < size; to++) {
                    reaches[from][to] = reaches[from][to] || (reaches[from][via] && reaches[via][to]);
                }
            }
        }
        Set<List<String>> cycles = new LinkedHashSet<>();
        for (int node = 0; node < size; node++) {
            if (reaches[node][node]) {
                List<String> cycle = new ArrayList<>();
                for (int other = 0; other < size; other++) {
                    if (reaches[node][other] && reaches[other][node]) {
                        cycle.add(nodes.get(other));
                    }
                }
                cycles.add(List.copyOf(cycle));
            }
        }
        return List.copyOf(cycles);
    }

    private static Set<String> nodes(Map<String, ? extends Collection<String>> edges) {
        Set<String> nodes = new TreeSet<>(edges.keySet());
        edges.values().forEach(nodes::addAll);
        return nodes;
    }
}
