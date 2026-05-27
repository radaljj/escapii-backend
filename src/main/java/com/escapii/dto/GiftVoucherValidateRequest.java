package com.escapii.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Zahtev za validaciju vaučer koda.
 * Korisnik unosi kod na /poklon stranici ili u booking formi.
 */
public record GiftVoucherValidateRequest(

        @NotBlank(message = "Vaučer kod je obavezan")
        @Size(max = 20, message = "Vaučer kod nije validan")
        @Pattern(regexp = "ESC-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}",
                 message = "Vaučer kod nije u ispravnom formatu")
        String code
) {}
