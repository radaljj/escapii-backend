package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "available_dates")
public class AvailableDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate departureDate;

    @Column(nullable = false)
    private LocalDate returnDate;

    /** 2 noci (Pet–Ned) ili 3 noci (Pet–Pon). */
    @Column(nullable = false)
    private Integer numberOfNights;

    @Column(nullable = false, length = 10)
    private String departureAirport;

    @Column(nullable = false)
    private Integer availableSlots;

    /** Osnovna cena u EUR: 279 za 2 noci, 319 za 3 noci. */
    @Column(nullable = false)
    private Integer basePrice;

    @Column(nullable = false)
    private Boolean active = true;

    /**
     * Privatni termin — kreiran samo za određenog korisnika na osnovu upita.
     * Vidljiv samo putem privateToken linka; ne pojavljuje se u javnom listingu.
     */
    @Column(nullable = false)
    private Boolean isPrivate = false;

    /**
     * Jedinstveni token za pristup privatnom terminu.
     * Null za javne termine. Generišemo UUID bez crtica.
     */
    @Column(unique = true, length = 64)
    private String privateToken;

    /**
     * Vreme isteka privatnog linka.
     * Null za javne termine. Tipično NOW + 72 sata.
     */
    private LocalDateTime expiresAt;

    /**
     * Optimistic locking — štiti od race condition-a kada više korisnika
     * istovremeno pokušava da rezerviše isti termin.
     * Primitive long (ne Long) — JDBC getLong() vraća 0 za SQL NULL,
     * čime se izbegava NPE pri Hibernate version increment-u.
     */
    @Version
    private long version;

    /**
     * Potencijalne destinacije koje admin vezuje za ovaj termin.
     * Vidljivo samo adminu — korisnici ne vide ove informacije.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "available_date_destinations",
        joinColumns = @JoinColumn(name = "available_date_id"),
        inverseJoinColumns = @JoinColumn(name = "destination_id")
    )
    private List<Destination> potentialDestinations = new ArrayList<>();
}
