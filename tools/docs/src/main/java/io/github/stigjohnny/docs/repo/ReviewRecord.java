package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A security review record under {@code security/reviews/}: its frontmatter and the public summary the allowlist page
 * shows, which is the first paragraph of the review's "Verdict" section. The rest of the review stays in the private
 * repository.
 */
public record ReviewRecord(DocFile file, String summary) {

    private static final Pattern VERDICT = Pattern.compile("^##\\s+(\\d+\\.\\s+)?Verdict\\b.*");

    public static ReviewRecord parse(String path, String text) {
        DocFile file = DocFile.parse(path, text);
        return new ReviewRecord(file, verdictParagraph(file));
    }

    public String component() {
        return file.text("component");
    }

    public String verdict() {
        return file.text("verdict");
    }

    private static String verdictParagraph(DocFile file) {
        List<String> paragraph = new ArrayList<>();
        boolean inVerdict = false;
        for (String line : file.body().split("\n", -1)) {
            if (!inVerdict) {
                inVerdict = VERDICT.matcher(line).matches();
                continue;
            }
            if (line.startsWith("#")) {
                break;
            }
            if (line.isBlank()) {
                if (!paragraph.isEmpty()) {
                    break;
                }
                continue;
            }
            paragraph.add(line);
        }
        if (paragraph.isEmpty()) {
            throw new DocsException(file.path() + ": the review has no Verdict section with a paragraph to summarise");
        }
        return String.join("\n", paragraph);
    }
}
