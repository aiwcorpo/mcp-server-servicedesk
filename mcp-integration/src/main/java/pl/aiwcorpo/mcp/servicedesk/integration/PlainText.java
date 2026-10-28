package pl.aiwcorpo.mcp.servicedesk.integration;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
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

    private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[([^\\]]*)]\\([^)]*\\)|!?\\[([^\\]]*)]\\[[^\\]]*]");
    // Some of these are letters to Unicode (Hangul fillers), so reducing to letters and digits would keep them.
    private static final Pattern DEFAULT_IGNORABLE = Pattern.compile("[\\u00AD\\u034F\\u061C\\u115F\\u1160\\u17B4\\u17B5"
            + "\\u180B-\\u180F\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\u3164\\uFE00-\\uFE0F\\uFEFF\\uFFA0"
            + "\\uFFF0-\\uFFF8\\x{1BCA0}-\\x{1BCA3}\\x{1D173}-\\x{1D17A}\\x{E0000}-\\x{E0FFF}]");
    private static final int DECODE_ROUNDS = 5;
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private PlainText() {
    }

    /**
     * Whether {@code text} could read as {@code phrase} to anyone downstream, fail-closed. The note is checked in
     * every form a reader or a further system might make of it: as it is; fully decoded as HTML5 (entities with
     * and without a semicolon, tags removed), repeated to a fixed point because another system may decode again;
     * and each of these with Markdown link and image syntax reduced to its visible text. Every form goes through
     * {@link TextHygiene#normalize}, NFKC, removal of default-ignorable characters and reduction to lower-case
     * letters and digits. The note is sent escaped ({@link #escapedForNote}), which is the first line of defence;
     * this check does not rely on it. Look-alike letters from other alphabets are not mapped.
     */
    static boolean contains(String text, String phrase) {
        String wanted = reduced(phrase);
        for (String form : forms(text)) {
            if (reduced(form).contains(wanted) || reduced(MARKDOWN_LINK.matcher(form).replaceAll("$1$2")).contains(wanted)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> forms(String text) {
        List<String> forms = new ArrayList<>();
        forms.add(text);
        String unescaped = text;
        String parsed = text;
        for (int round = 0; round < DECODE_ROUNDS; round++) {
            String nextUnescaped = Parser.unescapeEntities(unescaped, false);
            String nextParsed = Jsoup.parse(parsed).wholeText();
            if (nextUnescaped.equals(unescaped) && nextParsed.equals(parsed)) {
                break;
            }
            forms.add(nextUnescaped);
            forms.add(nextParsed);
            forms.add(Jsoup.parse(nextUnescaped).wholeText());
            unescaped = nextUnescaped;
            parsed = nextParsed;
        }
        return forms;
    }

    /**
     * The model's note as text that a service desk showing HTML displays literally: {@code &}, {@code <} and
     * {@code >} become entities, and so do their compatibility forms (full-width, small), which a downstream NFKC
     * would turn back into the ASCII characters. No decoder on our side has to guess what the other system's
     * parser makes of the text.
     */
    static String escapedForNote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        text.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            String compatible = codePoint < 128 ? character : Normalizer.normalize(character, Normalizer.Form.NFKC);
            switch (compatible) {
                case "&" -> out.append("&amp;");
                case "<" -> out.append("&lt;");
                case ">" -> out.append("&gt;");
                default -> out.append(character);
            }
        });
        return out.toString();
    }

    private static String reduced(String text) {
        String compatible = Normalizer.normalize(TextHygiene.normalize(text), Normalizer.Form.NFKC);
        String visible = DEFAULT_IGNORABLE.matcher(compatible).replaceAll("");
        return NOT_LETTER_OR_DIGIT.matcher(visible).replaceAll("").toLowerCase(Locale.ROOT);
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
