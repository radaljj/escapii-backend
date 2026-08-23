package com.escapii.controller;

import com.escapii.config.GlobalExceptionHandler;
import com.escapii.dto.BookingRequest;
import com.escapii.dto.BookingResponse;
import com.escapii.dto.BookingStatusResponse;
import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AccommodationType;
import com.escapii.model.BookingStatus;
import com.escapii.service.BookingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import static org.junit.jupiter.api.Assertions.assertEquals;
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
            {"name": "Marko Marković", "gender": "M", "dateOfBirth": "1990-01-01", "hasValidPassport": true, "passportNumber": "AA1234567"}
          ],
          "firstName": "Marko",
          "lastName": "Marković",
          "email": "marko@example.com",
          "phone": "+381601234567",
          "acceptedTerms": true,
          "acceptedPrivacy": true,
          "acceptedGdpr": true,
          "consentVersion": "2026-08-24",
          "consentLang": "sr",
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

    /**
     * Checkbox-evi za uslove/privatnost/GDPR postoje samo na frontu i lako se
     * zaobiđu direktnim POST-om. Backend mora sam odbiti zahtev bez saglasnosti -
     * inače nemamo nikakav dokaz da je korisnik ikada išta prihvatio.
     */
    @Test
    void zahtevBezSaglasnostiVraca400() throws Exception {
        for (String polje : new String[]{"acceptedTerms", "acceptedPrivacy", "acceptedGdpr"}) {
            String bezSaglasnosti = VALID_JSON.replace("\"" + polje + "\": true", "\"" + polje + "\": false");

            mockMvc.perform(post("/api/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bezSaglasnosti))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(bookingService);
    }

    @Test
    void nepoznatAerodromVraca400() throws Exception {
        String badAirport = VALID_JSON.replace("\"BEG\"", "\"XXX\""); // nije u DepartureAirport

        mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badAirport))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    /**
     * Svaki aerodrom iz DepartureAirport mora proći kroz @ValidDepartureAirport
     * validaciju - ovo hvata slučaj da je aerodrom dodat u enum ali ga validacija
     * i dalje odbija (npr. da je neko vratio stari @Pattern sa fiksnom listom).
     */
    @Test
    void sviDefinisaniAerodromiProlazeValidaciju() throws Exception {
        when(bookingService.createBooking(any())).thenReturn(
                BookingResponse.builder().bookingRef("ESC-abcd1234").status(BookingStatus.PENDING)
                        .totalPriceAll(560).numberOfTravelers(1).build());

        for (com.escapii.model.DepartureAirport a : com.escapii.model.DepartureAirport.values()) {
            mockMvc.perform(post("/api/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON.replace("\"BEG\"", "\"" + a.code() + "\"")))
                    .andExpect(status().isCreated());
        }
    }

    /**
     * Aerodrom i pol se normalizuju (trim + uppercase) PRE @Valid - kroz
     * @JsonSetter na BookingRequest, ne kroz normalize() koji se zove tek u
     * BookingServiceImpl, posle validacije. Bez ovoga bi klijent koji pošalje
     * malim slovima dobio generičku 400 grešku umesto da prođe normalizovano.
     */
    @Test
    void malaSlovaAerodromIPolSeNormalizujuPreValidacijeIProlaze() throws Exception {
        when(bookingService.createBooking(any())).thenReturn(
                BookingResponse.builder().bookingRef("ESC-abcd1234").status(BookingStatus.PENDING)
                        .totalPriceAll(560).numberOfTravelers(1).build());

        String lowerCase = VALID_JSON
                .replace("\"BEG\"", "\" beg \"")
                .replace("\"M\"", "\" m \"");

        mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lowerCase))
                .andExpect(status().isCreated());

        ArgumentCaptor<BookingRequest> captor = ArgumentCaptor.forClass(BookingRequest.class);
        verify(bookingService).createBooking(captor.capture());
        assertEquals("BEG", captor.getValue().getDepartureAirport());
        assertEquals("M", captor.getValue().getPassengers().get(0).getGender());
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
