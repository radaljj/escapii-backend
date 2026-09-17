package com.escapii.controller;

import com.escapii.service.impl.JobHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * /api/health/jobs: 200 kad je sve poslato, 503 kad reveal kasni (to je signal za bot),
 * 503 DOWN kad baza ne odgovara. Odgovor nosi samo brojeve - endpoint je javan.
 */
@WebMvcTest(controllers = HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
class HealthJobsHttpTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JdbcTemplate jdbcTemplate;
    @MockitoBean private JobHealthService jobHealthService;

    @Test
    void sveUReduVraca200() throws Exception {
        when(jobHealthService.proveri(any())).thenReturn(
                new JobHealthService.Stanje(0, 0, 0, 0, 0, null, LocalDate.of(2026, 9, 16), false));

        mockMvc.perform(get("/api/health/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.revealKasni").value(0))
                .andExpect(jsonPath("$.proveravaPolaskeDo").value("2026-09-16"));
    }

    @Test
    void revealKasniVraca503SaRazlozima() throws Exception {
        when(jobHealthService.proveri(any())).thenReturn(
                new JobHealthService.Stanje(3, 0, 1, 1, 1, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16), false));

        mockMvc.perform(get("/api/health/jobs"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("REVEAL_KASNI"))
                .andExpect(jsonPath("$.revealKasni").value(3))
                .andExpect(jsonPath("$.bezDestinacije").value(1))
                .andExpect(jsonPath("$.cekaPrognozu").value(1))
                .andExpect(jsonPath("$.slanjeNijeUspelo").value(1))
                .andExpect(jsonPath("$.najranijiPolazak").value("2026-09-15"));
    }

    @Test
    void dnevniKrugKasniVraca503() throws Exception {
        when(jobHealthService.proveri(any())).thenReturn(
                new JobHealthService.Stanje(0, 0, 0, 0, 0, null, LocalDate.of(2026, 9, 16), true));

        mockMvc.perform(get("/api/health/jobs"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DNEVNI_KRUG_KASNI"))
                .andExpect(jsonPath("$.dnevniKrugKasni").value(true));
    }

    @Test
    void greskaBazeVraca503Down() throws Exception {
        when(jobHealthService.proveri(any())).thenThrow(new RuntimeException("baza"));

        mockMvc.perform(get("/api/health/jobs"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void obicanHealthOstajeNetaknut() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
