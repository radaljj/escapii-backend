package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "invoice_sequences")
public class InvoiceSequence {

    @Id
    @Column(name = "year")
    private Integer year;

    @Column(name = "last_seq", nullable = false)
    private Integer lastSeq = 0;

    public InvoiceSequence(int year) {
        this.year = year;
        this.lastSeq = 0;
    }
}
