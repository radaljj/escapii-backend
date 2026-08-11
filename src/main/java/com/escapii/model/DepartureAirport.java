package com.escapii.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Jedina definicija aerodroma polaska u sistemu.
 *
 * SVE što zavisi od aerodroma čita odavde - kartice na sajtu, poklon sekcija,
 * dropdown i checkboxovi u admin panelu, kartice liste čekanja, nazivi u
 * mejlovima i pravila za isključivanje destinacija. Frontend ih dobija preko
 * javnog GET /api/airports.
 *
 * DODAVANJE NOVOG AERODROMA = jedan red ovde (+ slika u temi frontenda,
 * putanja images/destinations/{imageSlug}.jpg). Nigde drugde nema izmena.
 *
 * Namerno enum, a ne tabela u bazi: dok je broj aerodroma mali i redak se menja,
 * ovo nema SQL migraciju, nema dinamičku validaciju i deploy je ionako automatski.
 * Ako broj poraste, ova struktura se preslikava 1:1 u tabelu bez promene pozivaoca.
 */
public enum DepartureAirport {

    //  grad (SR)      grad (EN)      slika         naziv aerodroma (SR)          naziv aerodroma (EN)             maxIsklj.  prvoBesplatno  javnoVidljiv  domaci
    BEG("Beograd",     "Belgrade",    "beograd",    "Aerodrom Nikola Tesla",      "Nikola Tesla Airport",          4, true,   true,          true),
    INI("Niš",         "Niš",         "nis",        "Aerodrom Konstantin Veliki", "Constantine the Great Airport", 0, false,  true,          true),
    BUD("Budimpešta",  "Budapest",    "budimpesta", "Aerodrom Ferenc List",       "Ferenc Liszt Airport",          4, true,   false,         false);

    private final String citySr;
    private final String cityEn;
    private final String imageSlug;
    private final String airportName;
    private final String airportNameEn;
    private final int    maxExclusions;
    private final boolean firstExclusionFree;
    private final boolean publiclySelectable;
    /** Aerodrom je u Srbiji - kupac ne treba da potvrđuje sopstveni prevoz do aerodroma. */
    private final boolean domestic;

    DepartureAirport(String citySr, String cityEn, String imageSlug,
                     String airportName, String airportNameEn,
                     int maxExclusions, boolean firstExclusionFree,
                     boolean publiclySelectable, boolean domestic) {
        this.citySr             = citySr;
        this.cityEn             = cityEn;
        this.imageSlug          = imageSlug;
        this.airportName        = airportName;
        this.airportNameEn      = airportNameEn;
        this.maxExclusions      = maxExclusions;
        this.firstExclusionFree = firstExclusionFree;
        this.publiclySelectable = publiclySelectable;
        this.domestic           = domestic;
    }

    public String code()              { return name(); }
    public String citySr()            { return citySr; }
    public String cityEn()            { return cityEn; }
    public String imageSlug()         { return imageSlug; }
    public String airportName()       { return airportName; }
    public String airportNameEn()     { return airportNameEn; }
    public int    maxExclusions()     { return maxExclusions; }
    public boolean firstExclusionFree() { return firstExclusionFree; }
    public boolean domestic()           { return domestic; }

    /**
     * Da li se aerodrom nudi u javnom izboru polazišta. Kad je false, sva logika
     * (cena, pravila, destinacije, privatni termini) i dalje radi - aerodrom se
     * samo ne prikazuje kupcu. Uključivanje pred lansiranje = ovde true.
     */
    public boolean publiclySelectable() { return publiclySelectable; }

    /** Da li aerodrom uopšte dozvoljava isključivanje destinacija. */
    public boolean allowsExclusions() { return maxExclusions > 0; }

    /** Tolerantno na null, mala slova i razmake - poziva se i nad korisničkim unosom. */
    public static Optional<DepartureAirport> from(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(a -> a.name().equals(normalized))
                .findFirst();
    }

    public static boolean isValid(String code) {
        return from(code).isPresent();
    }

    /** Naziv grada na srpskom; nepoznat kod vraća sam kod (npr. za stare zapise). */
    public static String cityNameOf(String code) {
        return from(code).map(DepartureAirport::citySr).orElse(code == null ? "" : code);
    }
}
