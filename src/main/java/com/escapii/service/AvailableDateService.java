package com.escapii.service;

import com.escapii.model.AvailableDate;

import java.util.List;

public interface AvailableDateService {

    /** Aktivni termini za dati aerodrom polaska, sortirani po datumu. */
    List<AvailableDate> getActiveDatesByAirport(String airport);

    /**
     * Pronalazi privatni termin po tokenu.
     * Baca 404 ako token ne postoji, ili 410 ako je link istekao.
     */
    AvailableDate getPrivateDateByToken(String token);

    /**
     * Pravi termin privatnim — generiše token, postavlja availableSlots i expiresAt.
     * Koristi se iz admin panela kada admin prihvati upit i hoće da pošalje link.
     */
    /**
     * @param pricePerPerson Ako nije null, prepisuje basePrice termina (cena iz upita / broj putnika).
     */
    AvailableDate makePrivate(Long dateId, int travelers, int expiresInHours, Integer pricePerPerson);
}
