package com.escapii.model;

/**
 * Stanje obracuna izmedju Escapii-ja i partnerske agencije za jednu rezervaciju.
 *
 * Odvojeno od {@link BookingStatus} - booking moze biti CONFIRMED a settlement
 * jos uvek NEEDS_COSTS jer admin nije uneo troskove aviona/hotela.
 *
 * Prelazi: NEEDS_COSTS → READY_FOR_INVOICE → INVOICED → PAID
 * Otkazana rezervacija ostavlja settlement u zatecenom stanju, ali ne moze biti
 * finalizovana; korekcija zahteva rucnu intervenciju.
 */
public enum SettlementStatus {

    /** Rezervacija je aktivna, ali admin jos nije uneo agencyCost za sve 50/50 stavke. */
    NEEDS_COSTS,

    /** Svi troskovi uneseni, kalkulator vraca validan iznos. Faktura moze da se generise. */
    READY_FOR_INVOICE,

    /** Escapii je poslao fakturu agenciji. Booking je zakljucan protiv promene troskova. */
    INVOICED,

    /** Agencija je uplatila fakturu. */
    PAID
}
