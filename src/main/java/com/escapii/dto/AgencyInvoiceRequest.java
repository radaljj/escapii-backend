package com.escapii.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Telo za POST /api/admin/agencies/{id}/invoices - jedina stavka fakture. */
public record AgencyInvoiceRequest(
        @NotBlank(message = "Stavka fakture je obavezna")
        @Size(max = 500, message = "Stavka fakture može imati najviše 500 znakova")
        String description
) {}
