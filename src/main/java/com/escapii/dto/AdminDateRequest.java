package com.escapii.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AdminDateRequest {

    @NotNull(message = "Datum polaska je obavezan")
    @FutureOrPresent(message = "Datum polaska ne može biti u prošlosti")
    private LocalDate departureDate;

    @NotNull(message = "Datum povratka je obavezan")
    private LocalDate returnDate;

    // numberOfNights se računa automatski na osnovu departureDate i returnDate

    @NotBlank(message = "Aerodrom polaska je obavezan")
    @Pattern(regexp = "BEG|INI|ZAG|BUD|TIM", message = "Nepoznat aerodrom (BEG, INI, ZAG...)")
    private String departureAirport;

    @NotNull(message = "Broj mesta je obavezan")
    @Min(value = 1, message = "Minimum 1 mesto")
    @Max(value = 500, message = "Maksimum 500 mesta")
    private Integer availableSlots;

    @NotNull(message = "Osnovna cena je obavezna")
    @Min(value = 1, message = "Cena mora biti pozitivna")
    private Integer basePrice;

    /** ID-evi destinacija za ovaj termin - opciono, max 20. */
    @Size(max = 20, message = "Maksimalno 20 destinacija po terminu")
    private List<Long> destinationIds = new ArrayList<>();
}
