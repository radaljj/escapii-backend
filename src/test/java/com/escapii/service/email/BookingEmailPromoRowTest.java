package com.escapii.service.email;

import com.escapii.dto.CountryDto;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.Destination;
import com.escapii.model.PassengerInfo;
import com.escapii.service.AppErrorService;
import com.escapii.service.DestinationService;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.email.impl.BookingEmailServiceImpl;
import com.escapii.service.voucher.VoucherPdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kad je promo kod pokrio isključivanja, red ostaje u cenovniku mejla sa 0 € i ušteđenim iznosom -
 * kupac vidi da je pogodnost stvarno primenjena. Bez promo koda mejl izgleda kao i do sada.
 */
class BookingEmailPromoRowTest {

    private final EmailSender sender = mock(EmailSender.class);
    private BookingEmailServiceImpl svc;

    private static void set(Object o, String polje, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(polje);
        f.setAccessible(true);
        f.set(o, v);
    }

    @BeforeEach
    void setUp() throws Exception {
        svc = new BookingEmailServiceImpl(sender, new DestinationService() {
            public List<Destination> getDestinationsByAirport(String a) { return List.of(); }
            public List<Destination> getAllDestinations() { return List.of(); }
            public List<CountryDto> fetchCountries() { return List.of(new CountryDto("RS", "Serbia", "Srbija")); }
        }, ime -> ime, mock(VoucherPdfService.class));
        set(svc, "teamEmail", "tim@escapii.rs");
        set(svc, "contactEmail", "info@escapii.rs");
        set(svc, "appErrorService", mock(AppErrorService.class));
        Method init = BookingEmailServiceImpl.class.getDeclaredMethod("initCountryNames");
        init.setAccessible(true);
        init.invoke(svc);
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(true);
    }

    private static Booking rezervacija() {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-a3f8b2c1");
        b.setStatus(BookingStatus.PENDING);
        b.setCreatedAt(LocalDateTime.now());
        b.setFirstName("Ana"); b.setLastName("Anić"); b.setEmail("ana@primer.rs");
        b.setPhone("+381601234567");
        b.setDepartureAirport("BEG");
        b.setNumberOfTravelers(2);
        b.setBasePricePerPerson(500);
        b.setTotalPricePerPerson(500);
        b.setExclusionCount(4);
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(LocalDate.of(2026, 10, 9));
        d.setReturnDate(LocalDate.of(2026, 10, 12));
        d.setNumberOfNights(3);
        d.setDepartureAirport("BEG");
        b.setSelectedDate(d);
        b.setPassengers(new ArrayList<>(List.of(
            new PassengerInfo("Ana Anić",       "F", LocalDate.of(1992, 5, 5), null, true, "Srbija", "BB1"),
            new PassengerInfo("Marko Marković", "M", LocalDate.of(1990, 1, 1), null, true, "Srbija", "AA1"))));
        return b;
    }

    private String mejlKupcu(Booking b) {
        svc.sendCustomerConfirmation(b);
        ArgumentCaptor<String> telo = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("ana@primer.rs"), anyString(), telo.capture());
        return telo.getValue();
    }

    @Test
    void promoPokrioIskljucivanja_redSaNulaEvraIUstedom() {
        Booking b = rezervacija();
        b.setExclusionCostEur(0);
        b.setPromoCode("SKIP3");
        b.setPromoSavedEur(60);
        b.setTotalPriceAll(1000);

        String html = mejlKupcu(b);

        assertTrue(html.contains("besplatno uz promo kod SKIP3"), "red o promo kodu");
        assertTrue(html.contains("ušteda 60 €"));
        assertFalse(html.contains("10€/os"), "nema reda sa naplatom isključivanja");
    }

    @Test
    void bezPromoKoda_redovnaNaplataKaoIDoSada() {
        Booking b = rezervacija();
        b.setExclusionCostEur(60);
        b.setTotalPriceAll(1060);

        String html = mejlKupcu(b);

        assertTrue(html.contains("Isključivanja (3× 10€/os)"));
        assertFalse(html.contains("promo kod"));
    }

    @Test
    void promoBezEfekta_nemaRedaOPromoKodu() {
        Booking b = rezervacija();
        b.setExclusionCount(1);           // prvo isključivanje je ionako besplatno
        b.setExclusionCostEur(0);
        b.setPromoCode("SKIP3");
        b.setPromoSavedEur(0);
        b.setTotalPriceAll(1000);

        assertFalse(mejlKupcu(b).contains("promo kod"));
    }
}
