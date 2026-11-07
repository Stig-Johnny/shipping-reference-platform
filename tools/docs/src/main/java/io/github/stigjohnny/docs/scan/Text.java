package io.github.stigjohnny.docs.scan;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The views of a file that the scanner matches: HTML entities decoded, then NFKC, without invisible format characters
 * (zero-width spaces, soft hyphens, direction marks), and with every dash folded to {@code -}; for the dictionary also
 * lower case with runs of whitespace as one space, and for markup also with its tags removed.
 */
final class Text {

    private static final Pattern ENTITY = Pattern.compile(
            "&(?:#([0-9]{1,7});?|#[xX]([0-9A-Fa-f]{1,6});?|([A-Za-z][A-Za-z0-9]{1,31});)");
    private static final Pattern TAG = Pattern.compile("<[^<>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** The named entities that can spell text: the escapes, spaces, invisible characters, dashes and punctuation. */
    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("AMP", "&"), Map.entry("lt", "<"), Map.entry("LT", "<"),
            Map.entry("gt", ">"), Map.entry("GT", ">"), Map.entry("quot", "\""), Map.entry("QUOT", "\""),
            Map.entry("apos", "'"), Map.entry("nbsp", " "), Map.entry("shy", "­"),
            Map.entry("ensp", " "), Map.entry("emsp", " "), Map.entry("thinsp", " "),
            Map.entry("zwnj", "‌"), Map.entry("zwj", "‍"), Map.entry("lrm", "‎"),
            Map.entry("rlm", "‏"), Map.entry("NoBreak", "⁠"), Map.entry("hyphen", "‐"),
            Map.entry("dash", "‐"), Map.entry("ndash", "–"), Map.entry("mdash", "—"),
            Map.entry("minus", "−"), Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"),
            Map.entry("ldquo", "“"), Map.entry("rdquo", "”"), Map.entry("hellip", "…"),
            Map.entry("middot", "·"), Map.entry("colon", ":"), Map.entry("sol", "/"),
            Map.entry("bsol", "\\"), Map.entry("period", "."), Map.entry("commat", "@"),
            Map.entry("lowbar", "_"), Map.entry("UnderBar", "_"), Map.entry("num", "#"),
            Map.entry("percnt", "%"), Map.entry("plus", "+"), Map.entry("equals", "="),
            Map.entry("excl", "!"), Map.entry("quest", "?"), Map.entry("lpar", "("), Map.entry("rpar", ")"),
            Map.entry("lsqb", "["), Map.entry("rsqb", "]"), Map.entry("lcub", "{"), Map.entry("rcub", "}"),
            Map.entry("verbar", "|"), Map.entry("ast", "*"), Map.entry("comma", ","), Map.entry("semi", ";"),
            Map.entry("dollar", "$"), Map.entry("grave", "`"), Map.entry("Hat", "^"), Map.entry("Tab", "\t"),
            Map.entry("NewLine", "\n"));

    private Text() {
    }

    /** Decimal and hexadecimal character references and the named entities above; any other text stays as it is. */
    static String decodeEntities(String text) {
        if (text.indexOf('&') < 0) {
            return text;
        }
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            String replacement;
            if (matcher.group(1) != null || matcher.group(2) != null) {
                String decimal = matcher.group(1);
                int codePoint = decimal != null ? Integer.parseInt(decimal)
                        : Integer.parseInt(String.valueOf(matcher.group(2)), 16);
                replacement = codePoint > 0 && Character.isValidCodePoint(codePoint)
                        ? new String(Character.toChars(codePoint)) : "�";
            } else {
                replacement = NAMED.getOrDefault(String.valueOf(matcher.group(3)), matcher.group());
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** NFKC, without format characters, with every dash and the minus sign as {@code -}. */
    static String normalize(String text) {
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(nfkc.length());
        nfkc.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (type == Character.FORMAT) {
                return;
            }
            if (type == Character.DASH_PUNCTUATION || codePoint == 0x2212) {
                out.append('-');
            } else {
                out.appendCodePoint(codePoint);
            }
        });
        return out.toString();
    }

    /** The text of a file as the scanner's patterns see it. */
    static String decoded(String raw) {
        return normalize(decodeEntities(raw));
    }

    /** Markup without its tags, which are replaced by {@code separator}, then decoded and normalised. */
    static String withoutTags(String markup, String separator) {
        return decoded(TAG.matcher(markup).replaceAll(Matcher.quoteReplacement(separator)));
    }

    /** What a dictionary term is compared with: lower case, with every run of whitespace as one space. */
    static String forDictionary(String text) {
        return WHITESPACE.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ");
    }
}
