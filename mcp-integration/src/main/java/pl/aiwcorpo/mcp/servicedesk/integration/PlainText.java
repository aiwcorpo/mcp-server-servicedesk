package pl.aiwcorpo.mcp.servicedesk.integration;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.TextHygiene;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * Service desk text as plain text: descriptions are often HTML from a rich-text editor, and markup is neither
 * needed by the model nor safe to pass on.
 *
 * <p>Order, as the starter prescribes: the conversion from HTML (entities decoded first, then tags dropped, script
 * and style with their content, so an escaped tag cannot come out as markup), {@link TextHygiene#normalize}, the cut
 * to the limit ({@link UntrustedText#cut}), and the value built from the cut text. Normalization only removes or
 * replaces by a space, so it cannot make a tag or an entity out of what the conversion left. White space is
 * tidied after it, because normalization can add spaces. What comes out is text in which {@code <} and {@code &}
 * are ordinary characters; the client does not render it as HTML and nothing escapes them.
 */
final class PlainText {

    private static final Pattern DROPPED_BLOCKS =
            Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>|<!--.*?-->");
    private static final Pattern LINE_BREAK_TAGS =
            Pattern.compile("(?i)<\\s*(br|/p|/div|/li|/tr|/h[1-6])\\b[^>]*>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9A-Fa-f]{1,6}|[0-9]{1,7});");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n\\s*\\n+");

    private static final Pattern HTML_TAG = Pattern.compile("</?[A-Za-z!][^>]*>");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int ENTITY_ROUNDS = 4;

    private PlainText() {
    }

    /**
     * Whether {@code text} contains {@code phrase} as a reader of the service desk would read it, whatever it is
     * dressed in: entities (decoded repeatedly, because a second decoding may happen downstream), tags
     * (only things an HTML parser takes for a tag, so a stray {@code <} does not swallow what follows),
     * full-width and other compatibility forms (NFKC), invisible characters, Markdown escapes and emphasis,
     * brackets, case and spacing. Everything but letters and digits is dropped from both sides before comparing.
     * Look-alike letters from other alphabets are not mapped.
     */
    static boolean contains(String text, String phrase) {
        String current = text;
        for (int round = 0; round < ENTITY_ROUNDS && current.indexOf('&') >= 0; round++) {
            current = decodeEntities(current);
        }
        for (String previous = null; !current.equals(previous); ) {
            previous = current;
            current = HTML_TAG.matcher(current).replaceAll("");
        }
        return reduced(current).contains(reduced(phrase));
    }

    private static String reduced(String text) {
        String compatible = Normalizer.normalize(TextHygiene.normalize(text), Normalizer.Form.NFKC);
        return NOT_LETTER_OR_DIGIT.matcher(compatible).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** Free text of a ticket (subject, description) as untrusted data; {@code null} when there is no text. */
    @Nullable
    static UntrustedText untrusted(@Nullable String raw, int max) {
        String clean = clean(raw);
        if (clean == null) {
            return null;
        }
        String kept = UntrustedText.cut(clean, max);
        return UntrustedText.plainCut(kept, kept.length() < clean.length());
    }

    /** A short value of the system (status, priority, time): the same conversion, cut without a mark. */
    @Nullable
    static String field(@Nullable String raw, int max) {
        String clean = clean(raw);
        return clean == null ? null : UntrustedText.cut(clean, max);
    }

    @Nullable
    private static String clean(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        if (text.indexOf('&') >= 0) {
            text = decodeEntities(text);
        }
        text = TextHygiene.normalize(stripMarkup(text));
        text = SPACES.matcher(text).replaceAll(" ");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n").strip();
        return text.isEmpty() ? null : text;
    }

    /**
     * Repeated until nothing changes: removing a tag can join the characters around it into a new one
     * ({@code <<b>script>}).
     */
    private static String stripMarkup(String text) {
        String previous;
        do {
            previous = text;
            if (text.indexOf('<') >= 0) {
                text = DROPPED_BLOCKS.matcher(text).replaceAll("");
                text = LINE_BREAK_TAGS.matcher(text).replaceAll("\n");
                text = TAG.matcher(text).replaceAll("");
            }
        } while (!text.equals(previous));
        // A lone '<' that never closes would hide the rest of a tag from TAG; drop what is left of it.
        int open = text.indexOf('<');
        return open >= 0 && text.indexOf('>', open) < 0 ? text.substring(0, open) : text;
    }

    private static String decodeEntities(String text) {
        String named = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
        StringBuilder out = new StringBuilder();
        Matcher matcher = NUMERIC_ENTITY.matcher(named);
        while (matcher.find()) {
            String code = matcher.group(1);
            int codePoint;
            try {
                codePoint = code.startsWith("x") ? Integer.parseInt(code.substring(1), 16) : Integer.parseInt(code);
            } catch (NumberFormatException tooLong) {
                codePoint = -1;
            }
            String replacement = Character.isValidCodePoint(codePoint) && codePoint != 0
                    ? new String(Character.toChars(codePoint)) : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        // &amp; last, so "&amp;lt;" stays the literal text "&lt;".
        return out.toString().replace("&amp;", "&");
    }
}
