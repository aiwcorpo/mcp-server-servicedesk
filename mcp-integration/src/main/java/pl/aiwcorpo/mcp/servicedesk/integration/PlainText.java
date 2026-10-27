package pl.aiwcorpo.mcp.servicedesk.integration;

import java.util.regex.Pattern;

/**
 * Service desk text as plain text: descriptions are often HTML from a rich-text editor, and markup is neither
 * needed by the model nor safe to pass on. Tags are dropped (script and style with their content), the common
 * entities decoded, control characters removed and the length capped.
 */
final class PlainText {

    static final String CUT_MARK = " ...[cut]";

    private static final Pattern DROPPED_BLOCKS =
            Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>|<!--.*?-->");
    private static final Pattern LINE_BREAK_TAGS =
            Pattern.compile("(?i)<\\s*(br|/p|/div|/li|/tr|/h[1-6])\\b[^>]*>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9A-Fa-f]{1,6}|[0-9]{1,7});");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\n\\t]]");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n\\s*\\n+");

    private PlainText() {
    }

    static String of(String raw, int max) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        if (text.indexOf('<') >= 0) {
            text = DROPPED_BLOCKS.matcher(text).replaceAll("");
            text = LINE_BREAK_TAGS.matcher(text).replaceAll("\n");
            text = TAG.matcher(text).replaceAll("");
            // A lone '<' that never closes would hide the rest of a tag from TAG; drop what is left of it.
            int open = text.indexOf('<');
            if (open >= 0 && text.indexOf('>', open) < 0) {
                text = text.substring(0, open);
            }
        }
        if (text.indexOf('&') >= 0) {
            text = decodeEntities(text);
        }
        text = CONTROL.matcher(text).replaceAll("");
        text = SPACES.matcher(text).replaceAll(" ");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n").strip();
        if (text.length() > max) {
            text = text.substring(0, max - CUT_MARK.length()) + CUT_MARK;
        }
        return text;
    }

    private static String decodeEntities(String text) {
        String named = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
        StringBuilder out = new StringBuilder();
        var matcher = NUMERIC_ENTITY.matcher(named);
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
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        // &amp; last, so "&amp;lt;" stays the literal text "&lt;".
        return out.toString().replace("&amp;", "&");
    }
}
