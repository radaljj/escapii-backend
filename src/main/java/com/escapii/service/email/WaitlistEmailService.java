package com.escapii.service.email;

public interface WaitlistEmailService {
    void sendWaitlistConfirmation(String email, String airport);

    /**
     * @return true ako je mejl stvarno otišao - pozivalac (WaitlistServiceImpl)
     *  briše unos sa liste čekanja samo na osnovu ovog ishoda, inače bi osoba
     *  bila trajno uklonjena i pored propalog slanja, bez ikakvog "pošalji ponovo".
     */
    boolean sendWaitlistNotification(String email, String airport);
}
