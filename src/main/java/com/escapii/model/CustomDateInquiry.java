package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "custom_date_inquiries")
public class CustomDateInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Aerodrom polaska koji je korisnik tražio (npr. "BEG"). */
    @Column(nullable = false, length = 10)
    private String airport;

    /** Broj putnika iz upita. */
    @Column(nullable = false)
    private Integer travelers;

    /** Željeni datum polaska koji je korisnik odabrao. */
    @Column(nullable = false)
    private LocalDate desiredDepartureDate;

    /** Željeni broj noćenja (1, 2 ili 3). */
    @Column(nullable = false)
    private Integer nights;

    /** Email korisnika - na koji šaljemo privatni link. */
    @Column(nullable = false, length = 200)
    private String email;

    /** Opcionalna napomena korisnika (max 1000 karaktera). */
    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status = InquiryStatus.PENDING;

    /**
     * Cena putovanja koju admin unosi nakon obrade upita (ukupno, u EUR).
     * Null = cena još nije određena.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Vreme kada je upit zatvoren (status → CLOSED).
     * Koristi se za automatsko brisanje 24h nakon zatvaranja.
     * Null = upit još nije zatvoren.
     */
    @Column
    private LocalDateTime closedAt;
}
