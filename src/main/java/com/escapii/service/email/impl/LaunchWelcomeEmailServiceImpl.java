package com.escapii.service.email.impl;

import com.escapii.service.email.LaunchWelcomeEmailService;
import com.escapii.service.email.core.EmailSender;
import com.escapii.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * PRIVREMENO - deo coming-soon toka, briše se kad sajt ode live.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaunchWelcomeEmailServiceImpl implements LaunchWelcomeEmailService {

    private final EmailSender emailSender;


    /** Javna kontakt adresa koju kupac vidi (nije adresa na koju tim prima). */
    @Value("${app.contact-email}")
    private String contactEmail;

    @Override
    @Async
    public void sendWelcome(String email) {
        String html = loadTemplate("uskoro-stize.html")
            .replace("{{SENDER_EMAIL}}", contactEmail);

        boolean ok = emailSender.send(email, "Escapii uskoro stiže! 🚀", html);
        if (!ok) {
            log.warn("[LaunchWelcome] Email nije poslat na {}", LogUtils.maskEmail(email));
        }
    }

    private static String loadTemplate(String filename) {
        try (var is = LaunchWelcomeEmailServiceImpl.class.getResourceAsStream("/email/" + filename)) {
            if (is == null) throw new IllegalStateException("Email template nije pronađen: /email/" + filename);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Greška pri čitanju email template-a: " + filename, e);
        }
    }
}
