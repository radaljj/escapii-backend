package com.escapii.service.voucher;

import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.DepartureAirport;
import com.escapii.model.PassengerInfo;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Arrays;
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
 * @param code          kod koji QR i /poklon stranica razumeju - šifra rezervacije, velikim slovima
 * @param departureDate datum polaska
 * @param returnDate    datum povratka
 * @param nights        broj noći
 * @param travelers     broj putnika
 * @param airportCode   IATA kod aerodroma polaska (BEG)
 * @param airportCity   grad na srpskom (Beograd)
 * @param airportName   naziv aerodroma (Aerodrom Nikola Tesla)
 * @param passengers    imena putnika, redom sa rezervacije
 * @param buyerName     ko poklanja ("Ime Prezime"); prazno sakriva kolonu - i namerno je prazno
 *                      kad je kupac ista osoba kao obdareni (vidi {@link #buyerName})
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
    String buyerName
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
                buyerName(b));
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

    /**
     * Ko poklanja = kontakt iz forme ("Tvoji podaci" kod poklona - onaj ko plaća i
     * dobija profakturu). Ako je tu ukucano isto ime kao ime obdarenog, "Poklon od"
     * bi glasilo na samog obdarenog - besmisleno - pa se tada ne prikazuje nigde
     * (ni na PDF-u ni na /poklon). Poređenje ne gleda velika slova, redosled
     * ime/prezime ni dijakritiku: "markovic marko" je isti čovek kao "Marko Marković".
     */
    static String buyerName(Booking b) {
        String ime     = b.getFirstName() == null ? "" : b.getFirstName().trim();
        String prezime = b.getLastName()  == null ? "" : b.getLastName().trim();
        String kupac = (ime + " " + prezime).trim();
        return istaOsoba(kupac, b.getGiftRecipientName()) ? "" : kupac;
    }

    /** Ista osoba: isti skup reči imena, bez obzira na velika slova, redosled i dijakritiku. */
    static boolean istaOsoba(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return false;
        return kljuc(a).equals(kljuc(b));
    }

    private static String kljuc(String ime) {
        String bez = Normalizer.normalize(ime, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")      // č→c, š→s, ž→z, ć→c
                .replace('đ', 'd').replace('Đ', 'D') // đ nema dekompoziciju
                .toLowerCase(Locale.ROOT).trim();
        String[] reci = bez.split("\\s+");
        Arrays.sort(reci);
        return String.join(" ", reci);
    }
}
