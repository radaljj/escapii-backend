package com.escapii.controller;

import com.escapii.config.GlobalExceptionHandler;
import com.escapii.dto.BookingResponse;
import com.escapii.dto.BookingStatusResponse;
import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AccommodationType;
import com.escapii.model.BookingStatus;
import com.escapii.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * POST /api/booking je najvažniji javni endpoint u aplikaciji - jedina
 * ulazna tačka kroz koju kupac stvarno kreira rezervaciju (koja dalje
 * pokreće mejlove timu i kupcu, testirano na nivou servisa u
 * BookingCreationFlowTest). Ovaj test proverava da HTTP žica stvarno radi:
 * ruta postoji, validacija na kontroleru odbija loš zahtev PRE nego što
 * stigne do servisa, a ispravan zahtev prosleđuje servisu i vraća 201.
 *
 * @WebMvcTest učitava samo web sloj (bez baze) - BookingService je mockovan.
 * addFilters=false isključuje bezbednosne filtere (CORS/rate-limit/admin-key)
 * jer ovaj endpoint nema admin-key gate i rate limit je već testiran
 * indirektno kroz RateLimitingFilter-ovu sopstvenu logiku; ovde je fokus
 * isključivo na ruti, validaciji i JSON ugovoru.
 */
@WebMvcTest(controllers = BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import(GlobalExceptionHandler.class)
class BookingControllerHttpTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean   private BookingService bookingService;

    /** Kompletan validan zahtev kao raw JSON - izbegava Jackson serijalizaciju
     *  LocalDate (WebMvcTest slice ne izlaže konfigurisan ObjectMapper). */
    private static final String VALID_JSON = """
        {
          "departureAirport": "BEG",
          "numberOfTravelers": 1,
          "selectedDateId": 10,
          "accommodationType": "STANDARD",
          "cabinSuitcaseCount": 0,
          "passengers": [
            {"name": "Marko Marković", "gender": "M", "dateOfBirth": "1990-01-01", "hasValidPassport": true}
          ],
          "firstName": "Marko",
          "lastName": "Marković",
          "email": "marko@example.com",
          "phone": "+381601234567",
          "formDuration": 20
        }
        """;



    @Test
    void ispravanZahtevVraca201IProslediServisu() throws Exception {
        when(bookingService.createBooking(any())).thenReturn(
                BookingResponse.builder().bookingRef("ESC-abcd1234").status(BookingStatus.PENDING)
                        .totalPriceAll(560).numberOfTravelers(1).build());

        mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingRef").value("ESC-abcd1234"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(bookingService).createBooking(any());
    }

    /**
     * Nepotpun zahtev (nedostaje email, putnici, itd.) mora biti odbijen
     * VALIDACIJOM na kontroleru, pre nego što stigne do servisa - inače bi
     * servis morao da brani sam sebe od svakog mogućeg praznog polja.
     */
    @Test
    void nepotpunZahtevVraca400INikadNeStizeDoServisa() throws Exception {
        mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    void nepoznatAerodromVraca400() throws Exception {
        String badAirport = VALID_JSON.replace("\"BEG\"", "\"XXX\""); // nije u BEG|INI|ZAG|BUD|TIM

        mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badAirport))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    void statusEndpointProsledjujeParametreIVracaRezultat() throws Exception {
        when(bookingService.lookupStatus("ESC-abcd1234", "Marković")).thenReturn(
                BookingStatusResponse.builder().bookingRef("ESC-abcd1234").status(BookingStatus.CONFIRMED).build());

        mockMvc.perform(get("/api/booking/status")
                        .param("ref", "ESC-abcd1234")
                        .param("lastName", "Marković"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    /** GET /status bez oba parametra mora vratiti 400, ne 500 (vidi GlobalExceptionHandlerTest). */
    @Test
    void statusEndpointBezParametaraVraca400() throws Exception {
        mockMvc.perform(get("/api/booking/status").param("ref", "ESC-abcd1234"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pricePreviewProsledjujeServisu() throws Exception {
        when(bookingService.previewPrice(10L, 2, AccommodationType.STANDARD, 0, 0, false, false, false))
                .thenReturn(PricePreviewResponse.builder().totalEurAll(1000).build());

        mockMvc.perform(get("/api/booking/price-preview")
                        .param("selectedDateId", "10")
                        .param("numberOfTravelers", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEurAll").value(1000));
    }
}
