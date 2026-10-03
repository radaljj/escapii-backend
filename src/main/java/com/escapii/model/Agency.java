package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "agencies")
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String contactName;

    @Column(length = 200)
    private String contactEmail;

    @Column(length = 50)
    private String contactPhone;

    @Column(length = 1000)
    private String notes;

    // ── Pravni podaci za fakturu (opciono) ─────────────────────────────────
    // Faktura bez punog naziva, adrese i PIB-a kupca nije prava faktura; dok agencija
    // ne dostavi podatke, na PDF-u stoji samo ime i kontakt (kao do sada).

    /** Pun poslovni naziv iz APR-a, npr. "Sani Tours d.o.o. Beograd". Prazno = koristi se name. */
    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(length = 200)
    private String address;

    /** PIB - 9 cifara. */
    @Column(length = 20)
    private String pib;

    /** Matični broj - 8 cifara. */
    @Column(length = 20)
    private String mb;

    @Column(nullable = false)
    private Boolean active = true;
}
