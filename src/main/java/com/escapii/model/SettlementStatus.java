package com.escapii.model;

/**
 * Stanje obracuna izmedju Escapii-ja i partnerske agencije za jednu rezervaciju.
 *
 * Odvojeno od {@link BookingStatus} - booking moze biti CONFIRMED a settlement
 * jos uvek NEEDS_COSTS jer admin nije uneo troskove aviona/hotela.
 *
 * <p><b>Prelazi (dozvoljeni):</b>
 * <pre>
 *   NEEDS_COSTS ⇄ READY_FOR_INVOICE   (izvedeno iz kalkulatora, ne rucno)
 *   READY_FOR_INVOICE  → INVOICED     (finalize - dodeljuje broj fakture)
 *   INVOICED           → PAID         (agencija je uplatila)
 *   INVOICED           → VOIDED       (storno pre uplate)
 *   PAID               → INVOICED     (rollback: uplata bila greska)
 * </pre>
 *
 * <p><b>Sto nije dozvoljeno:</b> NEEDS↔READY rucno preko API-ja (izvedeno je iz
 * kalkulatora); INVOICED → READY (broj fakture je audit trag, storno mora ostati
 * vidljiv sa brojem koji je bio dodeljen - to je VOIDED); PAID → VOIDED (mora
 * prvo natrag u INVOICED).
 *
 * <p><b>Storno vs rollback:</b> {@link #VOIDED} znaci "faktura je izdata pa
 * ponistena" - broj ostaje na bookingu, agencija je videla da je ponistena.
 * INVOICED→PAID→INVOICED je samo ispravka gresak u knjizenju uplate.
 */
public enum SettlementStatus {

    /** Rezervacija je aktivna, ali admin jos nije uneo agencyCost za sve 50/50 stavke. */
    NEEDS_COSTS,

    /** Svi troskovi uneseni, kalkulator vraca validan iznos. Faktura moze da se generise. */
    READY_FOR_INVOICE,

    /** Escapii je poslao fakturu agenciji. Booking je zakljucan protiv promene troskova. */
    INVOICED,

    /** Agencija je uplatila fakturu. */
    PAID,

    /** Faktura je bila izdata pa ponistena. Broj fakture, {@code agencyInvoicedAt}
     *  i {@code voidedAt} ostaju na bookingu (audit). Booking je izvan aktivnog toka
     *  obracuna; nova faktura zahteva rucnu intervenciju (novi ref ili re-CREATE). */
    VOIDED
}
