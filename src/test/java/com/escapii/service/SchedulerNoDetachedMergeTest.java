package com.escapii.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nalaz iz revizije: dnevni scheduler je tiho brisao admin izmene.
 *
 * sendForecasts/sendReveals NAMERNO nemaju @Transactional (pad jednog bookinga ne
 * sme poništiti flag onima kojima je mejl već otišao), pa su Booking objekti u tim
 * petljama DETACHED - lista se učita u 10:00, a obrada traje minutima. save() nad
 * detached entitetom je em.merge(), a merge prepisuje SVE kolone vrednostima iz
 * starog snapshot-a: PDF koji je admin u međuvremenu uploadovao, otkazivanje,
 * beleške. Booking nema @Version, pa ništa ne primeti.
 *
 * Popravka su ciljani UPDATE upiti koji diraju samo jednu kolonu. Ovaj test
 * proverava STRUKTURU izvora, ne ponašanje - jer je rizik da neko kasnije vrati
 * "jednostavniji" save(booking), a to nijedan test ponašanja sa mock repozitorijumom
 * ne bi uhvatio (merge se dešava tek u pravoj JPA sesiji).
 */
class SchedulerNoDetachedMergeTest {

    private static final Path SCHEDULER =
            Path.of("src/main/java/com/escapii/service/impl/BookingSchedulingServiceImpl.java");
    private static final Path AUTO_SENDER =
            Path.of("src/main/java/com/escapii/service/impl/ConfirmationDocumentAutoSender.java");
    private static final Path REPO =
            Path.of("src/main/java/com/escapii/repository/BookingRepository.java");

    /**
     * Skida komentare pre provere. Komentari u tim fajlovima namerno objašnjavaju
     * ZAŠTO nečega nema (npr. "namerno bez setConfirmationSentAt(...)") - i baš taj
     * tekst bi lažno oborio provere ispod. Proverava se kod, ne objašnjenje.
     */
    private static String bezKomentara(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "")
                  .replaceAll("(?m)//.*$", "");
    }

    /** Iseca telo metode: od potpisa do sledećeg člana klase na istoj dubini uvlačenja. */
    private static String metoda(String src, String potpis) {
        int start = src.indexOf(potpis);
        assertTrue(start > 0, "nema metode: " + potpis);
        int kraj = src.length();
        for (String granica : new String[] {"\n    @Override", "\n    public ", "\n    private ", "\n    protected "}) {
            int i = src.indexOf(granica, start + potpis.length());
            if (i > 0 && i < kraj) kraj = i;
        }
        return src.substring(start, kraj);
    }

    @Test
    void batchPetljeNeZovuSaveNadDetachedBookingom() throws IOException {
        String src = Files.readString(SCHEDULER);

        for (String potpis : new String[] {"List<Booking> sendForecasts(", "List<Booking> sendReveals("}) {
            String telo = metoda(src, potpis);
            assertFalse(telo.contains("bookingRepository.save(booking)"),
                    potpis + " ne sme zvati save(booking): booking je detached, save bi bio merge "
                    + "i pregazio bi sve kolone starim vrednostima");
            assertFalse(telo.contains("saveAndFlush(booking)"),
                    potpis + " ne sme zvati saveAndFlush(booking) - isti razlog");
        }

        assertTrue(metoda(src, "List<Booking> sendForecasts(").contains("markForecastSent("),
                "prognoza mora upisivati flag ciljanim upitom");
        String reveal = metoda(src, "List<Booking> sendReveals(");
        assertTrue(reveal.contains("markRevealSent("),
                "reveal mora upisivati flag ciljanim upitom");
        assertTrue(reveal.contains("saveRevealTokenIfAbsent("),
                "token mora ići ciljanim upitom, ne kroz saveAndFlush celog entiteta");
        assertTrue(reveal.contains("findRevealTokenById("),
                "posle uslovnog upisa token se mora pročitati iz baze - ako ga je neko drugi "
                + "upisao u međuvremenu, mejl mora nositi TAJ token a ne naš");
    }

    /** Ručne metode iz panela SU transakcione i legitimno rade save() - test ih ne sme lažno oboriti. */
    @Test
    void rucneTransakcioneMetodeOstajuNetaknute() throws IOException {
        String src = Files.readString(SCHEDULER);
        String rucniReveal = metoda(src, "public Map<String, String> sendRevealForBooking(");
        assertTrue(src.substring(0, src.indexOf("public Map<String, String> sendRevealForBooking("))
                        .trim().endsWith("@Transactional"),
                "ručni reveal mora ostati @Transactional - tamo je save() ispravan");
        assertTrue(rucniReveal.contains("bookingRepository.save(booking)"));
    }

    @Test
    void autoSenderNeZoveSaveNadProsledjenimBookingom() throws IOException {
        String src = bezKomentara(Files.readString(AUTO_SENDER));
        assertFalse(src.contains("bookingRepository.save("),
                "booking ovamo stiže detached iz scheduler petlje - save bi bio merge");
        assertTrue(src.contains("markConfirmationSent("));
        // Nalaz protivprovere: sendAllPending je bio @Transactional, pa su rezervacije u
        // njemu bile MANAGED, a setConfirmationSentAt na managed entitetu tera Hibernate
        // da na commit-u upise CEO red iz snapshot-a starog koliko i cela SMTP petlja.
        // Ni transakcija ni setter ne smeju nazad - ciljani upit ima svoju transakciju.
        assertFalse(src.contains("@Transactional"),
                "auto-sender ne sme drzati transakciju preko SMTP slanja - entiteti bi bili managed");
        assertFalse(src.contains("setConfirmationSentAt("),
                "setter na entitetu bi ga zaprljao; flag ide iskljucivo ciljanim upitom");
    }

    /** Ciljani upiti moraju biti idempotentni: uslov IS NULL sprečava pomeranje već upisanog vremena. */
    @Test
    void ciljaniUpisiSuIdempotentni() throws IOException {
        String src = Files.readString(REPO);
        for (String kolona : new String[] {"forecastSentAt", "revealSentAt", "confirmationSentAt", "revealToken"}) {
            assertTrue(src.contains("b." + kolona + " IS NULL"),
                    "UPDATE za " + kolona + " mora imati uslov IS NULL");
        }
        assertTrue(src.contains("int markForecastSent(") && src.contains("int markRevealSent(")
                && src.contains("int markConfirmationSent(") && src.contains("int saveRevealTokenIfAbsent("));
    }
}
