package com.escapii.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Neispravan query parametar je greška pozivaoca - mora biti 400.
 * Ranije je padao na Exception fallback koji vraća 500 i šalje email alert,
 * pa je svaki bot bez parametra pravio lažnu uzbunu.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void nedostajuciParametarVraca400() {
        var req = new MockHttpServletRequest("GET", "/api/gifts/vouchers/reveal");
        var ex  = new MissingServletRequestParameterException("code", "String");

        var res = handler.handleBadRequestParam(ex, req);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(String.valueOf(res.getBody().get("error")).contains("code"),
                "poruka treba da imenuje parametar koji nedostaje");
    }

    @Test
    void parametarPogresnogTipaVraca400() {
        var req = new MockHttpServletRequest("GET", "/api/bookings");
        var ex  = new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null);

        var res = handler.handleBadRequestParam(ex, req);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(String.valueOf(res.getBody().get("error")).contains("id"));
    }

    /** Prava greška servera i dalje mora biti 500 - inače bi popravka sakrila kvarove. */
    @Test
    void nepredvidjenaGreskaOstaje500() {
        var req = new MockHttpServletRequest("GET", "/api/bookings");
        var res = handler.handleUnexpected(new IllegalStateException("pukao servis"), req);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
    }
}
