package com.escapii.service.invoice;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import javax.imageio.ImageIO;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Alat za dizajn, ne test ponašanja: renderuje kandidat šablona {@code agency-invoice.html}
 * iz foldera {@code KANDIDAT_DIR} (van classpath-a) u PDF i PNG, sa istim fontovima, logom i
 * podacima kao produkcija. Tri varijante: bez računa (napomena o uplati), sa računom i
 * pravnim podacima agencije, i dugačak opis (guard za jednu stranu). Bez env promenljive
 * se preskače, pa u CI ne radi ništa.
 *
 * Pokretanje: KANDIDAT_DIR=D:/putanja/do/foldera ./mvnw -q test -Dtest=InvoiceCandidateRenderTest
 * Rezultat: KANDIDAT_DIR/out/{bez-racuna,sa-racunom,dug-opis}.{pdf,png}
 */
class InvoiceCandidateRenderTest {

    private static final String FIRMA   = "Marija Radalj PR agencija za marketing Escapii Technologies Beograd";
    private static final String ADRESA  = "Lješka 2, sprat 4, stan 23, 11030 Beograd (Čukarica)";

    @Test
    void renderujKandidata() throws Exception {
        String dir = System.getenv("KANDIDAT_DIR");
        Assumptions.assumeTrue(dir != null && !dir.isBlank(), "KANDIDAT_DIR nije postavljen - alat za dizajn, preskačem");
        String prefix = dir.replace('\\', '/');
        if (!prefix.endsWith("/")) prefix += "/";
        assertTrue(new File(prefix + "agency-invoice.html").isFile(), "nema " + prefix + "agency-invoice.html");

        FileTemplateResolver r = new FileTemplateResolver();
        r.setPrefix(prefix);
        r.setSuffix(".html");
        r.setTemplateMode(TemplateMode.HTML);
        r.setCharacterEncoding("UTF-8");
        r.setCacheable(false);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(r);
        InvoicePdfService svc = new InvoicePdfService(engine);

        File out = new File(prefix + "out");
        out.mkdirs();
        sacuvaj(svc.generateAgency(bezRacuna()),  out, "bez-racuna");
        sacuvaj(svc.generateAgency(saRacunom()),  out, "sa-racunom");
        sacuvaj(svc.generateAgency(dugOpis()),    out, "dug-opis");
    }

    /** Stanje danas: firma registrovana, račun još nije; agencija bez pravnih podataka. */
    static AgencyInvoiceData bezRacuna() {
        return new AgencyInvoiceData("ESC-AG-2026-0005", LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 11),
                LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 14),
                "tst", "Marko Radalj", "radalj1234@gmail.com",
                null, null, null, null,
                "Marketinške usluge za period 09.10.2026. – 14.10.2026.", new BigDecimal("709.50"),
                FIRMA, ADRESA, "115994656", "68810809", "000-0000000000000-00", "placeholder banka",
                "info@escapii.rs", "escapii.rs", "Beograd", "Marija Radalj", false);
    }

    /** Ciljno stanje: račun unet, agencija sa punim pravnim podacima. */
    static AgencyInvoiceData saRacunom() {
        return new AgencyInvoiceData("ESC-AG-2026-0012", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 26),
                "Sani Tours", "Sandra Petrović", "sandra@sanitours.rs",
                "Sani Tours d.o.o. Beograd", "Bulevar kralja Aleksandra 73, 11000 Beograd", "100123456", "20123456",
                "Marketinške usluge za period 16.10.2026. – 26.10.2026.", new BigDecimal("2846.00"),
                FIRMA, ADRESA, "115994656", "68810809", "160-0000001234567-89", "Banca Intesa",
                "info@escapii.rs", "escapii.rs", "Beograd", "Marija Radalj", false);
    }

    /** Dugačak opis - guard da sve i dalje stane na jednu stranu. */
    static AgencyInvoiceData dugOpis() {
        return new AgencyInvoiceData("ESC-AG-2026-0013", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 26),
                "Sani Tours", "Sandra Petrović", "sandra@sanitours.rs",
                "Sani Tours d.o.o. Beograd", "Bulevar kralja Aleksandra 73, 11000 Beograd", "100123456", "20123456",
                "Marketinške usluge za period 02.10.2026. – 26.10.2026. (promocija termina, obrada upita i rezervacija "
                + "putnika, komunikacija sa putnicima pre polaska, slanje prognoze i otkrića destinacije, "
                + "partnerske preporuke uz putovanje)", new BigDecimal("11234.50"),
                FIRMA, ADRESA, "115994656", "68810809", "160-0000001234567-89", "Banca Intesa",
                "info@escapii.rs", "escapii.rs", "Beograd", "Marija Radalj", false);
    }

    private static void sacuvaj(byte[] pdf, File dir, String ime) throws Exception {
        Files.write(new File(dir, ime + ".pdf").toPath(), pdf);
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertTrue(doc.getNumberOfPages() == 1, ime + ": faktura mora stati na jednu stranu, ima " + doc.getNumberOfPages());
            ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 110), "png", new File(dir, ime + ".png"));
        }
    }
}
