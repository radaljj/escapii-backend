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

    @Test
    void praznoIliCistTekstNePucaju() {
        assertEquals("", EmailSender.toPlainText(""));
        assertEquals("Zdravo", EmailSender.toPlainText("Zdravo"));
        assertEquals("A & B", EmailSender.toPlainText("A &amp; B"));
    }
}
