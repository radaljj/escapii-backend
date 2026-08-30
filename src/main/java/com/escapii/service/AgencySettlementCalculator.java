package com.escapii.service;

import com.escapii.dto.AgencySettlementResponse;
import com.escapii.model.Booking;

/**
 * Jedini izvor finansijskih formula za obracun Escapii ↔ agencija.
 *
 * <p>Sve poslovne odluke - podela marze 50/50, klasifikacija stavki po
 * {@link com.escapii.model.AllocationType}, vaucer kao unapred naplacen novac,
 * pravilo za neparni cent - moraju biti implementirane iskljucivo ovde.
 * Frontend i admin panel ne smeju racunati nista sami; oni samo prikazuju
 * {@link AgencySettlementResponse}.
 */
public interface AgencySettlementCalculator {

    /**
     * Obracun za jednu rezervaciju. Radi i za PENDING (kao preview) i za CONFIRMED
     * (kao osnov za fakturu). Ne menja stanje - samo cita snapshot iz financialItems.
     */
    AgencySettlementResponse calculate(Booking booking);
}
