package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Javni identifikator rezervacije (npr. ESC-a3f8b2c1). Prikazuje se korisniku. */
    @Column(nullable = false, unique = true, length = 20)
    private String bookingRef;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BookingStatus oldStatus;

    // ── Polazak ───────────────────────────────────────────────────────

    @Column(nullable = false, length = 10)
    private String departureAirport;

    @Column(nullable = false)
    private Integer numberOfTravelers;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "selected_date_id", nullable = false)
    private AvailableDate selectedDate;

    // ── Isključene destinacije (max 3) ────────────────────────────────

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "excluded_dest1_id")
    private Destination excludedDestination1; // besplatno

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "excluded_dest2_id")
    private Destination excludedDestination2; // +10€ flat

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "excluded_dest3_id")
    private Destination excludedDestination3; // +10€ flat

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "excluded_dest4_id")
    private Destination excludedDestination4; // +15€ flat

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "excluded_dest5_id")
    private Destination excludedDestination5; // +15€ flat

    @Column(nullable = false)
    private Integer exclusionCount = 0;

    /** Flat naknada za isključivanja (0, 10 ili 20€). */
    @Column(name = "exclusion_cost_eur", nullable = false)
    private Integer exclusionCostEur = 0;

    // ── Smeštaj ───────────────────────────────────────────────────────

    @Column(nullable = false, length = 20)
    private String accommodationType; // STANDARD | SUPERIOR

    /** Flat upgrade naknada za Superior sobu (0 ili 100€). */
    @Column(nullable = false)
    private Integer accommodationExtra = 0;

    // ── Dodaci ────────────────────────────────────────────────────────

    /** Broj putnika koji su odabrali kabinski kofer (0–N). */
    @Column(nullable = false)
    private Integer cabinSuitcaseCount = 0; // ×100€/pp

    @Column(nullable = false)
    private Boolean hasInsurance    = false; // +10€/pp

    @Column(nullable = false)
    private Boolean hasBreakfast    = false; // +15€/pp

    @Column(nullable = false)
    private Boolean hasSeatsTogther = false; // +10€/pp (naziv iz spec)

    /** Putnik je saglasan da može presedati tokom leta (ne naplaćuje se). */
    @Column(nullable = false)
    private Boolean hasConnectingFlights = false;

    // ── Putnici ───────────────────────────────────────────────────────

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "booking_passengers", joinColumns = @JoinColumn(name = "booking_id"))
    @OrderColumn(name = "position")
    private List<PassengerInfo> passengers = new ArrayList<>();

    // ── Kalkulisane cene (EUR) ────────────────────────────────────────

    @Column(nullable = false)
    private Integer basePricePerPerson = 0;

    @Column(nullable = false)
    private Integer totalPricePerPerson = 0; // eurPerPerson (per-person stavke)

    @Column(nullable = false)
    private Integer totalPriceAll = 0;       // kompletna cena rezervacije

    // ── Pasoš ─────────────────────────────────────────────────────────

    // ── Kontakt ───────────────────────────────────────────────────────

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Interna napomena admina — nije vidljiva korisniku. */
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @PrePersist
    protected void onCreate() {
        createdAt  = LocalDateTime.now();
        bookingRef = "ESC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
