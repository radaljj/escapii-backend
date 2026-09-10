package com.escapii.service.voucher;

import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.DepartureAirport;
import com.escapii.model.PassengerInfo;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Sve što PDF vaučera POKLONJENOG PUTOVANJA prikazuje - i ništa više.
 *
 * <p>Namerno nema ni jednog polja sa cenom: ovaj vaučer kupac štampa ili
 * prosleđuje obdarenom, a poklon ne nosi cenu. Ako neko za pola godine doda
 * cenu ovde, {@code GiftTripVoucherServiceTest} pada na imenu polja, a
 * {@code VoucherPdfRenderTest} na "€" u tekstu PDF-a.
 *
 * <p>Nema ni imena kupca ("Poklon od"): izvodilo se iz kontakta na formi, a taj
 * kontakt ume da bude bilo ko. Umesto toga kupac sam upiše poruku - ili ne.
 *
 * @param code          kod koji QR i /poklon stranica razumeju - šifra rezervacije, velikim slovima
 * @param departureDate datum polaska
 * @param returnDate    datum povratka
 * @param nights        broj noći
 * @param travelers     broj putnika
 * @param airportCode   IATA kod aerodroma polaska (BEG)
 * @param airportCity   grad na srpskom (Beograd)
 * @param airportName   naziv aerodroma (Aerodrom Nikola Tesla)
 * @param passengers    imena putnika, redom sa rezervacije
 * @param giftMessage   poruka kupca obdarenom, kako je ukucana; prazno sakriva blok
 */
public record TripVoucherData(
    String code,
    LocalDate departureDate,
    LocalDate returnDate,
    int nights,
    int travelers,
    String airportCode,
    String airportCity,
    String airportName,
    List<String> passengers,
    String giftMessage
) {
    /**
     * Iz rezervacije. Ovde, a ne u servisu, da mejl potvrde (PDF u prilogu) i
     * /poklon stranica grade ISTE podatke iz istog koda.
     */
    public static TripVoucherData from(Booking b) {
        AvailableDate d = b.getSelectedDate();
        Optional<DepartureAirport> ap = DepartureAirport.from(b.getDepartureAirport());
        List<String> pax = passengers(b);
        int travelers = b.getNumberOfTravelers() != null ? b.getNumberOfTravelers() : pax.size();
        return new TripVoucherData(
                code(b),
                d.getDepartureDate(), d.getReturnDate(),
                d.getNumberOfNights() != null ? d.getNumberOfNights() : 0,
                travelers,
                b.getDepartureAirport(),
                ap.map(DepartureAirport::citySr).orElse(b.getDepartureAirport()),
                ap.map(DepartureAirport::airportName).orElse(""),
                pax,
                giftMessage(b));
    }

    /** Šifra rezervacije velikim slovima - tako je na vaučeru, u imenu PDF-a i u polju za unos na /poklon. */
    public static String code(Booking b) {
        return b.getBookingRef() == null ? "" : b.getBookingRef().trim().toUpperCase(Locale.ROOT);
    }

    static List<String> passengers(Booking b) {
        if (b.getPassengers() == null) return List.of();
        return b.getPassengers().stream()
                .map(PassengerInfo::getName)
                .filter(n -> n != null && !n.isBlank())
                .map(String::trim)
                .toList();
    }

    /** Poruka kupca, isečena; prazno kad je nema. Ne izvodi se ni iz čega - ili je kupac ukucao, ili je nema. */
    static String giftMessage(Booking b) {
        return b.getGiftMessage() == null ? "" : b.getGiftMessage().trim();
    }
}
