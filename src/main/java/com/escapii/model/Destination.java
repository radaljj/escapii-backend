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
    private Boolean active;

    @Column(length = 255)
    private String imageUrl;

    /**
     * Aerodroми polaska sa kojih postoji let ka ovoj destinaciji.
     * Vrednosti: "BEG", "INI" (može biti više).
     * Koristi se za filtriranje u /api/destinations?airport=BEG.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "destination_departure_airports",
        joinColumns = @JoinColumn(name = "destination_id")
    )
    @Column(name = "departure_airport", length = 10)
    private Set<String> departureAirports = new HashSet<>();
}
