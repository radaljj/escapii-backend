package com.escapii.service.voucher;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Oba vaučera se STVARNO renderuju (pravi šabloni, pravi fontovi, pravi QR),
 * pa se iz PDF-a čita tekst. Ovo hvata ono što mock ne može: šablon koji ne
 * kompajlira, font koji fali, promenljivu koja nije prosleđena.
 *
 * <p>Uz to snima PDF i PNG pregled u {@code target/voucher-preview/} - jedini
 * način da se dizajn pogleda bez slanja mejla. Direktorijum je pod target/,
 * ne ide u git.
 */
class VoucherPdfRenderTest {

    private static final Path OUT = Path.of("target", "voucher-preview");

    private static void set(Object o, String polje, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(polje);
        f.setAccessible(true);
        f.set(o, v);
    }

    private static VoucherPdfService servis() throws Exception {
        VoucherPdfService s = new VoucherPdfService(new QrCodeGenerator());
        set(s, "redeemBaseUrl", "https://escapii.rs/poklon");
        set(s, "contactEmail", "info@escapii.rs");
        return s;
    }

    private static String tekst(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertEquals(1, doc.getNumberOfPages(), "vaučer mora stati na jedan list");
            return new PDFTextStripper().getText(doc);
        }
    }

    /**
     * Natpisi sa letter-spacing i uppercase iz PDF-a izlaze kao "P O K L O N  O D",
     * pa se poredi bez razmaka i bez obzira na velika slova.
     */
    private static boolean sadrzi(String tekst, String deo) {
        return tekst.replaceAll("\\s+", "").toUpperCase()
                    .contains(deo.replaceAll("\\s+", "").toUpperCase());
    }

    private static void snimi(String ime, byte[] pdf) throws Exception {
        Files.createDirectories(OUT);
        Files.write(OUT.resolve(ime + ".pdf"), pdf);
        try (PDDocument doc = PDDocument.load(pdf)) {
            BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 110);
            ImageIO.write(img, "png", OUT.resolve(ime + ".png").toFile());
        }
    }

    @Test
    void novcaniVaucer_sadrziIznosKodIPoruku() throws Exception {
        byte[] pdf = servis().generate(VoucherData.of(
                100, "ESC-MVV9-KZ27-SP2H", LocalDate.of(2026, 9, 9), "Ana Anić",
                "Srećan rođendan, Marko! Neka ti ovo putovanje bude bar upola lepo koliko si ti meni. Uživaj u svakom trenutku."));

        assertTrue(new String(pdf, 0, 5).startsWith("%PDF-"));
        String t = tekst(pdf);
        for (String ocekivano : new String[]{"ESC-MVV9-KZ27-SP2H", "100", "sto evra", "09.09.2026.", "09.09.2027.",
                                             "Ana Anić", "Srećan rođendan", "Tvoja sledeća", "avantura te čeka",
                                             "info@escapii.rs", "escapii.rs/poklon",
                                             "Izdato", "Važi do", "Vrednost", "Vaučer kod", "Lična poruka"}) {
            assertTrue(sadrzi(t, ocekivano), "u PDF-u nema: " + ocekivano + "\n--- tekst ---\n" + t);
        }
        snimi("poklon-vaucer", pdf);
    }

    @Test
    void putniVaucer_terminPutniciAerodrom_bezCene() throws Exception {
        byte[] pdf = servis().generateTrip(new TripVoucherData(
                "ESC-A3F8B2C1", LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 15), 3, 2,
                "BEG", "Beograd", "Aerodrom Nikola Tesla",
                List.of("Ana Anić", "Marko Marković"), "Marko Marković"));

        assertTrue(new String(pdf, 0, 5).startsWith("%PDF-"));
        String t = tekst(pdf);
        for (String ocekivano : new String[]{"ESC-A3F8B2C1", "BEG", "Beograd", "Aerodrom Nikola Tesla",
                                             "12.06.2026.", "15.06.2026.", "petak, 12. jun 2026.",
                                             "Ana Anić", "Marko Marković", "Tvoja avantura", "je rezervisana",
                                             "Polazak", "Povratak", "Noći", "Putnici", "Poklon od",
                                             "Vaučer kod", "Bez roka", "escapii.rs/poklon"}) {
            assertTrue(sadrzi(t, ocekivano), "u PDF-u nema: " + ocekivano + "\n--- tekst ---\n" + t);
        }
        assertFalse(t.contains("€"), "putni vaučer ne sme da nosi cenu:\n" + t);
        assertFalse(sadrzi(t, "vrednost"), "putni vaučer ne sme da nosi 'vrednost':\n" + t);
        assertFalse(sadrzi(t, "važi do"), "putni vaučer nema rok važenja:\n" + t);
        assertFalse(sadrzi(t, "1000"), "cena rezervacije ne sme da procuri:\n" + t);
        snimi("poklon-putovanje", pdf);
    }

    @Test
    void putniVaucer_bezKupcaIJedanPutnik() throws Exception {
        byte[] pdf = servis().generateTrip(new TripVoucherData(
                "ESC-00000001", LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 2), 2, 1,
                "INI", "Niš", "Aerodrom Konstantin Veliki", List.of("Uroš Đorđević"), null));
        String t = tekst(pdf);
        assertTrue(t.contains("Uroš Đorđević"), "dijakritika mora da se renderuje: " + t);
        assertTrue(t.contains("četvrtak, 31. decembar 2026."), t);
        assertFalse(sadrzi(t, "Poklon od"), "bez kupca nema kolone 'Poklon od'");
    }

    @Test
    void dugDatumNaSrpskom() {
        assertEquals("petak, 12. jun 2026.",        VoucherPdfService.dugDatum(LocalDate.of(2026, 6, 12)));
        assertEquals("ponedeljak, 1. septembar 2025.", VoucherPdfService.dugDatum(LocalDate.of(2025, 9, 1)));
        assertEquals("nedelja, 1. mart 2026.",     VoucherPdfService.dugDatum(LocalDate.of(2026, 3, 1)));
    }

    @Test
    void imenaPutnikaSeEscapeuju() {
        assertEquals("Ana<span>&#183;</span>B &amp; C", VoucherPdfService.passengersHtml(List.of("Ana", " B & C ")));
        assertEquals("", VoucherPdfService.passengersHtml(List.of()));
        assertEquals("", VoucherPdfService.passengersHtml(null));
    }
}
