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
import java.util.stream.Collectors;

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
     * Privatni termin - kreiran samo za određenog korisnika na osnovu upita.
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
     * Null za javne termine. Tipično NOW + 48 sata.
     */
    private LocalDateTime expiresAt;

    /**
     * Optimistic locking - štiti od race condition-a kada više korisnika
     * istovremeno pokušava da rezerviše isti termin.
     * Primitive long (ne Long) - JDBC getLong() vraća 0 za SQL NULL,
     * čime se izbegava NPE pri Hibernate version increment-u.
     */
    @Version
    private long version;

    /**
     * Per-termin destinacije sa individualnim active flagom.
     * Zamjena za stari @ManyToMany potentialDestinations.
     */
    @OneToMany(mappedBy = "date", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TermDestination> termDestinations = new ArrayList<>();

    /**
     * Email klijenta iz upita na osnovu kog je privatni termin napravljen -
     * da admin zna kome da pošalje privatni link.
     *
     * Čuva se kao tekst, ne kao veza ka upitu, jer se zatvoreni upiti brišu
     * (vidi cleanupClosedInquiries) - veza bi pukla, a adresa nam treba i posle.
     * Null za javne termine i za one ručno prebačene u privatne.
     */
    @Column(length = 200)
    private String clientEmail;
}
