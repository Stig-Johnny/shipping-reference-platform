package io.github.stigjohnny.docs.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** A built site read back as data, for assertions on its pages, links and assets. */
public final class BuiltSite {

    private static final Pattern URL = Pattern.compile("\\s(href|src)=\"([^\"]*)\"");
    private static final Pattern ID = Pattern.compile("\\sid=\"([^\"]*)\"");

    private final Path root;
    private final Map<String, byte[]> files;

    private BuiltSite(Path root, Map<String, byte[]> files) {
        this.root = root;
        this.files = files;
    }

    public static BuiltSite read(Path root) {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BuiltSite(root, files);
    }

    public Path root() {
        return root;
    }

    public Map<String, byte[]> files() {
        return files;
    }

    /** The bytes of a file, failing when the site has none. */
    public byte[] bytes(String path) {
        byte[] bytes = files.get(path);
        if (bytes == null) {
            throw new AssertionError("the site has no " + path);
        }
        return bytes;
    }

    public Set<String> pages() {
        Set<String> pages = new TreeSet<>();
        files.keySet().stream().filter(p -> p.endsWith(".html")).forEach(pages::add);
        return pages;
    }

    public String text(String path) {
        byte[] bytes = files.get(path);
        if (bytes == null) {
            throw new AssertionError("the site has no " + path + "; it has " + files.keySet());
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Every href and src value of a page, in order. */
    public List<String> urls(String page) {
        List<String> urls = new ArrayList<>();
        Matcher m = URL.matcher(text(page));
        while (m.find()) {
            urls.add(m.group(2).replace("&amp;", "&"));
        }
        return urls;
    }

    public Set<String> ids(String page) {
        Set<String> ids = new TreeSet<>();
        Matcher m = ID.matcher(text(page));
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    /** The site path a relative URL of a page points to, without its fragment. */
    public static String resolve(String page, String url) {
        String path = url.contains("#") ? url.substring(0, url.indexOf('#')) : url;
        if (path.isEmpty()) {
            return page;
        }
        List<String> parts = new ArrayList<>(List.of(page.split("/", -1)));
        parts.removeLast();
        for (String part : path.split("/", -1)) {
            if ("..".equals(part)) {
                parts.removeLast();
            } else if (!".".equals(part) && !part.isEmpty()) {
                parts.add(part);
            }
        }
        return String.join("/", parts);
    }
}
