package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.check.Consistency;
import io.github.stigjohnny.docs.generate.Brains;
import io.github.stigjohnny.docs.generate.Diagrams;
import io.github.stigjohnny.docs.repo.Allowlist;
import io.github.stigjohnny.docs.repo.AllowlistEntry;
import io.github.stigjohnny.docs.repo.ApiContract;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Builds the public site into a clean directory, deterministically: the same sources and revision give the same bytes,
 * with no time of the build anywhere. It writes only what it generates or copies from a known source: the pages, one
 * stylesheet, one script, the reviewed Mermaid bundle (checked against its allowlist SHA-256 first) and two downloads.
 */
public final class SiteBuilder {

    /** Where the vendored Mermaid bundle is served, byte-identical to the reviewed file. */
    public static final String MERMAID = "assets/mermaid/11.17.2/mermaid.min.js";
    static final String CSS = "assets/site.css";
    static final String JS = "assets/site.js";
    static final String MERMAID_SOURCE = "tools/docs/vendor/mermaid/11.17.2/mermaid.min.js";
    private static final String MERMAID_VERSION = "11.17.2";

    private SiteBuilder() {
    }

    /** Writes the site to {@code out}, which is emptied first; returns the number of files written. */
    public static int build(Sources sources, Path out, String revision) {
        if (!revision.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
            throw new DocsException("the source revision must be a full commit SHA: in CI it is GITHUB_SHA, locally "
                    + "the checked-out commit; the site never names a made-up revision");
        }
        Consistency.check(sources);
        SiteMap map = SiteMap.of(sources);
        SiteContext context = new SiteContext(sources, map, Diagrams.of(sources.workspace(), sources.elementStatus()));
        List<SitePage> pages = new ArrayList<>();
        pages.addAll(StartPages.of(context));
        int start = pages.size();
        pages.addAll(ModelPages.of(context));
        pages.addAll(ModulePages.of(context));
        pages.addAll(DecisionPages.of(context));
        pages.addAll(ApiPages.of(context));
        pages.addAll(TrustPages.of(context));
        pages.add(BrainPage.of(context));
        // The Demos section comes second in the register, but is built last, so that its links are checked against
        // every other page as built.
        pages.addAll(start, DemoPages.of(context, pages));
        SitePage notFound = NotFoundPage.of();
        Map<String, String> updated = new TreeMap<>();
        for (DocFile doc : sources.docs()) {
            map.site(doc.path()).ifPresent(page -> updated.put(page, doc.updated()));
        }
        Map<String, byte[]> files = new TreeMap<>();
        for (SitePage page : pages) {
            String html = PageShell.render(page, pages, revision, updated.get(page.path()),
                    target -> Urls.relative(page.path(), target));
            if (files.put(page.path(), html.getBytes(StandardCharsets.UTF_8)) != null) {
                throw new DocsException("two pages are written to " + page.path());
            }
        }
        files.put(notFound.path(), PageShell.render(notFound, pages, revision, null, NotFoundPage.urls())
                .getBytes(StandardCharsets.UTF_8));
        files.put(CSS, resource("site.css"));
        files.put(JS, resource("site.js"));
        files.put(MERMAID, mermaid(sources));
        files.put(SiteMap.CONTRACT_DOWNLOAD, Sources.bytes(sources.repo(), ApiContract.PATH));
        files.put(SiteMap.BRAIN_DOWNLOAD, Brains.root(sources).getBytes(StandardCharsets.UTF_8));
        write(files, out, sources.repo());
        return files.size();
    }

    /** The allowlist entry of the vendored Mermaid bundle; it must be approved. */
    static AllowlistEntry mermaidEntry(Sources sources) {
        return sources.allowlist().entries().stream()
                .filter(e -> e.kind().equals("vendored-js") && e.coordinate().equals("mermaid")
                        && e.version().equals(MERMAID_VERSION) && !e.rejected())
                .findFirst().orElseThrow(() -> new DocsException(Allowlist.PATH + " approves no Mermaid "
                        + MERMAID_VERSION + " bundle"));
    }

    /** The bundle's bytes, after checking them against the SHA-256 its review approved. */
    private static byte[] mermaid(Sources sources) {
        byte[] bytes = Sources.bytes(sources.repo(), MERMAID_SOURCE);
        String expected = mermaidEntry(sources).sha256();
        String actual = Allowlist.sha256(bytes);
        if (expected.isEmpty() || !expected.equalsIgnoreCase(actual)) {
            throw new DocsException(MERMAID_SOURCE + " has SHA-256 " + actual + ", not the reviewed " + expected
                    + "; the site never serves a changed bundle");
        }
        return bytes;
    }

    private static byte[] resource(String name) {
        try (InputStream in = SiteBuilder.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new DocsException("the docs tool has no resource " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new DocsException("the resource " + name + " cannot be read", e);
        }
    }

    /**
     * Empties {@code out}, then writes the files; symbolic links are never followed or created. Only an empty
     * directory or an earlier build of the site is emptied, so a mistyped directory is never deleted.
     */
    private static void write(Map<String, byte[]> files, Path out, Path repo) {
        Path target = out.toAbsolutePath().normalize();
        if (repo.toAbsolutePath().normalize().startsWith(target)) {
            throw new DocsException("the site's output directory contains the repository; refusing to empty it");
        }
        try {
            if (Files.exists(target) && !isEmptyOrASite(target)) {
                throw new DocsException("the site's output directory is not empty and holds no earlier build of the "
                        + "site; refusing to empty it");
            }
            if (Files.exists(target)) {
                try (Stream<Path> walk = Files.walk(target)) {
                    for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                }
            }
            Set<Path> directories = new HashSet<>();
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                Path path = target.resolve(file.getKey()).normalize();
                if (!path.startsWith(target)) {
                    throw new DocsException(file.getKey() + " would be written outside the site");
                }
                Path parent = path.getParent();
                if (parent != null && directories.add(parent)) {
                    Files.createDirectories(parent);
                }
                Files.write(path, file.getValue());
            }
        } catch (IOException e) {
            throw new DocsException("the site cannot be written: " + e.getClass().getSimpleName(), e);
        }
    }

    private static boolean isEmptyOrASite(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            if (entries.findAny().isEmpty()) {
                return true;
            }
        }
        return Files.isRegularFile(directory.resolve("index.html"), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(directory.resolve(JS), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(directory.resolve(CSS), LinkOption.NOFOLLOW_LINKS);
    }
}
