package com.escapii.service.email;

import com.escapii.model.Booking;

public interface ConfirmationDocumentEmailService {
    /**
     * Šalje korisniku zvanični dokument rezervacije (PDF od partnerske agencije)
     * kao prilog. Poziva se tek pošto je korisnik potvrdio da je video reveal.
     */
    void sendConfirmationDocument(Booking booking);
}
