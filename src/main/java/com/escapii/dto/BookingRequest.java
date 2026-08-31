package com.escapii.dto;

import com.escapii.model.AccommodationType;
import com.escapii.validation.ValidDepartureAirport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO koji prima podatke sa forme (svih 8 koraka).
 * Validacija se vrši automatski pre obrade (@Valid u controlleru), a to je PRE
 * poziva normalize() u BookingServiceImpl - za polja čiji @Pattern zavisi od
 * case-a (departureAirport, PassengerInfo.gender) normalizacija zato mora ići
 * kroz @JsonSetter (izvršava se pri Jackson deserijalizaciji, dakle pre @Valid),
 * ne kroz normalize(). Ostala polja (trim) ne utiču na ishod validacije pa im
 * je dovoljno da se normalizuju u normalize() pre čuvanja.
 */
@Getter
@Setter
@NoArgsConstructor
public class BookingRequest {

    // ── Korak 1: Polazni aerodrom ─────────────────────────────────────

    @NotBlank(message = "Aerodrom polaska je obavezan")
    @ValidDepartureAirport
    private String departureAirport;

    /**
     * Normalizuje pre nego što @Valid vidi vrednost (Jackson zove setter pri
     * deserijalizaciji, a to je pre validacije) - inače npr. "beg" ili " BEG "
     * padne na @Pattern iako je posle normalize() validan.
     */
    @JsonSetter("departureAirport")
    public void setDepartureAirport(String departureAirport) {
        this.departureAirport = departureAirport == null ? null : departureAirport.trim().toUpperCase();
    }

    // ── Korak 2: Broj putnika ─────────────────────────────────────────

    @NotNull(message = "Broj putnika je obavezan")
    @Min(value = 1, message = "Minimum 1 putnik")
    @Max(value = 6, message = "Maksimum 6 putnika")
    private Integer numberOfTravelers;

    // ── Korak 3: Izabrani termin (FK → AvailableDate) ─────────────────

    @NotNull(message = "Termin putovanja je obavezan")
    private Long selectedDateId;

    /** Token za privatni termin — obavezan samo kad je termin privatan, inače se ignoriše. */
    @Size(max = 64, message = "privateToken je predugačak")
    private String privateToken;

    // ── Korak 4: Isključene destinacije (max 4, opciono) ─────────────

    private Long excludedDestination1Id;
    private Long excludedDestination2Id;
    private Long excludedDestination3Id;
    private Long excludedDestination4Id;

    // ── Korak 5: Tip smeštaja ─────────────────────────────────────────

    @NotNull(message = "Tip smeštaja je obavezan")
    private AccommodationType accommodationType;

    // ── Korak 6: Dodaci ───────────────────────────────────────────────

    /** Koliko putnika želi kabinski kofer (0 do numberOfTravelers). */
    @NotNull(message = "Broj kofera je obavezan")
    @Min(value = 0, message = "Ne može biti negativan broj kofera")
    @Max(value = 6, message = "Maksimum 6 kofera")
    private Integer cabinSuitcaseCount = 0;

    private boolean hasInsurance         = false; // +12€/pp
    private boolean hasBreakfast         = false; // +12€/pp/noć
    private boolean hasSeatsTogether     = false; // +24€/pp (12€/smer × 2 smera)
    private boolean hasConnectingFlights = false; // besplatno - saglasnost na presedanje

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

    /** Honeypot - mora biti prazan. Bots ga popune, korisnici ne vide polje. */
    private String website;

    /** Vreme popunjavanja forme u sekundama. Ispod praga = bot. */
    private Integer formDuration;

    // ── Reveal Box (opciono) ──────────────────────────────────────────

    /** Korisnik želi fizički Reveal Box (+35€ flat). */
    private boolean hasRevealBox = false;

    /** Adresa dostave - obavezno ako hasRevealBox=true. */
    @Size(max = 300, message = "Adresa ne sme biti duža od 300 karaktera")
    private String deliveryAddress;

    /** Grad dostave. */
    @Size(max = 100, message = "Grad ne sme biti duži od 100 karaktera")
    private String deliveryCity;

    /** Telefon za dostavu. */
    @Pattern(regexp = "^$|^[+]?[0-9\\-\\s]{6,20}$",
             message = "Telefon za dostavu nije validan")
    private String deliveryPhone;

    /** Dodatne info za dostavu (stan, sprat, interfon...). Opciono. */
    @Size(max = 150, message = "Dodatne info ne smeju biti duže od 150 karaktera")
    private String deliveryApartment;

    /**
     * Opcioni gift vaučer kod koji korisnik unosi u koraku 7.
     * Admin vidi kod i ručno primenjuje popust pri potvrdi rezervacije.
     */
    @Pattern(regexp = "ESC-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}",
             message = "Neispravan format vaučer koda")
    @Size(max = 20)
    private String voucherCode;

    // ── Saglasnosti (GDPR dokaz) ──────────────────────────────────────
    // Checkbox-evi na frontu se mogu zaobići direktnim API pozivom, pa se
    // prihvatanje validira i ovde, a vreme/verzija/jezik čuvaju uz rezervaciju.

