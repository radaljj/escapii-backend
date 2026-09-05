package com.escapii.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Zaključava popravku stvarne rupe nađene pred lansiranje.
 *
 * GET /api/%61dmin/bookings ("%61" je slovo "a") je na produkciji vraćao HTTP 200
 * sa svim rezervacijama, bez ikakvog admin ključa. Uzrok: getRequestURI() vraća
 * NEDEKODIRANU putanju pa shouldNotFilter preskoči filter, ali DispatcherServlet
 * rutira po DEKODIRANOJ i svejedno pogodi AdminController.
 *
 * Isti obrazac je obarao i sve rate limite (npr. /api/l%61unch-notify).
 *
 * Test gađa RequestPaths jer je to jedino mesto gde se odluka donosi - ako neko
 * kasnije vrati golo poređenje getRequestURI(), padne ovde.
 */
class AdminPathBypassTest {

    private MockHttpServletRequest zahtev(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        r.setRequestURI(uri);
        return r;
    }

    @Test
    void procentnoKodiranaAdminPutanjaSePrepoznaje() {
        // Ovo je tačan zahtev koji je curio podatke na produkciji.
        assertTrue(RequestPaths.startsWith(zahtev("/api/%61dmin/bookings"), "/api/admin/"),
                "kodirano 'a' mora da se prepozna kao admin putanja, inače filter ne radi");
    }

    @Test
    void obicnaAdminPutanjaSeIDaljePrepoznaje() {
        assertTrue(RequestPaths.startsWith(zahtev("/api/admin/bookings"), "/api/admin/"));
    }

    @Test
    void javnaPutanjaNijeAdmin() {
        assertFalse(RequestPaths.startsWith(zahtev("/api/booking"), "/api/admin/"));
        assertFalse(RequestPaths.startsWith(zahtev("/api/dates"), "/api/admin/"));
    }

    @Test
    void kodiranaPutanjaZaRateLimitSePrepoznaje() {
        // /api/l%61unch-notify je zaobilazio limit i trošio Resend kvotu.
        assertEquals("/api/launch-notify", RequestPaths.decodedPath(zahtev("/api/l%61unch-notify")));
        assertTrue(RequestPaths.decodedPath(zahtev("/api/b%6Foking")).endsWith("/api/booking"));
    }

    /**
     * Dvostruko kodiranje se namerno NE razrešava do "admin" - ne razrešava ga ni
     * ruter, pa takav zahtev završi kao 404 umesto da pogodi kontroler. Bitno je
     * samo da se filter i ruter slažu.
     */
    @Test
    void dvostrukoKodiranjeNePogadjaKontroler() {
        assertEquals("/api/%61dmin/bookings", RequestPaths.decodedPath(zahtev("/api/%2561dmin/bookings")));
    }

    @Test
    void neispravnaProcentnaSekvencaNeRusiZahtev() {
        assertDoesNotThrow(() -> RequestPaths.decodedPath(zahtev("/api/%zz/bookings")));
    }
}
