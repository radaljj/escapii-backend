package com.escapii.model;

/**
 * Kako se marza pojedinacne stavke deli izmedju Escapii-ja i partnerske agencije.
 *
 * Fiksno odredjena tipom stavke (vidi {@link ItemType}) - ne moze se menjati na
 * bookingu bez menjanja poslovnog pravila.
 */
public enum AllocationType {

    /** Escapii i agencija dele maržu 50/50. Zahteva unos agencyCost pre fakturisanja. */
    MARGIN_50_50,

    /** Stavka je 100% Escapii prihod. Agencija ne snosi trosak, ne dobija maržu. */
    ESCAPII_100,

    /** Rezervisano za buducnost — trenutno nema stavki koje pripadaju 100% agenciji. */
    AGENCY_100
}
