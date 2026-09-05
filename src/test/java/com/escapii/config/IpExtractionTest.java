package com.escapii.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Zaključava redosled izvora klijentskog IP-a.
 *
 * Rizik nije da ovo danas ne radi, nego da neko kasnije "pojednostavi" metodu nazad na
 * samo X-Forwarded-For. To zaglavlje napadač može napuniti unapred, pa bi svaka zaštita
 * merena po IP-u (rate limit rezervacija, brojač pogrešnih admin ključeva, limit vaučera)
 * postala zaobilazna promenom jednog broja u zahtevu. Zato test proverava PRIMAT
 * CF-Connecting-IP zaglavlja, ne samo da ekstrakcija vraća nešto.
 */
class IpExtractionTest {

    @Test
    void cloudflareZaglavljeImaPrimatNadForwardedFor() {
        var req = new MockHttpServletRequest("GET", "/api/booking");
        // Napadač je unapred napunio X-Forwarded-For; Cloudflare je pravu adresu
        // stavio u svoje zaglavlje i prepisao ono što je klijent poslao.
        req.addHeader("X-Forwarded-For", "9.9.9.9, 8.8.8.8");
        req.addHeader("CF-Connecting-IP", "203.0.113.7");

        assertEquals("203.0.113.7", IpUtils.extractClientIp(req),
                "kroz Cloudflare mora da pobedi CF-Connecting-IP, inače je rate limit zaobilazan");
    }

    @Test
    void bezCloudflareZaglavljaUzimaPoslednjiForwardedForUnos() {
        var req = new MockHttpServletRequest("GET", "/api/booking");
        req.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.7");

        assertEquals("203.0.113.7", IpUtils.extractClientIp(req),
                "proxy dopisuje pravu adresu na kraj - prvi unos je onaj koji je klijent poslao");
    }

    @Test
    void neispravnoCloudflareZaglavljePadaNaForwardedFor() {
        var req = new MockHttpServletRequest("GET", "/api/booking");
        // Nije IP nego pokušaj log injection-a - mora biti odbijen, ne prosleđen dalje.
        req.addHeader("CF-Connecting-IP", "nije-ip\nINFO fake log linija");
        req.addHeader("X-Forwarded-For", "203.0.113.7");

        assertEquals("203.0.113.7", IpUtils.extractClientIp(req));
    }

    @Test
    void bezIkakvihZaglavljaKoristiDirektnogPeera() {
        var req = new MockHttpServletRequest("GET", "/api/booking");
        req.setRemoteAddr("10.0.0.5");

        assertEquals("10.0.0.5", IpUtils.extractClientIp(req));
    }

    @Test
    void praznoCloudflareZaglavljeNePreskaceOstaleIzvore() {
        var req = new MockHttpServletRequest("GET", "/api/booking");
        req.addHeader("CF-Connecting-IP", "   ");
        req.setRemoteAddr("10.0.0.5");

        assertEquals("10.0.0.5", IpUtils.extractClientIp(req));
    }
}
