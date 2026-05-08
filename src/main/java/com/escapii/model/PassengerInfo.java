package com.escapii.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PassengerInfo {

    @Column(name = "passenger_name", nullable = false, length = 200)
    private String name;

    /** M ili F */
    @Column(name = "passenger_gender", nullable = false, length = 1)
    private String gender;

    @Column(name = "passenger_dob", nullable = false)
    private LocalDate dateOfBirth;

    /**
     * Slobodan tekst — putnik upisuje za koje države ima aktivnu vizu
     * (npr. "SAD, Kanada, Ujedinjeno Kraljevstvo"). Opciono.
     */
    @Column(name = "visa_info", length = 500)
    private String visaInfo;

    /** Da li putnik ima validan pasoš (važeći najmanje 6 meseci od povratka). */
    @Column(name = "has_valid_passport", nullable = false)
    private Boolean hasValidPassport = true;

    /** Broj pasoša kojim putnik putuje — unosi se pri rezervaciji. Opciono. */
    @Column(name = "passport_number", length = 50)
    private String passportNumber;
}
