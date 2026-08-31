package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Snapshot jedne finansijske stavke na rezervaciji.
 *
 * <p>Svaka stavka pamti (a) sta je kupcu naplaceno u trenutku rezervacije i
 * (b) sta je agencijski stvarni trosak. Escapii i agencija se dele po pravilu
 * iz {@link AllocationType} - taj deo se izvodi u {@code AgencySettlementCalculator},
 * ne cuva se u bazi da se ne bi razlikovao od trenutne poslovne logike.
 *
 * <p>Zasto snapshot? Jer ako sutra promenimo cenu doručka (npr. sa 20€ na 12€), stara
 * rezervacija mora ostati u obračunu po staroj ceni. Rekonstrukcija iz {@code PriceCalculatorImpl}
 * konstanti je izgubljena u trenutku promene - zato pamtimo iznose ovde.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_financial_items", indexes = {
        @Index(name = "idx_bfi_booking", columnList = "booking_id"),
        @Index(name = "idx_bfi_type", columnList = "item_type")
})
public class BookingFinancialItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 32)
    private ItemType itemType;

    /** Alokacija se izvodi iz itemType, ali cuvamo je za lakse SQL upite i istoriju
     *  (ako se poslovno pravilo promeni, stara rezervacija zadrzava tadasnju alokaciju). */
    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type", nullable = false, length = 32)
    private AllocationType allocationType;

    @Column(name = "description", length = 255)
    private String description;

    /** Kolicina koja je proizvela ovaj total (broj noci × putnika za doručak,
     *  broj naplativih isključivanja, itd.). Cuva se za auditni prikaz u panelu. */
    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    /** Jedinicna cena naplacena kupcu (npr. 12€/os/noć za doručak). */
    @Column(name = "unit_customer_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCustomerPrice = BigDecimal.ZERO;

    /** Ukupno naplaceno kupcu za ovu stavku (quantity × unitCustomerPrice). Snapshot. */
    @Column(name = "customer_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal customerTotal = BigDecimal.ZERO;

    /** Stvarni trosak agencije za ovu stavku. Null znaci "admin jos nije uneo" -
     *  za MARGIN_50_50 stavke blokira fakturisanje dok se ne popuni.
     *  Za ESCAPII_100 stavke se ne unosi (uvek 0/null - agencija ne snosi trosak). */
    @Column(name = "agency_cost", precision = 12, scale = 2)
    private BigDecimal agencyCost;

    /** Trosak aviona - samo za BASE_PACKAGE. Zajedno sa hotelAgencyCost daje agencyCost. */
    @Column(name = "flight_agency_cost", precision = 12, scale = 2)
    private BigDecimal flightAgencyCost;

    /** Trosak hotela (sa svim porezima) - samo za BASE_PACKAGE. */
    @Column(name = "hotel_agency_cost", precision = 12, scale = 2)
    private BigDecimal hotelAgencyCost;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
