package io.github.stigjohnny.docs.site;

import java.util.ArrayList;
import java.util.List;

/** Relative URLs between site paths, so that the site works under any base path, from a local server and offline. */
public final class Urls {

    private Urls() {
    }

    /** The URL of {@code target} (a site path, optionally with a fragment) as seen from the page {@code from}. */
    public static String relative(String from, String target) {
        String fragment = "";
        String path = target;
        int hash = target.indexOf('#');
        if (hash >= 0) {
            fragment = target.substring(hash);
            path = target.substring(0, hash);
        }
        if (path.equals(from)) {
            return fragment.isEmpty() ? lastSegment(path) : fragment;
        }
        List<String> fromDirs = directories(from);
        List<String> toParts = new ArrayList<>(List.of(path.split("/", -1)));
        int common = 0;
        while (common < fromDirs.size() && common < toParts.size() - 1
                && fromDirs.get(common).equals(toParts.get(common))) {
            common++;
        }
        StringBuilder out = new StringBuilder();
        out.append("../".repeat(fromDirs.size() - common));
        out.append(String.join("/", toParts.subList(common, toParts.size())));
        return out + fragment;
    }

    private static List<String> directories(String page) {
        List<String> parts = new ArrayList<>(List.of(page.split("/", -1)));
        parts.removeLast();
        return parts;
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
