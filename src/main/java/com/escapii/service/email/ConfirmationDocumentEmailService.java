package com.escapii.service.email;

import com.escapii.model.Booking;

public interface ConfirmationDocumentEmailService {
    /**
     * Šalje korisniku zvanični dokument rezervacije (PDF od partnerske agencije)
     * kao prilog. Poziva se tek pošto je korisnik potvrdio da je video reveal.
     */
    /** @return true ako je mejl stvarno otišao - pozivalac tek tada sme da upiše
     *  confirmationSentAt, inače panel prikazuje "poslato" za mejl koji nije stigao. */
    boolean sendConfirmationDocument(Booking booking);
}
