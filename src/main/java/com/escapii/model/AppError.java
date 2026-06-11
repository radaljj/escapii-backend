package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Čuva neočekivane greške (5xx) koje se dogode u aplikaciji.
 * Iste greške (isti endpoint + tip exceptiona) se grupišu - ne pravi se novi red, samo se povećava count.
 */
@Entity
@Table(name = "app_errors")
@Getter @Setter @NoArgsConstructor
public class AppError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "POST /api/booking" */
    @Column(nullable = false)
    private String endpoint;

    /** "NullPointerException", "DataIntegrityViolationException", itd. */
    @Column(nullable = false)
    private String exceptionType;

    /** Kratka poruka greške (max 500 chars) */
    @Column(columnDefinition = "TEXT")
    private String message;

    /** Stack trace (max 4000 chars) */
    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    /** HTTP status kod (uvek 500 za neočekivane) */
    private Integer statusCode;

    /** Koliko puta se ista greška ponovila */
    @Column(nullable = false)
    private Integer count = 1;

    /** Kada je greška prvi put viđena */
    @Column(nullable = false)
    private LocalDateTime firstSeenAt;

    /** Kada je greška poslednji put viđena */
    @Column(nullable = false)
    private LocalDateTime lastSeenAt;

    /** Admin je označio grešku kao rešenu */
    @Column(nullable = false)
    private boolean resolved = false;
}
