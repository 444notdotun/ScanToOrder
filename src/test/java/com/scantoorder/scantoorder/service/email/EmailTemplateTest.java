package com.scantoorder.scantoorder.service.email;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmailTemplateTest {

    private static EmailTemplate.Rendered sample() {
        return EmailTemplate.of("Sunset Grill", "Reset your password")
                .paragraph("Someone asked to reset the password.")
                .details(List.of(new EmailTemplate.Row("Username", "sunset.manager")))
                .lines(List.of(new EmailTemplate.Row("2 × Jollof", "₦13,000")), "Total", "₦13,000")
                .button("Choose a new password", "https://www.platelyy.com/reset-password?token=abc_-123")
                .note("If you didn't ask, ignore this email.")
                .render();
    }

    @Test
    void testThatTheHtmlIsOneColumnOfTablesWithInlineStylesAndTheLogoAsItsOnlyImage() {
        String html = sample().html();

        assertTrue(html.contains("max-width:600px"));
        assertTrue(html.contains("background:#E9E2D2"));
        assertTrue(html.contains("background:#FBF7EF"));
        assertTrue(html.contains("border-radius:14px"));
        assertTrue(html.contains("height:3px"));
        assertTrue(html.contains("<img src=\"https://www.platelyy.com/email/plately-logo.png\" width=\"150\" alt=\"Plately\""));
        assertEquals(1, html.split("<img ", -1).length - 1, "the logo is the only image");
        assertEquals(1, html.split("<style>", -1).length - 1, "one style block, for the dark look");
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<link"));
        assertFalse(html.contains("@font-face"));
        assertFalse(html.contains("url("));
        assertTrue(html.contains("Georgia"));
        assertTrue(html.contains("Helvetica"));
    }

    @Test
    void testThatTheEmailSaysItSupportsLightAndDarkAndCarriesADarkVersion() {
        String html = sample().html();

        assertTrue(html.contains("<meta name=\"color-scheme\" content=\"light dark\">"));
        assertTrue(html.contains("<meta name=\"supported-color-schemes\" content=\"light dark\">"));
        assertTrue(html.contains(":root{color-scheme:light dark;"));
        assertTrue(html.contains("@media (prefers-color-scheme: dark)"));
        assertTrue(html.contains("[data-ogsb] .pg-ground{background-color:#0F1C16"));
        assertTrue(html.contains("[data-ogsc] .pg-text{color:#F4ECD8"));
        for (String colour : List.of("#0F1C16", "#16261F", "#F4ECD8", "#E8C77A", "#C9D3C4", "#1E3229", "#2B4237", "#F0B94A", "#10281F")) {
            assertTrue(html.contains(colour), colour);
        }
        // The inline styles stay the light email.
        assertTrue(html.contains("background:#E9E2D2"));
        assertTrue(html.contains("background:#FBF7EF"));
    }

    @Test
    void testThatTheHeaderAndTheGoldKeepTheirColoursWhenAMailAppRepaintsBackgrounds() {
        String html = sample().html();

        assertTrue(html.contains("class=\"pg-header\" style=\"background:#10281F;background-image:linear-gradient(#10281F,#10281F);"));
        assertEquals(2, html.split("background-image:linear-gradient\\(#F0B94A,#F0B94A\\);", -1).length - 1, "the 3px gold line and the button");
        assertTrue(html.contains("class=\"pg-btncell\" style=\"background:#F0B94A;background-image:linear-gradient(#F0B94A,#F0B94A);border-radius:999px;"));
        assertTrue(html.contains("class=\"pg-btn\" style=\"display:inline-block;padding:15px 28px;font-family:Helvetica, Arial, sans-serif;font-size:16px;font-weight:700;line-height:18px;color:#10281F;"));
        assertTrue(html.contains("https://www.platelyy.com/email/plately-logo.png"));
    }

    @Test
    void testThatTheButtonIsAGoldPillAndTheLinkIsWrittenOutUnderIt() {
        String html = sample().html();

        assertTrue(html.contains("background:#F0B94A"));
        assertTrue(html.contains("border-radius:999px"));
        assertTrue(html.contains("Choose a new password"));
        assertTrue(html.contains("Button not working? Copy this link into your browser:"));
        assertEquals(2, html.split("href=\"https://www.platelyy.com/reset-password\\?token=abc_-123\"", -1).length - 1);
    }

    @Test
    void testThatTheFooterNamesSupportAndPlatformAlertsHaveOnlyTheirOwnLine() {
        String normal = sample().html();
        assertTrue(normal.contains("Plately &middot; Order from your table, no waiting."));
        assertTrue(normal.contains("Questions? Reply to this email or write to"));
        assertTrue(normal.contains("support@platelyy.com"));

        String alert = EmailTemplate.of("Waiting for you", "New on Plately: X").paragraph("Hi.").platformOnly().render().html();
        assertTrue(alert.contains("Sent to the platform owner only."));
        assertFalse(alert.contains("Questions?"));
        assertFalse(alert.contains("no waiting"));
    }

    @Test
    void testThatThePlainTextHasTheSameWordsWithTheLinkOnItsOwnLine() {
        String text = sample().text();

        assertTrue(text.contains("Reset your password"));
        assertTrue(text.contains("Someone asked to reset the password."));
        assertTrue(text.contains("Username: sunset.manager"));
        assertTrue(text.contains("Total  ₦13,000"));
        assertTrue(text.contains("Choose a new password:\nhttps://www.platelyy.com/reset-password?token=abc_-123\n"));
        assertTrue(text.contains("If you didn't ask, ignore this email."));
        assertTrue(text.contains("support@platelyy.com"));
        assertFalse(text.contains("<"));
    }

    @Test
    void testThatEveryValueFromAPersonIsEscapedInTheHtml() {
        String nasty = "<script>alert(1)</script> & \"Mama\" <b>Put</b>";
        EmailTemplate.Rendered rendered = EmailTemplate.of(nasty, nasty)
                .paragraph(nasty)
                .details(List.of(new EmailTemplate.Row(nasty, nasty)))
                .lines(List.of(new EmailTemplate.Row(nasty, nasty)), nasty, nasty)
                .button(nasty, "https://example.com/?a=\"><script>x</script>")
                .note(nasty)
                .small(nasty)
                .render();

        assertFalse(rendered.html().contains("<script"));
        assertFalse(rendered.html().contains("<b>Put"));
        assertTrue(rendered.html().contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;Mama&quot;"));
        // The text version is plain words, never html, so nothing in it is escaped.
        assertTrue(rendered.text().contains(nasty));
    }

    @Test
    void testThatALinkThatIsNotAWebAddressNeverBecomesAnHref() {
        String html = EmailTemplate.of("e", "h").button("Go", "javascript:alert(1)").render().html();

        assertFalse(html.contains("href=\"javascript"));
        assertTrue(html.contains("href=\"#\""));
    }
}
