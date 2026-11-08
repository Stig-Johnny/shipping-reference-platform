package io.github.stigjohnny.c4contractkit.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The import cycles among files: the strongly connected components of the file import graph (Tarjan's algorithm)
 * with more than one file, or one file that imports itself. Each cycle is reported once, as its sorted files.
 */
final class ImportCycles {

    private final Map<String, Set<String>> graph;
    private final Map<String, Integer> index = new HashMap<>();
    private final Map<String, Integer> lowLink = new HashMap<>();
    private final Deque<String> stack = new ArrayDeque<>();
    private final Set<String> onStack = new TreeSet<>();
    private final List<List<String>> cycles = new ArrayList<>();

    private ImportCycles(Map<String, Set<String>> graph) {
        this.graph = graph;
    }

    /** Each cycle as its sorted files, the cycles sorted by their first file; edges given as file to imported files. */
    static List<List<String>> in(Map<String, Set<String>> graph) {
        ImportCycles search = new ImportCycles(graph);
        for (String file : new TreeSet<>(graph.keySet())) {
            if (!search.index.containsKey(file)) {
                search.visit(file);
            }
        }
        return search.cycles.stream().sorted((a, b) -> a.getFirst().compareTo(b.getFirst())).toList();
    }

    /** A number the search has already assigned. */
    private static int value(Map<String, Integer> numbers, String file) {
        return Objects.requireNonNull(numbers.get(file));
    }

    private void visit(String file) {
        int number = index.size();
        index.put(file, number);
        lowLink.put(file, number);
        stack.push(file);
        onStack.add(file);
        for (String imported : graph.getOrDefault(file, Set.of())) {
            if (!index.containsKey(imported)) {
                visit(imported);
                lowLink.put(file, Math.min(value(lowLink, file), value(lowLink, imported)));
            } else if (onStack.contains(imported)) {
                lowLink.put(file, Math.min(value(lowLink, file), value(index, imported)));
            }
        }
        if (value(lowLink, file) == value(index, file)) {
            List<String> component = new ArrayList<>();
            String member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.add(member);
            } while (!member.equals(file));
            if (component.size() > 1 || graph.getOrDefault(file, Set.of()).contains(file)) {
                cycles.add(component.stream().sorted().toList());
            }
        }
    }
}
