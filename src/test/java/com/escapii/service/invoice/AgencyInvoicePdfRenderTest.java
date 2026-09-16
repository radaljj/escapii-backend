package com.escapii.service.invoice;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PDF zbirne fakture: sadrži broj, stavku, period, iznos i agenciju; dok podaci firme nisu
 * uneti ne prikazuje nule ni „placeholder", nego napomenu. Snima PDF+PNG u
 * target/agency-invoice-preview/ za pregled okom.
 */
class AgencyInvoicePdfRenderTest {

    private final InvoicePdfService svc = new InvoicePdfService();

    private static AgencyInvoiceData data(String pib, String mb, String account, String bank) {
        return new AgencyInvoiceData("ESC-AG-2026-0007", LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15),
                "Sani Tours", "Sandra", "sandra@sani.rs",
                "Marketinške usluge za period 01.09.2026. – 15.09.2026.", new BigDecimal("1234.50"),
                "Escapii d.o.o.", "Beograd, Srbija", pib, mb, account, bank, "info@escapii.rs", "escapii.rs");
    }

    private static String tekst(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertEquals(1, doc.getNumberOfPages(), "faktura mora stati na jednu stranu");
            return new PDFTextStripper().getText(doc);
        }
    }

    private static String normalizovano(String t) {
        return t.replaceAll("\\s+", " ").toLowerCase();
    }

    private static void sacuvaj(byte[] pdf, String ime) throws Exception {
        File dir = new File("target/agency-invoice-preview");
        dir.mkdirs();
        Files.write(new File(dir, ime + ".pdf").toPath(), pdf);
        try (PDDocument doc = PDDocument.load(pdf)) {
            ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 110), "png", new File(dir, ime + ".png"));
        }
    }

    @Test
    void bezPodatakaFirme_bezNulaIPlaceholdera_saNapomenom() throws Exception {
        byte[] pdf = svc.generateAgency(data("000000000", "00000000", "000-0000000000000-00", "placeholder banka"));
        sacuvaj(pdf, "bez-firme");
        String t = normalizovano(tekst(pdf));

        assertTrue(t.contains("esc-ag-2026-0007"), t);
        assertTrue(t.contains("marketinške usluge za period 01.09.2026. – 15.09.2026."), t);
        assertTrue(t.contains("1.234,50"), t);
        assertTrue(t.contains("sani tours"), t);
        assertTrue(t.contains("sandra@sani.rs"), t);
        assertTrue(t.contains("datum prometa: 15.09.2026."), t);
        assertTrue(t.contains("rok plaćanja: 24.09.2026."), t);
        assertTrue(t.contains("podaci za uplatu biće naknadno dostavljeni"), t);
        assertFalse(t.contains("000000000"), "PIB nule ne smeju na dokument");
        assertFalse(t.contains("placeholder"), t);
        assertFalse(t.contains("pib"), t);
        assertFalse(t.contains("broj računa"), t);
    }

    @Test
    void saPodacimaFirme_prikazujePibIRacun() throws Exception {
        byte[] pdf = svc.generateAgency(data("112233445", "21234567", "160-0000001234567-89", "Banca Intesa"));
        sacuvaj(pdf, "sa-firmom");
        String t = normalizovano(tekst(pdf));

        assertTrue(t.contains("pib: 112233445"), t);
        assertTrue(t.contains("mb: 21234567"), t);
        assertTrue(t.contains("160-0000001234567-89"), t);
        assertTrue(t.contains("banca intesa"), t);
        assertTrue(t.contains("97 esc-ag-2026-0007"), t);
        assertFalse(t.contains("naknadno dostavljeni"), t);
    }
}
