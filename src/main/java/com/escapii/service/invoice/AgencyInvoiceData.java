package com.escapii.service.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Podaci za PDF zbirne fakture agenciji. Podaci firme dolaze iz {@code app.company.*};
 * dok firma nije otvorena tamo stoje podrazumevane nule, i tada se PIB, matični broj i
 * račun NE prikazuju (umesto računa ide napomena da podaci za uplatu stižu naknadno).
 */
public record AgencyInvoiceData(
        String invoiceNumber,
        LocalDate issuedAt,
        LocalDate dueDate,
        LocalDate periodFrom,
        LocalDate periodTo,
        String agencyName,
        String agencyContact,
        String agencyEmail,
        String description,
        BigDecimal amount,
        String companyName,
        String companyAddress,
        String companyPib,
        String companyMb,
        String companyAccount,
        String companyBank,
        String companyEmail,
        String companyWebsite
) {
    public boolean hasPib()     { return !placeholder(companyPib); }
    public boolean hasMb()      { return !placeholder(companyMb); }
    public boolean hasAccount() { return !placeholder(companyAccount); }
    public boolean hasBank()    { return !placeholder(companyBank); }

    /** „1.234,50" - srpski zapis, dve decimale. */
    public String amountFormatted() {
        return String.format(new Locale("sr", "RS"), "%,.2f", amount == null ? BigDecimal.ZERO : amount);
    }

    /** Podrazumevane vrednosti iz application.properties: prazno, same nule i crte, ili reč "placeholder". */
    static boolean placeholder(String v) {
        if (v == null || v.isBlank()) return true;
        String t = v.trim().toLowerCase(Locale.ROOT);
        return t.contains("placeholder") || t.chars().allMatch(c -> c == '0' || c == '-' || c == ' ');
    }
}
