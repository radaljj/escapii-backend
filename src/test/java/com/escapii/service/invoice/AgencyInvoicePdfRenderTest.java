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
        return data(pib, mb, account, bank, false);
    }

    private static AgencyInvoiceData data(String pib, String mb, String account, String bank, boolean isPreview) {
        return new AgencyInvoiceData("ESC-AG-2026-0007", LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15),
                "Sani Tours", "Sandra", "sandra@sani.rs",
                "Sani Tours d.o.o. Beograd", "Bulevar kralja Aleksandra 73, 11000 Beograd", "100123456", "20123456",
                "Marketinške usluge za period 01.09.2026. – 15.09.2026.", new BigDecimal("1234.50"),
                "Marija Radalj PR agencija za marketing Escapii Technologies Beograd", "Lješka 2, sprat 4, stan 23, 11030 Beograd (Čukarica)",
                pib, mb, account, bank, "info@escapii.rs", "escapii.rs", "Beograd", "Marija Radalj", isPreview);
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
        // Etikete su uppercase sa letter-spacing pa ih PDFTextStripper vadi kao "d a t u m" -
        // proveravaju se bez razmaka; vrednosti su u zasebnim celijama pa se proveravaju odvojeno.
        String z = t.replace(" ", "");
        assertTrue(z.contains("datumprometa") && t.contains("15.09.2026."), t);
        assertTrue(z.contains("rokplaćanja") && t.contains("24.09.2026."), t);
        assertTrue(z.contains("datumizdavanja") && t.contains("16.09.2026."), t);
        assertTrue(z.contains("mestoizdavanja") && t.contains("beograd"), t);
        assertTrue(z.contains("ukupnozauplatu"), t);
        assertTrue(z.contains("brojfakture"), t);
        assertTrue(t.contains("obveznik nije u sistemu pdv-a"), t);
        assertTrue(t.contains("dokument je validan bez pečata i potpisa"), t);
        assertTrue(t.contains("odgovorno lice: marija radalj"), t);
        assertTrue(t.contains("podaci za uplatu biće naknadno dostavljeni"), t);
        assertTrue(t.contains("dokument br. esc-ag-2026-0007"), t);
        assertFalse(t.contains("000000000"), "PIB nule ne smeju na dokument");
        assertFalse(t.contains("placeholder"), t);
        // PIB se pojavljuje samo jednom - kod kupca (agencije); PIB firme (nule) ni u zaglavlju ni u podnožju
        assertEquals(1, z.split("pib", -1).length - 1, "PIB samo kod kupca: " + t);
        assertFalse(z.contains("brojračuna"), t);
        assertFalse(z.contains("pregled"), "bez isPreview nema etikete pregleda");
    }

    @Test
    void saPodacimaFirme_prikazujePibIRacun() throws Exception {
        byte[] pdf = svc.generateAgency(data("112233445", "21234567", "160-0000001234567-89", "Banca Intesa"));
        sacuvaj(pdf, "sa-firmom");
        String t = normalizovano(tekst(pdf));

        String z = t.replace(" ", "");
        assertTrue(z.contains("pib") && t.contains("112233445"), t);
        assertTrue(z.contains("mb") && t.contains("21234567"), t);
        assertTrue(z.contains("brojračuna") && z.contains("pozivnabroj"), t);
        assertTrue(t.contains("160-0000001234567-89"), t);
        assertTrue(z.contains("banka") && t.contains("banca intesa"), t);
        assertTrue(t.contains("faktura esc-ag-2026-0007"), t);
        // poziv na broj je sam broj fakture - model 97 traži numerički poziv, pa "97 " ne ide uz alfanumerički
        assertFalse(t.contains("97 esc-ag-2026-0007"), t);
        assertFalse(t.contains("naknadno dostavljeni"), t);
        assertFalse(z.contains("pregled"), t);
    }

    @Test
    void saRacunomBezBanke_nemaRedaBanka() throws Exception {
        byte[] pdf = svc.generateAgency(data("112233445", "21234567", "160-0000001234567-89", "placeholder banka"));
        sacuvaj(pdf, "sa-racunom-bez-banke");
        String t = normalizovano(tekst(pdf));

        String z = t.replace(" ", "");
        assertTrue(t.contains("160-0000001234567-89"), t);
        assertTrue(z.contains("svrhauplate") && z.contains("pozivnabroj"), t);
        assertFalse(z.contains("banka"), "bez banke nema reda Banka ni crtice: " + t);
        assertFalse(t.contains("placeholder"), t);
    }

    @Test
    void pregled_imaEtiketuINemaDokumentBr() throws Exception {
        byte[] pdf = svc.generateAgency(data("112233445", "21234567", "160-0000001234567-89", "Banca Intesa", true));
        sacuvaj(pdf, "pregled");
        String t = normalizovano(tekst(pdf));

        String z = t.replace(" ", "");
        assertTrue(z.contains("pregled"), t);
        assertTrue(z.contains("nijeizdato"), t);
        assertTrue(t.contains("dokument nije izdat"), t);
        assertFalse(t.contains("dokument br."), t);
        assertTrue(t.contains("esc-ag-2026-0007"), t);
    }
}
