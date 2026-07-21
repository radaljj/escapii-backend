package com.escapii.service.email.impl;

import com.escapii.service.email.LaunchWelcomeEmailService;
import com.escapii.service.email.core.EmailHtmlBuilder;
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

    @Value("${app.team-email}")
    private String teamEmail;

    @Override
    @Async
    public void sendWelcome(String email) {
        String body =
            "<p style=\"font-size:15px;line-height:1.7;color:#3d2e1a;margin:0 0 18px;\">Hej,</p>" +

            "<p style=\"font-size:15px;line-height:1.8;color:#3d2e1a;margin:0 0 18px;\">" +
            "Tvoj mejl je sad zvanično u našoj bazi." +
            "</p>" +

            "<p style=\"font-size:15px;line-height:1.8;color:#3d2e1a;margin:0 0 18px;\">" +
            "Radimo vredno na poslednjim koracima prve platforme za putovanja iznenađenja u Srbiji, " +
            "i dajemo sve od sebe da budemo spremni za poletanje što pre." +
            "</p>" +

            "<p style=\"font-size:15px;line-height:1.8;color:#3d2e1a;margin:0 0 18px;\">" +
            "Kad se to desi, <strong style=\"color:#a85e44;\">ti ćeš biti među prvima koji će saznati</strong> " +
            "i imaćeš pristup terminima za putovanja iznenađenja pre svih ostalih." +
            "</p>" +

            "<p style=\"font-size:15px;line-height:1.8;color:#3d2e1a;margin:0 0 22px;\">" +
            "Družimo se od septembra, i iznenađenja koja ti spremamo su drugačija od svega " +
            "što si do sada doživeo/la." +
            "</p>" +

            "<div style=\"height:1px;background:#ebe1cf;margin:0 0 22px;\"></div>" +

            "<p style=\"font-size:15px;line-height:1.8;color:#3d2e1a;margin:0;\">" +
            "Vidimo se uskoro. 🛫<br>" +
            "<strong style=\"color:#1a1410;\">Tim Escapii</strong>" +
            "</p>";

        String html = EmailHtmlBuilder.wrapBase(
            "#a85e44", "",
            EmailHtmlBuilder.statusBadge("Uskoro", "orange"),
            "Escapii uskoro stiže! 🚀",
            "",
            "",
            body,
            EmailHtmlBuilder.customerFooter(teamEmail),
            false,
            "Hvala što si tu. Obećavamo ti da ćeš saznati među prvima kad poletimo."
        );

        boolean ok = emailSender.send(email, "Escapii uskoro stiže! 🚀", html);
        if (!ok) {
            log.warn("[LaunchWelcome] Email nije poslat na {}", LogUtils.maskEmail(email));
        }
    }
}
