package com.escapii.model;

/**
 * Tipovi finansijskih stavki koje ulaze u obracun sa partnerskom agencijom.
 *
 * Svaki tip ima fiksnu {@link AllocationType} - alokacija nije stvar podataka
 * nego poslovnog pravila, i menja se samo kroz ovaj enum. Frontend/admin
 * ne biraju alokaciju, ona je izvedena.
 */
public enum ItemType {

    /** Cena aviona + hotel, jedina stavka koja ima podelu agencijskog troska
     *  na dve komponente (flight + hotel). Ostale stavke imaju jedan agency_cost. */
    BASE_PACKAGE          (AllocationType.MARGIN_50_50),
    ACCOMMODATION_UPGRADE (AllocationType.MARGIN_50_50),
    BREAKFAST             (AllocationType.MARGIN_50_50),
    SEATS_TOGETHER        (AllocationType.MARGIN_50_50),
    CABIN_SUITCASE        (AllocationType.MARGIN_50_50),
    /** Trenutno onemogucen dodatak (feature flag na frontendu). Ostaje u enumu
     *  radi istorijskih rezervacija i eventualnog ponovnog ukljucivanja. */
    INSURANCE             (AllocationType.MARGIN_50_50),
    /** Doplata za jednog putnika (nema soba za dva) - naplacuje Escapii, agencija
     *  ne snosi nikakav trosak jer je vec ukljucen u cenu osnovnog paketa. */
    SOLO_SURCHARGE        (AllocationType.ESCAPII_100),
    DESTINATION_EXCLUSIONS(AllocationType.ESCAPII_100),
    /** Fizicka kutija je Escapii proizvod. Interni trosak (stampa, dostava) je
     *  poseban i ne umanjuje ono sto Escapii fakturise agenciji. */
    REVEAL_BOX            (AllocationType.ESCAPII_100);

    private final AllocationType allocationType;

    ItemType(AllocationType allocationType) {
        this.allocationType = allocationType;
    }

    public AllocationType getAllocationType() {
        return allocationType;
    }

    public boolean isSharedMargin() {
        return allocationType == AllocationType.MARGIN_50_50;
    }

    public boolean isEscapiiOnly() {
        return allocationType == AllocationType.ESCAPII_100;
    }
}
