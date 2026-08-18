package com.escapii.service.email.impl;

import com.escapii.model.CustomDateInquiry;
import com.escapii.service.email.InquiryEmailService;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryEmailServiceImpl implements InquiryEmailService {

    private final EmailSender emailSender;

    @Value("${app.team-email}")
    private String teamEmail;

    @Override
    @Async
    public void sendTeamAlert(CustomDateInquiry i) {
        String notesRow = (i.getNotes() != null && !i.getNotes().isBlank())
                ? EmailHtmlBuilder.dRow("Napomena", EmailHtmlBuilder.esc(i.getNotes()))
                : "";

        String body = EmailHtmlBuilder.detailsCard("Detalji upita",
                EmailHtmlBuilder.dRow("Aerodrom", EmailHtmlBuilder.esc(i.getAirport())) +
                EmailHtmlBuilder.dRow("Email", "<a href=\"mailto:" + EmailHtmlBuilder.esc(i.getEmail()) + "\" style=\"color:#a85e44;\">" + EmailHtmlBuilder.esc(i.getEmail()) + "</a>") +
                EmailHtmlBuilder.dRow("Putnici", String.valueOf(i.getTravelers())) +
                EmailHtmlBuilder.dRow("Željeni datum", String.valueOf(i.getDesiredDepartureDate())) +
                EmailHtmlBuilder.dRow("Noći", i.getNights() + " noći") +
                notesRow,
                "#a85e44");

        String html = EmailHtmlBuilder.wrapBase(
                "#a85e44", "",
                EmailHtmlBuilder.statusBadge("Nov upit", "blue"),
                "Prilagođeni termin #" + i.getId(),
                i.getAirport() + " · " + i.getTravelers() + " putnik/a",
                "",
                body,
                "<strong style=\"color:#1a1410;\">escapii</strong> · Interno obaveštenje",
                false
        );

        boolean ok = emailSender.send(teamEmail, "📅 Nov prilagođeni upit #" + i.getId(), html);
        if (!ok) log.warn("[Inquiry] Tim notifikacija nije poslata za upit id={}", i.getId());
    }
}
