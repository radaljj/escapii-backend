package com.escapii.service.flow;

import com.escapii.dto.BookingRequest;
import com.escapii.dto.PricePreviewResponse;
import com.escapii.mapper.BookingMapper;
import com.escapii.model.AccommodationType;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.DestinationRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.service.PriceCalculator;
import com.escapii.service.email.BookingEmailService;
import com.escapii.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kreiranje rezervacije je ulazna tačka celog novčanog toka - ako ovo
 * pukne ili ne pošalje mejlove, ništa iza njega (faktura, reveal, prognoza)
 * ni ne dolazi na red. Test proverava da uspešna rezervacija UVEK obavesti
 * i tim i kupca, sa upravo sačuvanom (perzistovanom) rezervacijom - ne sa
 * privremenim objektom pre snimanja u bazu.
 *
 * Namerno NE pokriva svaku validacionu granu (honeypot, timing, broj mesta
 * itd.) - to su jednostavne odbrambene provere sa jasnim porukama o grešci,
 * niska su rizika za "tiho pogrešno stanje" koje ovaj paket testova lovi.
 */
@ExtendWith(MockitoExtension.class)
class BookingCreationFlowTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private AvailableDateRepository availableDateRepository;
    @Mock private DestinationRepository destinationRepository;
    @Mock private GiftVoucherRepository giftVoucherRepository;
    @Mock private PriceCalculator priceCalculator;
    @Mock private BookingEmailService bookingEmailService;
    @Mock private BookingMapper bookingMapper;

    private BookingServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new BookingServiceImpl(bookingRepository, availableDateRepository, destinationRepository,
                giftVoucherRepository, priceCalculator, bookingEmailService, bookingMapper);
    }

    private BookingRequest validRequest() {
        BookingRequest r = new BookingRequest();
        r.setDepartureAirport("BEG");
        r.setNumberOfTravelers(1);
        r.setSelectedDateId(10L);
        r.setAccommodationType(AccommodationType.STANDARD);
        r.setCabinSuitcaseCount(0);
        r.setPassengers(List.of(new BookingRequest.PassengerInfo(
                "Marko Marković", "M", LocalDate.of(1990, 1, 1), null, true, null)));
        r.setFirstName("Marko");
        r.setLastName("Marković");
        r.setEmail("marko@example.com");
        r.setPhone("+381601234567");
        // formDuration >= 4 i website prazno - prolaze anti-bot provere
        r.setFormDuration(20);
        return r;
    }

    private AvailableDate activeDate() {
        AvailableDate d = new AvailableDate();
        d.setId(10L);
        d.setActive(true);
        d.setDepartureAirport("BEG");
        d.setDepartureDate(LocalDate.now().plusDays(30));
        d.setReturnDate(LocalDate.now().plusDays(33));
        d.setAvailableSlots(5);
        d.setBasePrice(500);
        return d;
    }

    private PricePreviewResponse price() {
        return PricePreviewResponse.builder()
                .basePricePerPerson(500).accommodationExtraPerPerson(0)
                .breakfastPerPerson(0).seatsTogether(0).insurancePerPerson(0)
                .eurPerPerson(500).exclusionCostFlat(0).soloSurcharge(60)
                .cabinSuitcaseCount(0).cabinSuitcaseTotal(0).revealBoxTotal(0)
                .totalEurAll(560).exclusionCount(0).numberOfTravelers(1).numberOfNights(3)
                .build();
    }

    @Test
    void uspesnaRezervacijaObavestavaTimIKupca() {
        when(availableDateRepository.findById(10L)).thenReturn(Optional.of(activeDate()));
        when(bookingRepository.existsDuplicateBooking(anyString(), anyLong(), any())).thenReturn(false);
        when(priceCalculator.calculate(any(), anyInt(), any(), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyString())).thenReturn(price());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(1L); // simulira dodelu ID-a pri INSERT-u
            return b;
        });

        svc.createBooking(validRequest());

        ArgumentCaptor<Booking> teamBooking = ArgumentCaptor.forClass(Booking.class);
        ArgumentCaptor<Booking> customerBooking = ArgumentCaptor.forClass(Booking.class);
        verify(bookingEmailService).sendTeamNotification(teamBooking.capture());
        verify(bookingEmailService).sendCustomerConfirmation(customerBooking.capture());

        // Oba mejla moraju nositi SAČUVANU rezervaciju (sa ID-jem), ne prolazni
        // objekat od pre snimanja - inače bi npr. link na admin panel bio mrtav.
        assertEquals(1L, teamBooking.getValue().getId());
        assertEquals(1L, customerBooking.getValue().getId());
        assertEquals("marko@example.com", customerBooking.getValue().getEmail());
    }

    /** Nepostojeći/neaktivan termin ne sme stići do slanja mejlova. */
    @Test
    void neaktivanTerminOdbijaSePreSlanjaMejlova() {
        AvailableDate inactive = activeDate();
        inactive.setActive(false);
        when(availableDateRepository.findById(10L)).thenReturn(Optional.of(inactive));

        assertThrows(Exception.class, () -> svc.createBooking(validRequest()));

        verifyNoInteractions(bookingEmailService);
    }
}
