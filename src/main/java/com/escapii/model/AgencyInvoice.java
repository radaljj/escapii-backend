package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Zbirna faktura Escapii → agencija: jedna stavka (slobodan tekst, npr. „Marketinške
 * usluge za period …") i iznos = zbir Escapii zarade po završenim putovanjima koja su
 * ušle u nju. Rezervacije pokazuju na fakturu kroz {@code bookings.agency_invoice_id}.
 *
 * <p>Podaci o agenciji su snimak u trenutku izdavanja (ime, mejl) - ako se agencija
 * kasnije preimenuje, dokument u istoriji ostaje kakav je poslat. PDF se čuva da bi
 * „PDF" i „Pošalji ponovo" u panelu dali baš onaj dokument koji je agencija dobila.
 *
 * <p>Tabelu i kolonu pravi {@code SchemaBootstrap} iz {@code db/schema.sql} pri startu.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "agency_invoices")
public class AgencyInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ESC-AG-YYYY-NNNN iz agency_invoice_sequences (ista serija kao ranije po rezervaciji). */
    @Column(name = "invoice_number", nullable = false, unique = true, length = 25)
    private String invoiceNumber;

    @Column(name = "agency_id", nullable = false)
    private Long agencyId;

    @Column(name = "agency_name", nullable = false, length = 100)
    private String agencyName;

    /** Mejl na koji je faktura poslata (snimak). */
    @Column(name = "agency_email", length = 200)
    private String agencyEmail;

    /** Stavka na fakturi - tekst koji admin unese u popup. */
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    /** Raspon datuma povratka putovanja koja su ušla u fakturu. */
    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "booking_count", nullable = false)
    private Integer bookingCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AgencyInvoiceStatus status = AgencyInvoiceStatus.SENT;

    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Poslednje (uspešno) slanje mejla - prvo slanje ili „Pošalji ponovo". */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    // Bez @Lob - isto kao Booking.confirmationDocument: Hibernate 6 mapira byte[] na bytea.
    @Column(name = "pdf", columnDefinition = "bytea")
    private byte[] pdf;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
