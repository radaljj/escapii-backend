package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Gift putovanje — kupac traži termin kao poklon za nekoga.
 * Admin flow identičan CustomDateInquiry: admin formira cenu → šalje KUPCU privatni link.
 * Nakon potvrde, primaocu ide reveal email (bez destinacije do 2 dana pre polaska).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "gift_trip_inquiries")
public class GiftTripInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Aerodrom polaska (npr. "BEG"). */
    @Column(nullable = false, length = 10)
    private String airport;

    /** Broj putnika. */
    @Column(nullable = false)
    private Integer travelers;

    /** Željeni datum polaska. */
    @Column(nullable = false)
    private LocalDate desiredDepartureDate;

    /** Željeni broj noćenja (1, 2 ili 3). */
    @Column(nullable = false)
    private Integer nights;

    /** Email KUPCA — na koji šaljemo privatni link za plaćanje. */
    @Column(nullable = false, length = 200)
    private String buyerEmail;

    /** Opcionalna napomena kupca (max 1000 karaktera). */
    @Column(length = 1000)
    private String notes;

    // ── Primalac poklona ─────────────────────────────────────────────────────

    /** Ime primaoca poklona — za reveal email. */
    @Column(nullable = false, length = 200)
    private String recipientName;

    /** Email primaoca — na koji šaljemo reveal email nakon potvrde. */
    @Column(nullable = false, length = 200)
    private String recipientEmail;

    /** Poruka kupca primaocu (opciono, max 500 karaktera). */
    @Column(length = 500)
    private String giftMessage;

    // ── Status i cena ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status = InquiryStatus.PENDING;

    /**
     * Cena putovanja koju admin unosi (ukupno, u EUR).
     * Null = cena još nije određena.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Vreme zatvaranja upita (status → CLOSED). */
    @Column
    private LocalDateTime closedAt;
}
