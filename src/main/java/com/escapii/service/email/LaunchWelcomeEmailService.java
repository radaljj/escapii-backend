package com.escapii.service.email;

/**
 * PRIVREMENO - deo coming-soon toka, briše se kad sajt ode live
 * (zajedno sa LaunchSubscriber entitetom, /api/launch-notify endpointom
 * i coming-soon.php stranicom).
 */
public interface LaunchWelcomeEmailService {

    /** Potvrda korisniku koji je ostavio mejl na coming-soon stranici. */
    void sendWelcome(String email);
}
