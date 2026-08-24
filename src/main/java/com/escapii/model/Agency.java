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

    @Column(nullable = false)
    private Boolean active = true;
}
