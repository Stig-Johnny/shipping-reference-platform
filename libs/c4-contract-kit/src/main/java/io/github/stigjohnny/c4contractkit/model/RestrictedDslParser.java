package io.github.stigjohnny.c4contractkit.model;

import com.structurizr.Workspace;
import com.structurizr.dsl.Features;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.dsl.StructurizrDslParserException;
import com.structurizr.view.ImageView;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The one place that creates a {@link StructurizrDslParser} (security/reviews/maven--structurizr-dsl--6.2.4.md).
 *
 * <p>Every parser it creates has all ten Structurizr features switched off before it parses: no environment variables,
 * no file system access beyond the model file itself, no HTTP or HTTPS, no {@code !include}, no plugins, no scripts, no
 * component finder, no documentation and no decision import. It never allows a URL on the parser's HTTP client and
 * never uses the deprecated restricted mode. What the parser accepts but the kit must not (image views and themes,
 * which renderers fetch later, and unresolved {@code ${NAME}} references) is rejected after parsing.
 */
final class RestrictedDslParser {

    static final List<String> DISABLED_FEATURES = List.of(Features.ENVIRONMENT, Features.FILE_SYSTEM, Features.HTTP,
            Features.HTTPS, Features.INCLUDE, Features.PLUGINS, Features.SCRIPTS, Features.COMPONENT_FINDER,
            Features.DOCUMENTATION, Features.DECISIONS);

    private RestrictedDslParser() {
    }

    static StructurizrDslParser newParser() {
        StructurizrDslParser parser = new StructurizrDslParser();
        for (String feature : DISABLED_FEATURES) {
            parser.getFeatures().disable(feature);
        }
        return parser;
    }

    /** Parses one model file; problems name the file by {@code displayPath}, never by its absolute path. */
    static ParsedModel parse(Path dsl, String displayPath) throws InvalidModelException {
        if (!Files.isRegularFile(dsl)) {
            throw new InvalidModelException(List.of(displayPath + ": the model file does not exist"));
        }
        List<String> lines = lines(dsl, displayPath);
        StructurizrDslParser parser = newParser();
        File file = dsl.toFile();
        try {
            parser.parse(file);
        } catch (StructurizrDslParserException e) {
            String message = Objects.requireNonNullElse(e.getMessage(), "the DSL could not be parsed");
            throw new InvalidModelException(List.of(message.replace(file.getAbsolutePath(), displayPath)));
        }
        Workspace workspace = parser.getWorkspace();
        if (workspace == null) {
            throw new InvalidModelException(List.of(displayPath + ": the file defines no workspace"));
        }
        rejectWhatTheParserKept(workspace, lines, displayPath);
        return new ParsedModel(workspace, parser.getIdentifiersRegister());
    }

    /**
     * Rejects what the parser accepts but the kit must not: image views and themes, which renderers fetch, and
     * {@code ${NAME}} references the parser left as text because nothing defines them and environment variables are
     * off. A rejected construct is a model problem, never a silent pass.
     */
    private static void rejectWhatTheParserKept(Workspace workspace, List<String> lines, String displayPath)
            throws InvalidModelException {
        Problems problems = new Problems();
        for (ImageView view : workspace.getViews().getImageViews()) {
            problems.add("views", "image view '" + view.getKey()
                    + "' is not supported: image views make renderers fetch content");
        }
        for (String theme : workspace.getViews().getConfiguration().getThemes()) {
            problems.add("views", "theme '" + theme + "' is not supported: themes are fetched by renderers");
        }
        UnresolvedReferences.check(lines, displayPath, problems);
        problems.throwIfAny();
    }

    /** The model's lines, which the parser reads as UTF-8 too. */
    private static List<String> lines(Path dsl, String displayPath) throws InvalidModelException {
        try {
            return Files.readAllLines(dsl, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidModelException(List.of(displayPath + ": the model file is not readable UTF-8: " + e));
        }
    }
}
