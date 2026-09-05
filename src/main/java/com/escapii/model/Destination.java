package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "destinations")
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** IATA kod odredišnog aerodroma (kuda se leti, ne odakle). */
    @Column(nullable = false, length = 10)
    private String airportCode;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(length = 50)
    private String region;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(length = 255)
    private String imageUrl;

    /** Engleski naziv grada/destinacije - automatski popunjen iz IATA koda. */
    @Column(length = 100)
    private String nameEn;

    /** Engleski naziv države - automatski popunjen iz IATA koda. */
    @Column(length = 100)
    private String countryEn;

    /**
     * Aerodroми polaska sa kojih postoji let ka ovoj destinaciji.
     * Vrednosti su kodovi iz DepartureAirport (može biti više).
     * Koristi se za filtriranje u /api/destinations?airport=BEG.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "destination_departure_airports",
        joinColumns = @JoinColumn(name = "destination_id")
    )
    @Column(name = "departure_airport", length = 10)
    private Set<String> departureAirports = new HashSet<>();

    // ── Partnerski dodaci (popup posle otkrivanja destinacije) ────────────
    //
    // Sva tri sluga su identifikatori NA STRANI PARTNERA, ne naši - zato se ne
    // izvode iz imena grada nego se popunjavaju skriptom iz javnih sitemapa
    // partnera. Ime grada je kod nas srpsko ("Beč"), kod partnera englesko
    // ("vienna"), pa preslikavanje mora biti eksplicitno.
    //
    // Prazno polje znači "nemamo link za ovog partnera" i tada se ta kartica u
    // popupu ne prikazuje. Namerno tako: bolje jedna kartica manje nego link
    // koji vodi na praznu stranicu.

    /**
     * Slug i numerički ID lokacije na GetYourGuide-u zajedno, npr. {@code florence-l32}.
     * Čuvaju se kao jedna vrednost jer sam slug bez ID-a vraća 404.
     */
    @Column(name = "gyg_slug", length = 120)
    private String gygSlug;

    /**
     * Slug stranice eSIM paketa na Airalu, npr. {@code italy-esim}. Vezan je za
     * DRŽAVU a ne za grad, i nije ISO kod nego engleski naziv u kebab-case.
     */
    @Column(name = "airalo_slug", length = 120)
    private String airaloSlug;

    /** Slug grada na Bounce-u, npr. {@code florence}. Engleski naziv, bez dijakritike. */
    @Column(name = "bounce_slug", length = 120)
    private String bounceSlug;

    /**
     * Da li Bounce uopšte ima lokacije u ovom gradu. Odvojeno od {@code bounceSlug}
     * jer slug ume biti tačan a grad nepokriven - Memingen i Fridrihshafen nemaju
     * nijednu lokaciju, Kipar nije pokriven uopšte. Kad je false, kartica za
     * prtljag se ne prikazuje.
     */
    @Column(name = "bounce_covered", nullable = false)
    private Boolean bounceCovered = false;
}
