package com.escapii.service.email;

import com.escapii.model.*;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.email.impl.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Provera da svih 12 mejlova prolazi kroz MJML shell bez zaostalih tokena. */
class ShellSmokeTest {

    private static void set(Object target, String field, Object val) throws Exception {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, val);
    }

    private EmailSender capturing(AtomicReference<String> cap) {
        return new EmailSender(null) {
            @Override public boolean send(String to, String s, String html) { cap.set(html); return true; }
            @Override public boolean sendWithAttachment(String to, String s, String html,
                                                        String n, byte[] b, String ct) { cap.set(html); return true; }
        };
    }

    private Booking booking() {
        Booking b = new Booking();
        b.setId(1L); b.setBookingRef("ESC-a1b2c3d4");
        b.setFirstName("Marko"); b.setLastName("Marković");
        b.setEmail("marko@example.com"); b.setPhone("+381601234567");
        b.setDepartureAirport("BEG"); b.setNumberOfTravelers(2);
        b.setTotalPriceAll(638); b.setLeadPassengerGender("M");
        b.setAssignedDestination("Barselona"); b.setAirlineName("Wizz Air");
        b.setConfirmationDocumentFilename("rezervacija.pdf");
        b.setConfirmationDocument(new byte[]{1,2,3});
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(LocalDate.now().plusDays(20));
        d.setReturnDate(LocalDate.now().plusDays(23));
        d.setNumberOfNights(3);
        b.setSelectedDate(d);
        return b;
    }

    private GiftVoucher voucher() {
        GiftVoucher v = new GiftVoucher();
        v.setId(7L); v.setCode("ESC-A3KM-P2HT-X9QR");
        v.setAmount(BigDecimal.valueOf(200)); v.setUsedAmount(BigDecimal.ZERO);
        v.setBuyerEmail("kupac@example.com"); v.setBuyerName("Ana Anić");
        v.setGiftMessage("Srećan rođendan!");
        v.setExpiresAt(LocalDateTime.now().plusYears(1));
        return v;
    }

    private void check(String name, String html) throws Exception {
        assertNotNull(html, name + ": html je null");
        assertFalse(html.contains("{{"), name + ": ostao nezamenjen token!");
        assertTrue(html.contains("<!doctype html") || html.contains("<!DOCTYPE html"),
                name + ": nije kompletan HTML dokument");
        assertTrue(html.contains("escapii"), name + ": nedostaje brend/logo");
        Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "mail-" + name + ".html"), html);
    }

    @Test
    void sviMejloviProlazeKrozShell() throws Exception {
        AtomicReference<String> cap = new AtomicReference<>();
        EmailSender sender = capturing(cap);

        // 1-2. Fakture (rezervacija + vaučer)
        var inv = new InvoiceEmailServiceImpl(sender);
        set(inv, "teamEmail", "escapii.team@gmail.com");
        inv.sendInvoiceToClient(booking(), new byte[]{1}, "ESC-INV-2026-0001");
        check("faktura-rezervacija", cap.get());
        inv.sendVoucherInvoiceToClient(voucher(), new byte[]{1}, "ESC-INV-2026-0002");
        check("faktura-vaucer", cap.get());

        // 3. Reveal
        var rev = new RevealEmailServiceImpl(sender);
        set(rev, "frontendUrl", "https://escapii.rs");
        Booking b = booking(); b.setRevealToken("abc123");
        rev.sendRevealEmail(b);
        check("reveal", cap.get());

        // 4. Dokument rezervacije
        var cd = new ConfirmationDocumentEmailServiceImpl(sender);
        set(cd, "teamEmail", "escapii.team@gmail.com");
        cd.sendConfirmationDocument(booking());
        check("dokument-rezervacije", cap.get());

        // 5-6. Waitlist
        var wl = new WaitlistEmailServiceImpl(sender);
        wl.sendWaitlistConfirmation("test@example.com", "BEG");
        check("waitlist-potvrda", cap.get());
        wl.sendWaitlistNotification("test@example.com", "BEG");
        check("waitlist-novi-termini", cap.get());

        // 8-9. Vaučer (tim + PDF kupcu)
        var gv = new GiftVoucherEmailServiceImpl(sender);
        set(gv, "teamEmail", "escapii.team@gmail.com");
        gv.sendTeamAlert(voucher());
        check("vaucer-tim", cap.get());
        gv.sendVoucherPdfToBuyer(voucher(), new byte[]{1});
        check("vaucer-pdf", cap.get());

        // 10-11. Upit za prilagođeni termin (tim + potvrda kupcu)
        var inq = new InquiryEmailServiceImpl(sender);
        set(inq, "teamEmail", "escapii.team@gmail.com");
        CustomDateInquiry ci = new CustomDateInquiry();
        ci.setId(3L); ci.setEmail("upit@example.com"); ci.setAirport("BEG");
        ci.setTravelers(2); ci.setDesiredDepartureDate(LocalDate.now().plusDays(60));
        ci.setNights(3); ci.setStatus(InquiryStatus.PENDING);
        ci.setCreatedAt(LocalDateTime.now());
        inq.sendTeamAlert(ci);
        check("upit-tim", cap.get());

        // 7. Prognoza
        var fc = new ForecastEmailServiceImpl(sender);
        var days = List.of(
            new com.escapii.service.weather.DailyForecast(LocalDate.now().plusDays(20), 0, 28, 18, 0.0),
            new com.escapii.service.weather.DailyForecast(LocalDate.now().plusDays(21), 61, 24, 16, 3.2),
            new com.escapii.service.weather.DailyForecast(LocalDate.now().plusDays(22), 1, 26, 17, 0.0),
            new com.escapii.service.weather.DailyForecast(LocalDate.now().plusDays(23), 3, 25, 17, 0.4));
        fc.sendForecastEmail(booking(), days);
        check("prognoza", cap.get());

        System.out.println("PREVIEW_DIR=" + System.getProperty("java.io.tmpdir"));
    }

    /** Oba shell-a (sa i bez mystery trake) se učitavaju i popunjavaju. */
    @Test
    void obaShellaRade() {
        for (boolean mystery : new boolean[]{false, true}) {
            String html = EmailHtmlBuilder.wrapBase(
                "#a85e44", "", EmailHtmlBuilder.statusBadge("Test", "orange"),
                "Naslov", "Podnaslov", "ESC-REF-1",
                "<p>Telo mejla</p>", "Futer tekst", mystery);
            assertFalse(html.contains("{{"), "mystery=" + mystery + ": zaostao token");
            assertTrue(html.contains("Telo mejla"));
            assertTrue(html.contains("Naslov"));
            assertTrue(html.contains("Futer tekst"));
            assertTrue(html.contains("ESC-REF-1"));
            assertEquals(mystery, html.contains("ostaje tajna"),
                    "mystery traka se ne poklapa sa zastavicom");
        }
    }
}
