package com.escapii.service.invoice;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Probni PDF zbirne fakture (dugme „Preuzmi pregled" u panelu): isti šablon kao prava faktura,
 * ali sa brojem „PREGLED" i isPreview=true - tekst mora da sadrži „PREGLED" da se ne pomeša
 * sa izdatom fakturom. Snima PDF u target/agency-invoice-preview/pregled.pdf.
 */
class AgencyInvoicePreviewPdfRenderTest {

    private final InvoicePdfService svc = new InvoicePdfService();

    @Test
    void probniPdf_sadrziPREGLED_iIsteIznose() throws Exception {
        AgencyInvoiceData d = new AgencyInvoiceData("PREGLED", LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 11),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15),
                "Sani Tours", "Sandra", "sandra@sani.rs",
                "Sani Tours d.o.o. Beograd", "Bulevar kralja Aleksandra 73, 11000 Beograd", "100123456", "20123456",
                "Marketinške usluge za period 01.09.2026. – 15.09.2026.", new BigDecimal("1234.50"),
                "Marija Radalj PR agencija za marketing Escapii Technologies Beograd", "Lješka 2, sprat 4, stan 23, 11030 Beograd (Čukarica)",
                "000000000", "00000000", "000-0000000000000-00", "placeholder banka",
                "info@escapii.rs", "escapii.rs", "Beograd", "Marija Radalj", true);

        byte[] pdf = svc.generateAgency(d);
        File dir = new File("target/agency-invoice-preview");
        dir.mkdirs();
        Files.write(new File(dir, "pregled-probni.pdf").toPath(), pdf);

        String t;
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertEquals(1, doc.getNumberOfPages(), "probna faktura mora stati na jednu stranu");
            t = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ").toLowerCase();
        }
        assertTrue(t.contains("pregled"), t);
        assertTrue(t.contains("marketinške usluge za period 01.09.2026. – 15.09.2026."), t);
        assertTrue(t.contains("1.234,50"), t);
        assertTrue(t.contains("sani tours"), t);
        assertFalse(t.contains("esc-ag-"), "probni PDF ne sme da nosi pravi broj fakture");
    }
}