    @AssertTrue(message = "Morate prihvatiti uslove korišćenja")
    private boolean acceptedTerms;

    @AssertTrue(message = "Morate prihvatiti politiku privatnosti")
    private boolean acceptedPrivacy;

    @AssertTrue(message = "Morate dati saglasnost za obradu ličnih podataka")
    private boolean acceptedGdpr;

    /** Verzija dokumenata koja je bila prikazana korisniku (npr. "2026-08-24"). */
    @Size(max = 20, message = "Verzija saglasnosti ne sme biti duža od 20 karaktera")
    private String consentVersion;

    /** Jezik na kom su dokumenti prikazani ("sr" ili "en"). */
    @Pattern(regexp = "^$|^(sr|en)$", message = "Jezik saglasnosti mora biti sr ili en")
    private String consentLang;

    @AssertTrue(message = "Adresa, grad i telefon za dostavu su obavezni kada je Reveal Box odabran")
    public boolean isDeliveryComplete() {
        if (!hasRevealBox) return true;
        return deliveryAddress != null && !deliveryAddress.isBlank()
            && deliveryCity    != null && !deliveryCity.isBlank()
            && deliveryPhone   != null && !deliveryPhone.isBlank();
    }

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
        if (deliveryApartment != null) deliveryApartment = deliveryApartment.trim();
        if (consentVersion    != null) consentVersion    = consentVersion.trim();
        if (consentLang       != null) consentLang       = consentLang.trim().toLowerCase();
        if (passengers != null) passengers.forEach(p -> {
            if (p.getName()    != null) p.setName(p.getName().trim());
            if (p.getGender()  != null) p.setGender(p.getGender().trim().toUpperCase());
            if (p.getVisaInfo()       != null) p.setVisaInfo(p.getVisaInfo().trim());
            if (p.getPassportNumber() != null) p.setPassportNumber(p.getPassportNumber().trim().toUpperCase());
            if (p.getPassportCountry() != null) p.setPassportCountry(p.getPassportCountry().trim());
        });
    }

    @Getter
    @Setter
    @NoArgsConstructor
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
         * Slobodan tekst - za koje države putnik ima aktivnu vizu.
         * Opciono (prazno = nema vize osim standardnih srpskih).
         */
        @Size(max = 500, message = "Informacija o vizi ne sme biti duža od 500 karaktera")
        private String visaInfo;

        /** Da li putnik ima validan pasoš (važeći najmanje 6 meseci od povratka). */
        @AssertTrue(message = "Putnik mora imati validan pasoš (važeći min. 6 meseci od povratka)")
        private Boolean hasValidPassport = true;

        /** Zemlja pasoša (dropdown vrednost, npr. "Srbija"). Opciono. */
        @Size(max = 50, message = "Zemlja pasoša ne sme biti duža od 50 karaktera")
        private String passportCountry;

        /** Serijski broj pasoša (slova i cifre, 5–20 karaktera, automatski uppercase). */
        @NotBlank(message = "Broj pasoša je obavezan")
        @Pattern(regexp = "^[A-Z0-9]{5,20}$",
                 message = "Broj pasoša nije validan (dozvoljena su slova i cifre, 5–20 karaktera)")
        private String passportNumber;

        /**
         * Ručni konstruktor umesto Lombok @AllArgsConstructor - Spring Boot
         * registruje ParameterNamesModule, pa bi Jackson bez ovoga konstruisao
         * objekat direktno kroz all-args konstruktor (implicit creator),
         * zaobilazeći setGender() i @Valid bi video neobrađenu vrednost
         * (npr. " m " ne prolazi @Pattern="M|F"). Isto zadržava i default
         * hasValidPassport=true kad JSON izostavi to polje - all-args
         * konstruktor bi ga inače postavio na null.
         */
        @JsonCreator
        public PassengerInfo(
                @JsonProperty("name") String name,
                @JsonProperty("gender") String gender,
                @JsonProperty("dateOfBirth") LocalDate dateOfBirth,
                @JsonProperty("visaInfo") String visaInfo,
                @JsonProperty("hasValidPassport") Boolean hasValidPassport,
                @JsonProperty("passportCountry") String passportCountry,
                @JsonProperty("passportNumber") String passportNumber) {
            this.name = name;
            this.gender = gender == null ? null : gender.trim().toUpperCase();
            this.dateOfBirth = dateOfBirth;
            this.visaInfo = visaInfo;
            this.hasValidPassport = hasValidPassport != null ? hasValidPassport : Boolean.TRUE;
            this.passportCountry = passportCountry;
            this.passportNumber = passportNumber != null ? passportNumber.trim().toUpperCase() : null;
        }

        public void setPassportNumber(String passportNumber) {
            this.passportNumber = passportNumber != null ? passportNumber.trim().toUpperCase() : null;
        }

        /** Normalizuje i kad se gender postavlja van JSON deserijalizacije (npr. normalize()). */
        public void setGender(String gender) {
            this.gender = gender == null ? null : gender.trim().toUpperCase();
        }
    }
}
