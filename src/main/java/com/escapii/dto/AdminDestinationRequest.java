package com.escapii.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO za postavljanje destinacije rezervacije.
 * Koristi se umesto sirove Map&lt;String, String&gt; radi type-safety i validacije.
 */
public record AdminDestinationRequest(
        @Size(max = 200, message = "Naziv destinacije ne sme biti duži od 200 karaktera")
        String destination
) {}
