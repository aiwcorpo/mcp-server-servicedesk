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
    void escapedMarkupStaysTextAndIsNotDecodedTwice() {
        assertThat(PlainText.of("a &lt;script&gt; b &amp;lt; c &#65;&#x42;", 100)).isEqualTo("a <script> b &lt; c AB");
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
