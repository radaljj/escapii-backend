package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.service.email.ConfirmationDocumentEmailService;
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
public class ConfirmationDocumentEmailServiceImpl implements ConfirmationDocumentEmailService {

    private final EmailSender emailSender;

    @Value("${app.team-email}")
    private String teamEmail;

    @Override
    @Async("pdfExecutor")
    public void sendConfirmationDocument(Booking booking) {
        String salutation = EmailHtmlBuilder.salutation(booking);
        var date = booking.getSelectedDate();

        String depStr = date.getDepartureDate().format(EmailHtmlBuilder.DATE_FMT);
        String retStr = date.getReturnDate() != null ? date.getReturnDate().format(EmailHtmlBuilder.DATE_FMT) : "-";
        int nights = date.getNumberOfNights();
        int travelers = booking.getNumberOfTravelers() != null ? booking.getNumberOfTravelers() : 1;
        String airline = booking.getAirlineName() != null && !booking.getAirlineName().isBlank()
                ? booking.getAirlineName() : "-";

        String body =
            "<p style=\"font-size:15px;line-height:1.7;color:#3d2e1a;margin:0 0 18px;\">" +
            salutation + " " + EmailHtmlBuilder.esc(booking.getFirstName()) + ",</p>" +

            "<p style=\"font-size:14px;line-height:1.8;color:#3d2e1a;margin:0 0 22px;\">" +
            "sad kad znaš svoju destinaciju, evo i zvaničnih podataka tvoje rezervacije - " +
            "u prilogu se nalazi PDF sa detaljima leta i smeštaja koje je naša partnerska agencija potvrdila za tebe." +
            "</p>" +

            EmailHtmlBuilder.detailsCard("Detalji putovanja",
                EmailHtmlBuilder.dRow("Rezervacija", "<strong>" + EmailHtmlBuilder.esc(booking.getBookingRef()) + "</strong>") +
                EmailHtmlBuilder.dRow("Destinacija", "<strong style=\"color:#a85e44;\">" + EmailHtmlBuilder.esc(booking.getAssignedDestination()) + "</strong>") +
                EmailHtmlBuilder.dRow("Polazak", depStr) +
                EmailHtmlBuilder.dRow("Povratak", retStr) +
                EmailHtmlBuilder.dRow("Trajanje", nights + (nights == 1 ? " noć" : " noći")) +
                EmailHtmlBuilder.dRow("Broj putnika", String.valueOf(travelers)) +
                EmailHtmlBuilder.dRow("Avio kompanija", EmailHtmlBuilder.esc(airline)),
                "#a85e44") +

            "<p style=\"font-size:14px;line-height:1.8;color:#3d2e1a;margin:18px 0 0;\">" +
            "Sačuvaj ovaj PDF - sadrži zvanične podatke koji ti mogu zatrebati na aerodromu ili u smeštaju. " +
            "Ako primetiš bilo kakvu grešku u podacima, javi nam se odmah na " +
            "<a href=\"mailto:" + EmailHtmlBuilder.esc(teamEmail) + "\" style=\"color:#a85e44;font-weight:600;\">" +
            EmailHtmlBuilder.esc(teamEmail) + "</a>." +
            "</p>";

        String html = EmailHtmlBuilder.wrapBase(
            "#2D5F6B", "",
            EmailHtmlBuilder.statusBadge("Podaci rezervacije", "blue"),
            "Tvoji zvanični podaci su stigli!",
            "Rezervacija " + booking.getBookingRef() + " · " + booking.getAssignedDestination(),
            "",
            body,
            EmailHtmlBuilder.customerFooter(teamEmail),
            false
        );

        String rawName = booking.getConfirmationDocumentFilename();
        String attachmentName = (rawName != null && !rawName.isBlank())
                ? rawName : "escapii-rezervacija-" + booking.getBookingRef() + ".pdf";

        boolean ok = emailSender.sendWithAttachment(
            booking.getEmail(),
            "📎 Zvanični podaci tvoje rezervacije · Escapii",
            html,
            attachmentName,
            booking.getConfirmationDocument(),
            "application/pdf"
        );
        if (!ok) log.warn("[ConfirmationDocument] Email nije poslat za rezervaciju {}", booking.getBookingRef());
    }
}
