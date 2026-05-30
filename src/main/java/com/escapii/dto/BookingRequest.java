package com.escapii.dto;

import com.escapii.model.AccommodationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO koji prima podatke sa forme (svih 8 koraka).
 * Validacija se vrši automatski pre obrade (@Valid u controlleru).
 * Normalizacija (trim, lowercase) vrši se u BookingServiceImpl pre obrade.
 */
@Getter
@Setter
@NoArgsConstructor
public class BookingRequest {

    // ── Korak 1: Polazni aerodrom ─────────────────────────────────────

    @NotBlank(message = "Aerodrom polaska je obavezan")
    @Pattern(regexp = "BEG|INI|ZAG|BUD|TIM", message = "Nepoznat aerodrom polaska")
    private String departureAirport;

    // ── Korak 2: Broj putnika ─────────────────────────────────────────

    @NotNull(message = "Broj putnika je obavezan")
    @Min(value = 1, message = "Minimum 1 putnik")
    @Max(value = 6, message = "Maksimum 6 putnika")
    private Integer numberOfTravelers;

    // ── Korak 3: Izabrani termin (FK → AvailableDate) ─────────────────

    @NotNull(message = "Termin putovanja je obavezan")
    private Long selectedDateId;

    // ── Korak 4: Isključene destinacije (max 3, opciono) ─────────────

    private Long excludedDestination1Id;
    private Long excludedDestination2Id;
    private Long excludedDestination3Id;
    private Long excludedDestination4Id;
    private Long excludedDestination5Id;

    // ── Korak 5: Tip smeštaja ─────────────────────────────────────────

    @NotNull(message = "Tip smeštaja je obavezan")
    private AccommodationType accommodationType;

    // ── Korak 6: Dodaci ───────────────────────────────────────────────

    /** Koliko putnika želi kabinski kofer (0 do numberOfTravelers). */
    @NotNull(message = "Broj kofera je obavezan")
    @Min(value = 0, message = "Ne može biti negativan broj kofera")
    @Max(value = 6, message = "Maksimum 6 kofera")
    private Integer cabinSuitcaseCount = 0;

    private boolean hasInsurance         = false; // +20€/pp
    private boolean hasBreakfast         = false; // +15€/pp
    private boolean hasSeatsTogether     = false; // +24€/pp (12€/smer × 2 smera)
    private boolean hasConnectingFlights = false; // besplatno — saglasnost na presedanje

    // ── Korak 7: Putnici (ime + pasoš) ───────────────────────────────

    @NotEmpty(message = "Potrebno je uneti bar jednog putnika")
    @Size(max = 6, message = "Maksimalno 6 putnika")
    @Valid
    private List<PassengerInfo> passengers;

    // ── Korak 8: Kontakt podaci ───────────────────────────────────────

    @NotBlank(message = "Ime nosioca rezervacije je obavezno")
    @Size(max = 100, message = "Ime ne sme biti duže od 100 karaktera")
    private String firstName;

    @NotBlank(message = "Prezime nosioca rezervacije je obavezno")
    @Size(max = 100, message = "Prezime ne sme biti duže od 100 karaktera")
    private String lastName;

    @NotBlank(message = "Email adresa je obavezna")
    @Email(message = "Email adresa nije validna")
    @Size(max = 200, message = "Email ne sme biti duži od 200 karaktera")
    private String email;

    @NotBlank(message = "Broj telefona je obavezan")
    @Pattern(
        regexp = "^[+]?[0-9\\-\\s]{6,20}$",
        message = "Broj telefona nije validan (dozvoljeni karakteri: cifre, +, -, razmak)"
    )
    private String phone;

    @Size(max = 1000, message = "Napomene ne smeju biti duže od 1000 karaktera")
    private String notes;

    // ── Anti-bot zaštita ──────────────────────────────────────────────

    /** Honeypot — mora biti prazan. Bots ga popune, korisnici ne vide polje. */
    private String website;

    /** Vreme popunjavanja forme u sekundama. Ispod praga = bot. */
    private Integer formDuration;

    // ── Reveal Box (opciono) ──────────────────────────────────────────

    /** Korisnik želi fizički Reveal Box (+25€ flat). */
    private boolean hasRevealBox = false;

    /** Adresa dostave — obavezno ako hasRevealBox=true. */
    @Size(max = 300, message = "Adresa ne sme biti duža od 300 karaktera")
    private String deliveryAddress;

    /** Grad dostave. */
    @Size(max = 100, message = "Grad ne sme biti duži od 100 karaktera")
    private String deliveryCity;

    /** Telefon za dostavu. */
    @Pattern(regexp = "^$|^[+]?[0-9\\-\\s]{6,20}$",
             message = "Telefon za dostavu nije validan")
    private String deliveryPhone;

    /**
     * Opcioni gift vaučer kod koji korisnik unosi u koraku 7.
     * Admin vidi kod i ručno primenjuje popust pri potvrdi rezervacije.
     */
    @Pattern(regexp = "ESC-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}",
             message = "Neispravan format vaučer koda")
    @Size(max = 20)
    private String voucherCode;

    /**
     * Normalizuje string polja pre čuvanja:
     *   - trim whitespace sa svih polja
     *   - email → lowercase
     *   - departureAirport → uppercase
     */
    public void normalize() {
        if (departureAirport  != null) departureAirport  = departureAirport.trim().toUpperCase();
        if (firstName         != null) firstName         = firstName.trim();
        if (lastName          != null) lastName          = lastName.trim();
        if (email             != null) email             = email.trim().toLowerCase();
        if (phone             != null) phone             = phone.trim();
        if (notes             != null) notes             = notes.trim();
        if (deliveryAddress   != null) deliveryAddress   = deliveryAddress.trim();
        if (deliveryCity      != null) deliveryCity      = deliveryCity.trim();
        if (deliveryPhone     != null) deliveryPhone     = deliveryPhone.trim();
        if (passengers != null) passengers.forEach(p -> {
            if (p.getName()    != null) p.setName(p.getName().trim());
            if (p.getGender()  != null) p.setGender(p.getGender().trim().toUpperCase());
            if (p.getVisaInfo() != null) p.setVisaInfo(p.getVisaInfo().trim());
        });
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerInfo {

        @NotBlank(message = "Ime putnika ne sme biti prazno")
        @Size(max = 200, message = "Ime putnika ne sme biti duže od 200 karaktera")
        private String name;

        /** M ili F */
        @NotBlank(message = "Pol putnika je obavezan")
        @Pattern(regexp = "M|F", message = "Pol mora biti M ili F")
        private String gender;

        @NotNull(message = "Datum rođenja putnika je obavezan")
        @Past(message = "Datum rođenja mora biti u prošlosti")
        private LocalDate dateOfBirth;

        /**
         * Slobodan tekst — za koje države putnik ima aktivnu vizu.
         * Opciono (prazno = nema vize osim standardnih srpskih).
         */
        @Size(max = 500, message = "Informacija o vizi ne sme biti duža od 500 karaktera")
        private String visaInfo;

        /** Da li putnik ima validan pasoš (važeći najmanje 6 meseci od povratka). */
        private Boolean hasValidPassport = true;

        /** Broj pasoša kojim putnik putuje. Opciono. */
        @Size(max = 50, message = "Broj pasoša ne sme biti duži od 50 karaktera")
        private String passportNumber;
    }
}
