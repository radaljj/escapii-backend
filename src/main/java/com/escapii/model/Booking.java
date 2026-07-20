package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.BatchSize;

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

    // ── Isključene destinacije (max 4) ────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "excluded_dest1_id")
    private Destination excludedDestination1; // besplatno

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "excluded_dest2_id")
    private Destination excludedDestination2; // +10€ flat

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "excluded_dest3_id")
    private Destination excludedDestination3; // +10€ flat

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "excluded_dest4_id")
    private Destination excludedDestination4; // +10€ flat

    @Column(nullable = false)
    private Integer exclusionCount = 0;

    /** Flat naknada za isključivanja (0, 10 ili 20€). */
    @Column(name = "exclusion_cost_eur", nullable = false)
    private Integer exclusionCostEur = 0;

    // ── Smeštaj ───────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccommodationType accommodationType;

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

    @Column(name = "has_seats_together", nullable = false)
    private Boolean hasSeatsTogether = false; // +12€/smer × 2 smera, po osobi

    /** Putnik je saglasan da može presedati tokom leta (ne naplaćuje se). */
    @Column(nullable = false)
    private Boolean hasConnectingFlights = false;

    // ── Putnici ───────────────────────────────────────────────────────

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "booking_passengers", joinColumns = @JoinColumn(name = "booking_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    private List<PassengerInfo> passengers = new ArrayList<>();

    // ── Kalkulisane cene (EUR) ────────────────────────────────────────

    @Column(nullable = false)
    private Integer basePricePerPerson = 0;

    @Column(nullable = false)
    private Integer totalPricePerPerson = 0; // eurPerPerson (per-person stavke)

    @Column(nullable = false)
    private Integer totalPriceAll = 0;       // kompletna cena rezervacije

    // ── Airline booking code ─────────────────────────────────────────

    /**
     * Naziv avio kompanije (npr. "Wizz Air").
     * Unosi admin - prikazuje se korisniku na reveal stranici.
     */
    @Column(name = "airline_name", length = 100)
    private String airlineName;

    /**
     * Kod avio kompanije za check-in (npr. "ABC123").
     * Unosi admin nakon potvrde rezervacije - prikazuje se korisniku na reveal stranici.
     */
    @Column(name = "airline_booking_code", length = 20)
    private String airlineBookingCode;

    // ── Kontakt ───────────────────────────────────────────────────────

    /** Pol prvog putnika (M/F) - koristi se za pozdrav u mejlovima. Nullable za stare rezervacije. */
    @Column(name = "lead_passenger_gender", length = 1)
    private String leadPassengerGender;

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

    /** Interna napomena admina - nije vidljiva korisniku. */
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    // ── Vaučer ────────────────────────────────────────────────────────

    /** Kod vaučera koji je primenjen pri rezervaciji. Null = bez vaučera. */
    @Column(name = "applied_voucher_code", length = 20)
    private String appliedVoucherCode;

    /** Iznos popusta koji je primenjen putem gift vaučera (EUR). Null = 0. */
    @Column(name = "voucher_discount")
    private Integer voucherDiscount;

    // ── Otkrivanje destinacije ────────────────────────────────────────

    /** Destinacija koju admin dodjeljuje - šalje se korisniku T-2 dana (48h) pre polaska. */
    @Column(name = "assigned_destination", length = 200)
    private String assignedDestination;

    /**
     * Opcionalni geocoding hint za vremensku prognozu.
     * Ako admin unese "Tenerife", geocoder može da pogodi planinu umesto obale.
     * Ovde se upiše precizan naziv: "Santa Cruz de Tenerife, Spain".
     * Ako prazno → koristi se assignedDestination.
     */
    @Column(name = "weather_city", length = 200)
    private String weatherCity;

    /**
     * UUID token za magic link u reveal emailu.
     * Generira se čim admin unese destinaciju. Nikad se ne izlaže kroz javne API-je.
     */
    @Column(name = "reveal_token", unique = true, length = 64)
    private String revealToken;

    /** Trenutak kad je reveal email poslan korisniku - null znači još nije poslan. */
    @Column(name = "reveal_sent_at")
    private LocalDateTime revealSentAt;

    /** Trenutak kad je weather forecast email poslan korisniku - null znači još nije poslan. */
    @Column(name = "forecast_sent_at")
    private LocalDateTime forecastSentAt;

    /** Broj profakture (npr. ESC-INV-2026-0001). Null znači još nije poslata. */
    @Column(name = "invoice_number", length = 25, unique = true)
    private String invoiceNumber;

    /** Trenutak kad je profaktura poslata korisniku - null znači još nije poslata. */
    @Column(name = "invoice_sent_at")
    private LocalDateTime invoiceSentAt;

    // ── Zvanični dokument rezervacije (od partnerske agencije) ─────────

    /**
     * PDF sa zvaničnim podacima rezervacije koje je admin dobio od partnerske
     * agencije (karte, vaučer smeštaja) i ručno uploadovao u admin panelu.
     * Šalje se korisniku automatski čim: (1) dokument postoji I (2) korisnik
     * je potvrdio da je video reveal (RevealEvent) - bez obzira na redosled.
     */
    // NAPOMENA: bez @Lob namerno - @Lob na byte[] tera Hibernate da koristi
    // PostgreSQL Large Object (OID) semantiku umesto bytea, čak i uz eksplicitni
    // columnDefinition="bytea" (uzrokuje "column is of type bytea but expression
    // is of type oid" na insert-u). Bez @Lob, Hibernate 6 mapira byte[] direktno
    // na bytea preko standardnog VARBINARY JDBC tipa - što i jeste kolona u bazi.
    @Column(name = "confirmation_document", columnDefinition = "bytea")
    private byte[] confirmationDocument;

    @Column(name = "confirmation_document_filename", length = 255)
    private String confirmationDocumentFilename;

    @Column(name = "confirmation_document_uploaded_at")
    private LocalDateTime confirmationDocumentUploadedAt;

    /** Trenutak kad je dokument poslat korisniku mejlom - null znači još nije poslat. */
    @Column(name = "confirmation_sent_at")
    private LocalDateTime confirmationSentAt;

    // ── Reveal Box ────────────────────────────────────────────────────

    /** Korisnik je odabrao fizički Reveal Box (25€ flat). */
    @Column(name = "has_reveal_box", nullable = false)
    private Boolean hasRevealBox = false;

    /** Adresa dostave za Reveal Box. */
    @Column(name = "delivery_address", length = 300)
    private String deliveryAddress;

    /** Grad dostave za Reveal Box. */
    @Column(name = "delivery_city", length = 100)
    private String deliveryCity;

    /** Telefon za dostavu Reveal Box-a. */
    @Column(name = "delivery_phone", length = 50)
    private String deliveryPhone;

    /** Dodatne info za dostavu (stan, sprat, interfon...). Opciono. */
    @Column(name = "delivery_apartment", length = 150)
    private String deliveryApartment;

    /**
     * Admin označi da je Reveal Box fizički poslan korisniku.
     * Ako je true → auto-reveal email se NE šalje (korisnik otvara kutiju).
     */
    @Column(name = "reveal_box_sent", nullable = false)
    private Boolean revealBoxSent = false;

    @PrePersist
    protected void onCreate() {
        createdAt  = LocalDateTime.now();
        bookingRef = "ESC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
