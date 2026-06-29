package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "term_destination",
    uniqueConstraints = @UniqueConstraint(columnNames = {"available_date_id", "destination_id"})
)
public class TermDestination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "available_date_id", nullable = false)
    private AvailableDate date;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(nullable = false)
    private boolean active = true;

    public TermDestination(AvailableDate date, Destination destination) {
        this.date        = date;
        this.destination = destination;
        this.active      = true;
    }
}
