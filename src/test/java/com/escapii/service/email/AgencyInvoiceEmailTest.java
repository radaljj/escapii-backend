package com.escapii.service.email;

import com.escapii.model.AgencyInvoice;
import com.escapii.model.AgencyInvoiceStatus;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.email.impl.InvoiceEmailServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mejl sa zbirnom fakturom agenciji: broj, stavka, period i iznos - bez roka plaćanja
 * (Markova odluka 2026-09-17: rok ostaje samo na PDF-u), PDF u prilogu sa imenom po broju.
 */
class AgencyInvoiceEmailTest {

    private static void set(Object target, String field, Object val) throws Exception {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, val);
    }

    record Poslato(String to, String subject, String html, String fileName, byte[] bytes, String contentType) {}

    private static EmailSender capturing(AtomicReference<Poslato> cap) {
        return new EmailSender(null) {
            @Override public boolean send(String to, String s, String html) { fail("faktura mora ići sa prilogom"); return false; }
            @Override public boolean sendWithAttachment(String to, String s, String html, String n, byte[] b, String ct) {
                cap.set(new Poslato(to, s, html, n, b, ct)); return true;
            }
        };
    }

    private static AgencyInvoice faktura() {
        AgencyInvoice inv = new AgencyInvoice();
        inv.setInvoiceNumber("ESC-AG-2026-0007");
        inv.setAgencyName("Sani Tours");
        inv.setAgencyEmail("sandra@sani.rs");
        inv.setDescription("Marketinške usluge za period 01.09.2026. – 15.09.2026.");
        inv.setPeriodFrom(LocalDate.of(2026, 9, 1));
        inv.setPeriodTo(LocalDate.of(2026, 9, 15));
        inv.setAmount(new BigDecimal("1234.50"));
        inv.setIssuedAt(LocalDate.of(2026, 9, 16));
        inv.setDueDate(LocalDate.of(2026, 9, 24));
        inv.setStatus(AgencyInvoiceStatus.SENT);
        return inv;
    }

    @Test
    void mejlAgenciji_brojStavkaPeriodIznos_bezRokaPlacanja_saPdfPrilogom() throws Exception {
        AtomicReference<Poslato> cap = new AtomicReference<>();
        var svc = new InvoiceEmailServiceImpl(capturing(cap), ime -> ime);
        set(svc, "contactEmail", "info@escapii.rs");

        assertTrue(svc.sendAgencyInvoice(faktura(), new byte[]{'%', 'P', 'D', 'F'}));

        Poslato p = cap.get();
        assertNotNull(p, "mejl nije poslat");
        assertEquals("sandra@sani.rs", p.to());
        assertEquals("Faktura ESC-AG-2026-0007 · Escapii", p.subject());
        assertEquals("escapii-faktura-ESC-AG-2026-0007.pdf", p.fileName());
        assertEquals("application/pdf", p.contentType());
        assertArrayEquals(new byte[]{'%', 'P', 'D', 'F'}, p.bytes());

        String html = p.html();
        assertTrue(html.contains("ESC-AG-2026-0007"), "broj fakture");
        assertTrue(html.contains("Marketinške usluge za period 01.09.2026. – 15.09.2026."), "stavka");
        assertTrue(html.contains("01.09.2026. – 15.09.2026."), "period");
        assertTrue(html.contains("1.234,50 EUR"), "iznos u srpskom formatu");
        assertTrue(html.contains("info@escapii.rs"), "kontakt");
        assertFalse(html.contains("Rok pla"), "rok plaćanja se u mejlu ne pominje");
        assertFalse(html.contains("24.09.2026."), "datum roka se u mejlu ne pominje");
        assertFalse(html.contains("{{"), "ostao nezamenjen token");
    }
}
