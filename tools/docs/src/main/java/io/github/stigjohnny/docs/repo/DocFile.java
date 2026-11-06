package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import io.github.stigjohnny.docs.yaml.YamlInputException;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.util.Optional;

/**
 * A Markdown file with YAML frontmatter.
 *
 * @param path the repository-relative path, with forward slashes
 * @param frontmatter the YAML between the leading {@code ---} lines
 * @param body the Markdown after the frontmatter
 */
public record DocFile(String path, YMap frontmatter, String body) {

    private static final String FENCE = "---\n";

    /** Splits a file into frontmatter and body; a file without frontmatter fails. */
    public static DocFile parse(String path, String text) {
        if (!text.startsWith(FENCE)) {
            throw new DocsException(path + ": the file does not start with YAML frontmatter ('---')");
        }
        int end = text.indexOf("\n---\n", FENCE.length() - 1);
        if (end < 0) {
            throw new DocsException(path + ": the frontmatter is not closed with '---'");
        }
        String yaml = text.substring(FENCE.length(), end + 1);
        String body = text.substring(end + "\n---\n".length());
        try {
            return new DocFile(path, SafeYaml.yaml(yaml, path + " (frontmatter)").asMap("frontmatter"), body);
        } catch (YamlInputException e) {
            throw new DocsException(e.getMessage(), e);
        }
    }

    public String title() {
        return text("title");
    }

    public String description() {
        return text("description");
    }

    public String status() {
        return text("status");
    }

    public String updated() {
        return text("updated");
    }

    public Optional<String> optional(String key) {
        return frontmatter.optionalString(key);
    }

    public String text(String key) {
        try {
            return frontmatter.string(key);
        } catch (YamlInputException e) {
            throw new DocsException(path + ": frontmatter " + e.getMessage(), e);
        }
    }

    /** The file name without directories. */
    public String fileName() {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
