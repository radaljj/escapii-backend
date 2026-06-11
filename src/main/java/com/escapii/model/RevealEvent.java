package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Beleži trenutak kad je korisnik ogrebaо scratch karticu.
 * Namerno odvojena od Booking entiteta - Booking ne treba da zna za ovo.
 * bookingRef je jedini link (ne FK) pa nema kaskadnog uticaja.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "reveal_events")
public class RevealEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Referenca rezervacije - npr. "ESC-6db0f0bb". Unique: samo jedan event po rezervaciji. */
    @Column(name = "booking_ref", nullable = false, unique = true, length = 20)
    private String bookingRef;

    /** Kada je korisnik zaista ogrebaо (50% površine scratch kartice). */
    @Column(name = "revealed_at", nullable = false)
    private LocalDateTime revealedAt;

    public RevealEvent(String bookingRef) {
        this.bookingRef  = bookingRef;
        this.revealedAt  = LocalDateTime.now();
    }
}
