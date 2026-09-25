package com.escapii.service.email.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tekstualna verzija mora nositi poruku, a ne ostatke CSS-a i tagova. */
class PlainTextTest {

    @Test
    void tekstNemaOstatakaHtmlaNiCssa() {
        String html = EmailHtmlBuilder.wrapBase(
            "#a85e44", "", EmailHtmlBuilder.statusBadge("Uskoro", "orange"),
            "Escapii uskoro stiže!", "", "ESC-a1b2c3d4",
            "<p>Tvoj mejl je sad zvanično u našoj bazi.</p>"
            + "<p>Vidimo se uskoro. <strong>Tim Escapii</strong></p>",
            EmailHtmlBuilder.customerFooter("escapii.team@gmail.com"), false);

        String text = EmailSender.toPlainText(html);

        assertFalse(text.contains("<"), "zaostao HTML tag");
        assertFalse(text.contains("background-color"), "zaostao CSS");
        assertFalse(text.contains("mso"), "zaostao Outlook uslovni blok");
        assertFalse(text.contains("&nbsp;"), "zaostao HTML entitet");

        assertTrue(text.contains("Escapii uskoro stiže!"), "nedostaje naslov");
        assertTrue(text.contains("Tvoj mejl je sad zvanično u našoj bazi."), "nedostaje telo");
        assertTrue(text.contains("Tim Escapii"), "nedostaje potpis");
        assertTrue(text.contains("ESC-a1b2c3d4"), "nedostaje referenca");

        // Ne sme biti gomila praznih redova od tabela
        assertFalse(text.contains("\n\n\n"), "previše praznih redova");
    }

    /** đ i š u šablonima stoje kao numerički entiteti - moraju u tekst kao slova, ne da nestanu. */
    @Test
    void numerickiEntitetiSeDekodirajuANeBrisu() {
        assertEquals("Datum rođenja · Zemlja pasoša · Br. pasoša",
                EmailSender.toPlainText("Datum ro&#273;enja &middot; Zemlja paso&#353;a &middot; Br. paso&#353;a"));
        assertEquals("đ → Đ", EmailSender.toPlainText("&#x111; &rarr; &#272;"));
        assertEquals("Tom's", EmailSender.toPlainText("Tom&#39;s"));
        assertEquals("🎁", EmailSender.toPlainText("&#127873;"), "kod van BMP (surrogate par)");
        assertEquals("&#9999999999;", EmailSender.toPlainText("&#9999999999;"), "predugačak kod ostaje kakav je");
        assertEquals("A & B", EmailSender.toPlainText("A &#38; B"));
    }

    @Test
    void praznoIliCistTekstNePucaju() {
        assertEquals("", EmailSender.toPlainText(""));
        assertEquals("Zdravo", EmailSender.toPlainText("Zdravo"));
        assertEquals("A & B", EmailSender.toPlainText("A &amp; B"));
        assertEquals("Počinje 02.10.2026. Kraj", EmailSender.toPlainText("Počinje <strong>02.10.2026</strong>. Kraj"), "bez razmaka pre tačke iza zatvorenog taga");
    }
}
