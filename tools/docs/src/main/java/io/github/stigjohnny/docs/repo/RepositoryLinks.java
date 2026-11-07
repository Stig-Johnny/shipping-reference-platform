package io.github.stigjohnny.docs.repo;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Pattern;

/** Classifies a link of a repository document: external, the private repository, a repository file, or broken. */
public final class RepositoryLinks {

    /** The private repository's location; the public cannot open it, so the site never links to it. */
    public static final String PRIVATE_REPOSITORY = "github.com/Stig-Johnny/shipping-reference-platform";

    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");

    /** Where a link points. */
    public sealed interface Target {
    }

    /** An http, https or mailto URL outside the repository. */
    public record External(String url) implements Target {
    }

    /** A URL inside the private repository on GitHub. */
    public record PrivateRepository() implements Target {
    }

    /** A file or directory of the repository, with an optional fragment. */
    public record RepoFile(String path, String fragment) implements Target {
    }

    /** A relative link to something that does not exist. */
    public record Missing(String path) implements Target {
    }

    /** A destination this tool never publishes. */
    public record Unsafe(String reason) implements Target {
    }

    private RepositoryLinks() {
    }

    public static Target classify(Path repo, String sourcePath, String destination) {
        java.util.regex.Matcher scheme = SCHEME.matcher(destination);
        if (scheme.find()) {
            String name = scheme.group(1).toLowerCase(Locale.ROOT);
            if ("mailto".equals(name)) {
                return new External(destination);
            }
            if (!"http".equals(name) && !"https".equals(name)) {
                return new Unsafe("the scheme '" + name + ":' is not allowed");
            }
            return privateRepository(destination) ? new PrivateRepository() : new External(destination);
        }
        if (destination.startsWith("//")) {
            return new Unsafe("protocol-relative URLs are not allowed");
        }
        if (destination.indexOf('?') >= 0 || destination.indexOf('\\') >= 0) {
            return new Unsafe("relative links may not carry a query or a backslash");
        }
        int hash = destination.indexOf('#');
        String path = hash < 0 ? destination : destination.substring(0, hash);
        String fragment = hash < 0 ? "" : destination.substring(hash + 1);
        String resolved = resolve(sourcePath, path);
        if (resolved == null) {
            return new Unsafe("the link leaves the repository");
        }
        return Files.exists(repo.resolve(resolved)) ? new RepoFile(resolved, fragment) : new Missing(resolved);
    }

    static boolean privateRepository(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath() == null ? "" : uri.getRawPath().toLowerCase(Locale.ROOT);
            String repository = "/" + PRIVATE_REPOSITORY.substring(PRIVATE_REPOSITORY.indexOf('/') + 1)
                    .toLowerCase(Locale.ROOT);
            boolean github = "github.com".equals(host) || "www.github.com".equals(host);
            return github && (path.equals(repository) || path.equals(repository + ".git")
                    || path.startsWith(repository + "/"));
        } catch (URISyntaxException e) {
            return url.toLowerCase(Locale.ROOT).contains(PRIVATE_REPOSITORY.toLowerCase(Locale.ROOT));
        }
    }

    /** The repository-relative path of a link from a source file, or null when it climbs out of the repository. */
    static @org.jspecify.annotations.Nullable String resolve(String sourcePath, String link) {
        Deque<String> parts = new ArrayDeque<>();
        if (!link.startsWith("/")) {
            int slash = sourcePath.lastIndexOf('/');
            if (slash > 0) {
                for (String part : sourcePath.substring(0, slash).split("/", -1)) {
                    parts.addLast(part);
                }
            }
        }
        for (String part : link.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (parts.isEmpty()) {
                    return null;
                }
                parts.removeLast();
            } else {
                parts.addLast(part);
            }
        }
        return String.join("/", parts);
    }
}
