package com.escapii.controller;

import com.escapii.config.GlobalExceptionHandler;
import com.escapii.promo.ExclusionPromo;
import com.escapii.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Javna provera promo koda i admin podešavanja. Javni odgovor kaže samo da li kod važi i do kad -
 * nikad ne vraća sam kod, i ista je poruka za nepostojeći, istekao i ugašen.
 */
@WebMvcTest(controllers = {PromoController.class, PromoAdminController.class})
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import(GlobalExceptionHandler.class)
class PromoControllerHttpTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExclusionPromo exclusionPromo;
    @MockitoBean private BookingRepository bookingRepository;

    private static final LocalDate DO = LocalDate.of(2026, 11, 30);

    @Test
    void vazeciKod_vracaVrstuIDatum_aNeKod() throws Exception {
        when(exclusionPromo.vazi("skip3")).thenReturn(true);
        when(exclusionPromo.podesavanja()).thenReturn(new ExclusionPromo.Podesavanja("SKIP3", DO, true));

        mockMvc.perform(post("/api/promo/validate").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"skip3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.kind").value("EXCLUSIONS_FREE"))
                .andExpect(jsonPath("$.validUntil").value("2026-11-30"))
                .andExpect(content().string(not(containsString("SKIP3"))));
    }

    @Test
    void nevazeciKod_uniformnaPoruka() throws Exception {
        when(exclusionPromo.vazi("PROBA")).thenReturn(false);

        mockMvc.perform(post("/api/promo/validate").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"PROBA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Promo kod nije važeći."))
                .andExpect(jsonPath("$.validUntil").doesNotExist());
    }

    @Test
    void prazanIliPredugKod_400_iNeStizeDoProvere() throws Exception {
        mockMvc.perform(post("/api/promo/validate").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/promo/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + "X".repeat(41) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(exclusionPromo, never()).vazi(any());
    }

    // ── admin ────────────────────────────────────────────────────────────────

    @Test
    void panelCitaPodesavanjaIStatistiku() throws Exception {
        when(exclusionPromo.podesavanja()).thenReturn(new ExclusionPromo.Podesavanja("SKIP3", DO, true));
        when(exclusionPromo.danas()).thenReturn(LocalDate.of(2026, 10, 1));
        when(bookingRepository.countPromoUses("SKIP3")).thenReturn(7L);
        when(bookingRepository.sumPromoSaved("SKIP3")).thenReturn(340L);

        mockMvc.perform(get("/api/admin/promo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SKIP3"))
                .andExpect(jsonPath("$.validUntil").value("2026-11-30"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.usedCount").value(7))
                .andExpect(jsonPath("$.savedTotalEur").value(340));
    }

    @Test
    void ukljucenAliIstekao_panelKazeDaNijeAktivan() throws Exception {
        when(exclusionPromo.podesavanja()).thenReturn(new ExclusionPromo.Podesavanja("SKIP3", DO, true));
        when(exclusionPromo.danas()).thenReturn(DO.plusDays(1));

        mockMvc.perform(get("/api/admin/promo"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void cuvanjeIzPanela() throws Exception {
        when(exclusionPromo.sacuvaj("SKIP3", DO, true)).thenReturn(new ExclusionPromo.Podesavanja("SKIP3", DO, true));
        when(exclusionPromo.danas()).thenReturn(LocalDate.of(2026, 10, 1));

        mockMvc.perform(put("/api/admin/promo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SKIP3\",\"validUntil\":\"2026-11-30\",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void losUnosIzPanela_400_saPorukomZaAdmina() throws Exception {
        when(exclusionPromo.sacuvaj(any(), any(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Da bi promo bio uključen, mora da ima datum do kog važi."));

        mockMvc.perform(put("/api/admin/promo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SKIP3\",\"validUntil\":null,\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Da bi promo bio uključen, mora da ima datum do kog važi."));
    }
}
