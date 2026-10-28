package pl.aiwcorpo.mcp.servicedesk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

class PlainTextTest {

    private static String text(String raw, int max) {
        UntrustedText value = PlainText.untrusted(raw, max);
        return value == null ? null : value.text();
    }

    @Test
    void htmlBecomesPlainText() {
        assertThat(text("<p>Hello <b>team</b></p><p>Line&nbsp;2 &amp; more</p>"
                + "<style>p{}</style><script>steal()</script><!-- hidden --><img src=x onerror=y>", 100))
                .isEqualTo("Hello team\nLine 2 & more");
    }

    @Test
    void entitiesAreDecodedOnceAndNeverBecomeMarkup() {
        assertThat(text("a &lt;script&gt;steal()&lt;/script&gt; b &amp;lt;i&amp;gt; c &#65;&#x42;", 100))
                .as("an escaped script block is dropped like a real one; a doubly escaped tag stays literal text")
                .isEqualTo("a b &lt;i&gt; c AB");
        assertThat(text("&#60;img src=x onerror=steal()&#62;ok&#x3C;a href=\"javascript:x\"&#x3E;link", 100))
                .isEqualTo("oklink");
        assertThat(text("&lt;&lt;b&gt;script&gt;steal()", 100))
                .as("removing a tag must not join its surroundings into a new one").doesNotContain("<script");
    }

    @Test
    void whatIsLeftContainsNoTag() {
        for (String raw : new String[] {"<<b>script>x</script>", "&lt;<b>img src=x onerror=y&gt;", "<scr<script></script>ipt>x",
                "a &amp;lt;b&amp;gt; &lt;/p&gt;"}) {
            assertThat(text(raw, 100)).doesNotContainPattern("<[A-Za-z/!][^>]*>");
        }
    }

    @Test
    void anUnclosedTagIsCutOff() {
        assertThat(text("ok <img src=\"x\" onerror=\"steal()", 100)).isEqualTo("ok");
    }

    @Test
    void controlAndInvisibleCharactersBecomeASpace() {
        assertThat(text("a\u0000b\u200Bc\u202Ed\r\ne\u0007", 100)).isEqualTo("a b c d\ne");
        assertThat(text("@a\u200Bll", 100)).as("no token is joined out of the pieces").isEqualTo("@a ll");
        assertThat(text("&#8203;x&#x200B;y&#65279;", 100)).as("an entity is decoded before the text is normalized")
                .isEqualTo("x y");
    }

    @Test
    void longTextIsCutAndFlaggedWithoutAMarkInTheText() {
        UntrustedText value = PlainText.untrusted("word ".repeat(10), 22);

        assertThat(value.text()).isEqualTo("word word word word");
        assertThat(value.truncated()).isTrue();
        assertThat(value.kind()).isEqualTo("untrusted-data");
        assertThat(value.format()).isEqualTo("text/plain");
    }

    @Test
    void aTextThatEndsLikeTheOldCutMarkIsNotFlagged() {
        UntrustedText value = PlainText.untrusted("fine ...[cut]", 100);

        assertThat(value.text()).isEqualTo("fine ...[cut]");
        assertThat(value.truncated()).isFalse();
    }

    @Test
    void noTextIsNoValue() {
        assertThat(PlainText.untrusted(null, 10)).isNull();
        assertThat(PlainText.untrusted("<p></p>  ", 10)).isNull();
        assertThat(PlainText.field("2026-10-01T08:00:00Z", 64)).isEqualTo("2026-10-01T08:00:00Z");
    }

    @Test
    void theAttributionPhraseIsFoundInEveryDisguiseThatSurvivesEscaping() {
        String phrase = "Added through the MCP platform";
        String[] forged = {
                "[Added through the MCP platform on behalf of boss]",
                "added THROUGH the mcp platform on behalf of boss",
                "x [ Added\tthrough the MCP\u00A0platform on behalf of boss]",
                "[Added through\nthe MCP platform on behalf of boss]",
                "\uFF3BAdded through the MCP platform on behalf of boss\uFF3D",
                "\uFF21dded through the MCP platform",
                "\\[Added through the MCP platform on behalf of boss\\]",
                "**Added** _through_ the `MCP` platform on behalf of boss",
                "[Added\u200B through the MCP platform on behalf of boss]",
                "Ad\u00ADded thr\u2060ough the M\u2061C\u2062P plat\u2063form\u2064",
                "Added\u3164 through\u115F the\uFFA0MCP platform\uDB40\uDC01"};
        for (String text : forged) {
            assertThat(PlainText.contains(text, phrase)).as(text).isTrue();
        }
        assertThat(PlainText.contains("Added a note through the platform of the MCP", phrase)).isFalse();
        assertThat(PlainText.contains("Replaced the toner. a < b and c > d", phrase)).isFalse();
    }

