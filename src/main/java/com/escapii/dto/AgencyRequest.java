package com.escapii.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AgencyRequest(
    @NotBlank @Size(max = 100)  String name,
    @Size(max = 100)            String contactName,
    @Email @Size(max = 200)     String contactEmail,
    @Pattern(regexp = "^$|^[+]?[0-9\\-\\s]{6,20}$",
             message = "Broj telefona nije validan")
    @Size(max = 50)             String contactPhone,
    @Size(max = 1000)           String notes
) {}
