package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "waitlist",
    uniqueConstraints = @UniqueConstraint(columnNames = {"email", "airport"})
)
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String email;

    /** IATA kod aerodroma polaska za koji čeka termine (BEG, INI...). */
    @Column(nullable = false, length = 10)
    private String airport;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
