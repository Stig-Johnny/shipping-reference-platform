package io.github.stigjohnny.docs.model;

import java.util.Locale;

/** Human titles and file names for views, derived from the model so that nothing about a view is hard-coded. */
public final class ViewNames {

    private ViewNames() {
    }

    /** "System context", "Containers", "Components of the API", or a dynamic view's first sentence. */
    public static String title(C4View view) {
        return switch (view.kind()) {
            case SYSTEM_CONTEXT -> "System context";
            case CONTAINER -> "Containers";
            case COMPONENT -> "Components of the " + view.scopeName();
            case DYNAMIC -> firstSentence(view.description(), view.key());
        };
    }

    /** The C4 kind in words, for captions and tables. */
    public static String kind(C4View view) {
        return switch (view.kind()) {
            case SYSTEM_CONTEXT -> "System context view";
            case CONTAINER -> "Container view";
            case COMPONENT -> "Component view";
            case DYNAMIC -> "Dynamic view";
        };
    }

    /** The view key in kebab case: {@code ApiComponents} becomes {@code api-components}. */
    public static String slug(C4View view) {
        StringBuilder out = new StringBuilder();
        String key = view.key();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(key.charAt(i - 1))) {
                out.append('-');
            }
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            } else if (!out.isEmpty() && out.charAt(out.length() - 1) != '-') {
                out.append('-');
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    private static String firstSentence(String description, String fallback) {
        String text = description.strip();
        int end = text.indexOf(". ");
        String sentence = end < 0 ? text : text.substring(0, end);
        if (sentence.endsWith(".")) {
            sentence = sentence.substring(0, sentence.length() - 1);
        }
        return sentence.isEmpty() ? fallback : sentence;
    }
}
