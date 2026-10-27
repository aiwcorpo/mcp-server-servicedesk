package pl.aiwcorpo.mcp.servicedesk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlainTextTest {

    @Test
    void htmlBecomesPlainText() {
        assertThat(PlainText.of("<p>Hello <b>team</b></p><p>Line&nbsp;2 &amp; more</p>"
                + "<style>p{}</style><script>steal()</script><!-- hidden --><img src=x onerror=y>", 100))
                .isEqualTo("Hello team\nLine 2 & more");
    }

    @Test
    void entitiesAreDecodedOnceAndNeverBecomeMarkup() {
        assertThat(PlainText.of("a &lt;script&gt;steal()&lt;/script&gt; b &amp;lt;i&amp;gt; c &#65;&#x42;", 100))
                .as("an escaped script block is dropped like a real one; a doubly escaped tag stays literal text")
                .isEqualTo("a b &lt;i&gt; c AB");
        assertThat(PlainText.of("&#60;img src=x onerror=steal()&#62;ok&#x3C;a href=\"javascript:x\"&#x3E;link", 100))
                .isEqualTo("oklink");
        assertThat(PlainText.of("&lt;&lt;b&gt;script&gt;steal()", 100))
                .as("removing a tag must not join its surroundings into a new one").doesNotContain("<script");
    }

    @Test
    void whatIsLeftContainsNoTag() {
        for (String raw : new String[] {"<<b>script>x</script>", "&lt;<b>img src=x onerror=y&gt;", "<scr<script></script>ipt>x",
                "a &amp;lt;b&amp;gt; &lt;/p&gt;"}) {
            assertThat(PlainText.of(raw, 100)).doesNotContainPattern("<[A-Za-z/!][^>]*>");
        }
    }

    @Test
    void anUnclosedTagIsCutOff() {
        assertThat(PlainText.of("ok <img src=\"x\" onerror=\"steal()", 100)).isEqualTo("ok");
    }

    @Test
    void controlAndInvisibleCharactersAreRemoved() {
        assertThat(PlainText.of("a\u0000b\u200Bc\u202Ed\r\ne\u0007", 100)).isEqualTo("abcd\ne");
    }

    @Test
    void longTextIsCutWithAMark() {
        String text = PlainText.of("x".repeat(50), 20);

        assertThat(text).hasSize(20).endsWith(PlainText.CUT_MARK);
        assertThat(PlainText.of(null, 10)).isNull();
    }
}
