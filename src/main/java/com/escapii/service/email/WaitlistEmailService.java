package com.escapii.service.email;

public interface WaitlistEmailService {
    void sendWaitlistConfirmation(String email, String airport);
    void sendWaitlistNotification(String email, String airport);
}
