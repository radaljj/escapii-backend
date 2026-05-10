package com.escapii.model;

public enum InquiryStatus {
    /** Novi upit — čeka pregled. */
    PENDING,
    /** Admin pregledao, u toku je analiza termina. */
    IN_REVIEW,
    /** Privatni termin kreiran i link poslat korisniku. */
    PRIVATE_SENT,
    /** Zatvoren (bez rešenja ili korisnik odustao). */
    CLOSED
}
