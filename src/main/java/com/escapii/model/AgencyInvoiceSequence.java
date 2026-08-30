package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sekvenca za Escapii → agencija fakturu (format ESC-AG-YYYY-NNNN).
 * Odvojena od {@link InvoiceSequence} (koja broji kupčeve profakture) da se
 * dva brojaca ne mesaju - agencijska sekvenca 0001 moze i treba da postoji
 * nezavisno od kupčeve 0001 u istoj godini.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "agency_invoice_sequences")
public class AgencyInvoiceSequence {

    @Id
    @Column(name = "year")
    private Integer year;

    @Column(name = "last_seq", nullable = false)
    private Integer lastSeq = 0;

    public AgencyInvoiceSequence(int year) {
        this.year = year;
        this.lastSeq = 0;
    }
}
