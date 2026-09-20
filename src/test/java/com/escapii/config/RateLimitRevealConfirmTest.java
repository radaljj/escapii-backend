package com.escapii.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Javljanje "ogrebano" (POST /api/reveal/confirm) ima svoj brojač. Dok ga je delilo sa otvaranjem
 * strane (GET /api/reveal, 10 na 15 min po IP), par osvežavanja sa dva telefona na istom Wi-Fi-ju
 * je trošilo limit i javljanje je tiho propadalo - a od njega zavisi da li kupcu ode dokument.
 */
class RateLimitRevealConfirmTest {

    private final RateLimitingFilter filter = new RateLimitingFilter();

    private int status(String metod, String putanja, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metod, putanja);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, mock(FilterChain.class));
        return res.getStatus();
    }

    @Test
    void potrosenLimitOtvaranjaStrane_neBlokiraJavljanje() throws Exception {
        for (int i = 0; i < 10; i++) assertEquals(200, status("GET", "/api/reveal", "203.0.113.7"), "otvaranje " + (i + 1));
        assertEquals(429, status("GET", "/api/reveal", "203.0.113.7"), "11. otvaranje je preko limita");

        assertEquals(200, status("POST", "/api/reveal/confirm", "203.0.113.7"), "javljanje ima svoj brojač");
    }

    @Test
    void javljanjeNeTrosiLimitOtvaranjaStrane() throws Exception {
        for (int i = 0; i < 12; i++) assertEquals(200, status("POST", "/api/reveal/confirm", "203.0.113.8"));
        for (int i = 0; i < 10; i++) assertEquals(200, status("GET", "/api/reveal", "203.0.113.8"), "otvaranje " + (i + 1));
    }

    @Test
    void iJavljanjeImaSvojLimit() throws Exception {
        for (int i = 0; i < 30; i++) assertEquals(200, status("POST", "/api/reveal/confirm", "203.0.113.9"), "javljanje " + (i + 1));
        assertEquals(429, status("POST", "/api/reveal/confirm", "203.0.113.9"), "31. javljanje je preko limita");
    }

    @Test
    void limitJePoAdresi() throws Exception {
        for (int i = 0; i < 30; i++) status("POST", "/api/reveal/confirm", "203.0.113.10");
        assertEquals(200, status("POST", "/api/reveal/confirm", "203.0.113.11"));
    }
}
