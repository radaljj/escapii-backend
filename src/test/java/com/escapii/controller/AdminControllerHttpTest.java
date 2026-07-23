package com.escapii.controller;

import com.escapii.config.DailyTaskScheduler;
import com.escapii.config.GlobalExceptionHandler;
import com.escapii.dto.AdminBookingResponse;
import com.escapii.model.BookingStatus;
import com.escapii.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Admin endpointi koji pokreću mejlove rezervacije: promena statusa, ručno
 * slanje profakture, ručno ponovno slanje dokumenta. Test proverava da ruta
 * postoji, da prosleđuje servisu, i - važno - da poslovna greška iz servisa
 * (409/502) stigne do klijenta kao takva, a ne kao 500.
 *
 * addFilters=false: AdminKeyFilter se testira zasebno u AdminKeyFilterTest.
 * Ovde je fokus na rutiranju i propagaciji grešaka, ne na autentikaciji.
 */
@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import(GlobalExceptionHandler.class)
class AdminControllerHttpTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean   private AdminService adminService;
    @MockitoBean   private DailyTaskScheduler dailyTaskScheduler;

    @Test
    void promenaStatusaProsledjujeServisu() throws Exception {
        when(adminService.updateBookingStatus(1L, BookingStatus.CONFIRMED))
                .thenReturn(AdminBookingResponse.builder().bookingRef("ESC-abcd1234").build());

        mockMvc.perform(patch("/api/admin/bookings/1/status").param("value", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingRef").value("ESC-abcd1234"));

        verify(adminService).updateBookingStatus(1L, BookingStatus.CONFIRMED);
    }

    @Test
    void nepoznatStatusVraca400() throws Exception {
        // "NEPOSTOJI" nije validan BookingStatus - konverzija pada, GlobalExceptionHandler daje 400
        mockMvc.perform(patch("/api/admin/bookings/1/status").param("value", "NEPOSTOJI"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminService);
    }

    @Test
    void sendInvoiceProsledjujeServisu() throws Exception {
        when(adminService.sendInvoice(1L))
                .thenReturn(AdminBookingResponse.builder().bookingRef("ESC-abcd1234").build());

        mockMvc.perform(post("/api/admin/bookings/1/send-invoice"))
                .andExpect(status().isOk());

        verify(adminService).sendInvoice(1L);
    }

    /**
     * Ako slanje profakture pukne, servis baca 502 - klijent MORA da vidi 502,
     * ne 500 ni 200. Inače admin ne bi znao da nešto treba ponoviti.
     */
    @Test
    void neuspesnoSlanjeProfaktureVraca502() throws Exception {
        when(adminService.sendInvoice(1L))
                .thenThrow(new ResponseStatusException(BAD_GATEWAY, "Slanje nije uspelo"));

        mockMvc.perform(post("/api/admin/bookings/1/send-invoice"))
                .andExpect(status().isBadGateway());
    }

    /**
     * Ručno slanje dokumenta pre otvorenog reveala → servis baca 409 →
     * klijent vidi 409. Ovo je brana koja čuva tajnost destinacije.
     */
    @Test
    void dokumentPreRevealaVraca409() throws Exception {
        when(adminService.resendConfirmationDocument(1L))
                .thenThrow(new ResponseStatusException(CONFLICT, "Kupac još nije otvorio reveal"));

        mockMvc.perform(post("/api/admin/bookings/1/confirmation-document/resend"))
                .andExpect(status().isConflict());
    }

    @Test
    void rucenoSlanjeRevealaProsledjujeScheduler() throws Exception {
        when(dailyTaskScheduler.sendRevealForBooking(anyLong(), nullable(String.class)))
                .thenReturn(java.util.Map.of("message", "Reveal email poslan"));

        mockMvc.perform(post("/api/admin/bookings/1/send-reveal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reveal email poslan"));

        verify(dailyTaskScheduler).sendRevealForBooking(eq(1L), nullable(String.class));
    }
}