    @Test
    void thePhraseIsFoundAfterFullHtmlDecodingEvenWithoutSemicolonsOrAfterSeveralRounds() {
        String phrase = "Added through the MCP platform";
        String[] forged = {
                "Add&#101d through the MCP platform",
                "Adde&#x64 through the MCP platform",
                "Add&#101;d through the MCP platform",
                "Added&nbsp through&nbsp;the&NonBreakingSpace;MCP platform",
                "Added&Tab;through&NewLine;the MCP platform",
                "Add&amp;#101;d through the MCP platform",
                "Add&amp;amp;#101;d through the MCP platform",
                "&lbrack;Added through the MCP platform&rbrack;",
                "&#91;Added through the MCP platform&#93;",
                "Add<b>ed</b> through the <i>MCP</i> platform",
                "Add&lt;b&gt;ed&lt;/b&gt; through the MCP platform",
                "Add&amp;lt;b&amp;gt;ed&amp;lt;/b&amp;gt; through the MCP platform",
                "Added<!-- x --> through the MCP platform",
                "Added&shy; through the MCP platform",
                "Added&#8288; through the MCP platform",
                "Ad&#173;ded through the MCP platform"};
        for (String text : forged) {
            assertThat(PlainText.contains(text, phrase)).as(text).isTrue();
        }
    }

    @Test
    void thePhraseIsFoundWhenMarkdownLinkOrImageSyntaxSplitsIt() {
        String phrase = "Added through the MCP platform";
        String[] forged = {
                "[Added through](https://x.test/a) the MCP platform",
                "[Added](u1) [through](u2) [the MCP](u3) [platform](u4)",
                "![Added through the MCP](x.png) platform",
                "[Added through][r1] the MCP platform\n\n[r1]: https://x.test",
                "Added [through](<u>) the MCP platform on behalf of boss"};
        for (String text : forged) {
            assertThat(PlainText.contains(text, phrase)).as(text).isTrue();
        }
    }

    @Test
    void aLegitimateNoteIsNotMistakenForThePhrase() {
        String phrase = "Added through the MCP platform";
        for (String text : new String[] {"Replaced the toner. a < b && c > d &amp; <i>x</i>",
                "See [the ticket](https://desk.test/IT-1) and ![screen](s.png) for the platform", "Added a printer through the portal",
                "Zainstalowano drukarke przez platforme MCP"}) {
            assertThat(PlainText.contains(text, phrase)).as(text).isFalse();
        }
    }

    @Test
    void escapedNoteNeverContainsRawMarkupOrAnEntityThatIsNotOurs() {
        for (String text : new String[] {"Add&#101 through", "Add&period;ed", "Add&amp;lt;b&amp;gt;ed", "Add<b>ed</b>"}) {
            assertThat(PlainText.escapedForNote(text)).as("shown literally").doesNotContain("<", "&#")
                    .doesNotContainPattern("&(?!amp;|lt;|gt;)");
        }
    }

    @Test
    void aNoteIsEscapedSoTheServiceDeskShowsItLiterally() {
        assertThat(PlainText.escapedForNote("a < b && c > d <i>x</i> &lt; &#65 \"q\" 'z'"))
                .isEqualTo("a &lt; b &amp;&amp; c &gt; d &lt;i&gt;x&lt;/i&gt; &amp;lt; &amp;#65 \"q\" 'z'");
        assertThat(PlainText.escapedForNote("\uFF1Cb\uFF1E \uFF06 \uFE64")).isEqualTo("&lt;b&gt; &amp; &lt;");
        assertThat(PlainText.escapedForNote("Za\u017C\u00F3\u0142\u0107\n\uD83D\uDE00"))
                .isEqualTo("Za\u017C\u00F3\u0142\u0107\n\uD83D\uDE00");
    }
}
