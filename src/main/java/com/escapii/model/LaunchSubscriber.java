package com.escapii.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Email prijavljen na coming-soon stranici za obaveštenje kad sajt krene live.
 * Privremena tabela - uklanja se kad sajt ode u produkciju.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "launch_subscribers", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class LaunchSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
