package com.escapii.service;

import com.escapii.model.WaitlistEntry;

import java.util.List;
import java.util.Map;

public interface WaitlistService {

    /** Vraća true ako je korisnik već pretplaćen, false ako je sačuvan i email poslat. */
    boolean subscribe(String email, String airport);

    /** Pregled svih čekajućih po aerodromu sa brojevima. */
    Map<String, Object> getWaitlistSummary();

    /** Šalje notifikacije svim čekajućima za dati aerodrom i briše ih sa liste. */
    int notifyAndClear(String airport);
}
