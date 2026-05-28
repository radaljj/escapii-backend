package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "gift_vouchers", indexes = {
        @Index(name = "idx_gift_voucher_code", columnList = "code", unique = true)
})
public class GiftVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Kriptografski sigurni vaučer kod u formatu ESC-XXXX-XXXX-XXXX.
     * Generiše se serverski, nikad sa klijenta. Unique index.
     */
    @Column(nullable = false, length = 20, unique = true)
    private String code;

    /** Iznos vaučera u EUR (50, 100, 200, 300, 400, ili custom ≥ 50). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherStatus status = VoucherStatus.PENDING;

    // ── Kupac ────────────────────────────────────────────────────────────────

    /** Email kupca — na koji šaljemo instrukcije za uplatu. */
    @Column(nullable = false, length = 200)
    private String buyerEmail;

    /** Ime kupca (opciono, za personalizaciju emaila). */
    @Column(length = 200)
    private String buyerName;

    /** Lična poruka kupca — ispisuje se na PDF vaučeru. */
    @Column(length = 500)
    private String giftMessage;

    // ── Vremenske oznake ─────────────────────────────────────────────────────

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Kada je admin aktivirao vaučer (označio kao plaćen).
     * Null = nije još aktiviran.
     */
    @Column
    private LocalDateTime activatedAt;

    /**
     * Vaučer važi 1 godinu od aktivacije.
     * Null = nije još aktiviran.
     */
    @Column
    private LocalDateTime expiresAt;

    /**
     * Kada je vaučer iskorišćen u booking-u.
     * Null = nije iskorišćen.
     */
    @Column
    private LocalDateTime usedAt;

    /**
     * Referenca na booking u kome je vaučer primenjen.
     * Null = nije primenjen, ili booking se prati ručno.
     */
    @Column
    private Long usedInBookingRef;
}
