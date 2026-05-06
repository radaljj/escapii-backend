package com.escapii.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO za ažuriranje interne napomene rezervacije.
 * Koristi se umesto sirove Map&lt;String, String&gt; radi type-safety i validacije.
 */
public record AdminNotesRequest(
        @Size(max = 3000, message = "Napomena ne sme biti duža od 3000 karaktera")
        String adminNotes
) {}
