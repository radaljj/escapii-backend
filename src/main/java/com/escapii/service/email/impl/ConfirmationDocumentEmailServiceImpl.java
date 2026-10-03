package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.service.email.ConfirmationDocumentEmailService;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.impl.TravelAddonsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmationDocumentEmailServiceImpl implements ConfirmationDocumentEmailService {

    private final EmailSender emailSender;
    /** "Zdravo, Uroše," - vokativ imena; nikad ne baca, na sve vraca nominativ. */
    private final com.escapii.service.VocativeService vocativeService;
    /** Partnerski linkovi (eSIM, ture, prtljag) za dodeljenu destinaciju - isti izvor kao popup posle reveala. */
    private final TravelAddonsService travelAddonsService;

    /** Javna kontakt adresa koju kupac vidi (nije adresa na koju tim prima). */
    @Value("${app.contact-email}")
    private String contactEmail;

    @Override
    public boolean sendConfirmationDocument(Booking booking) {
        String salutation = EmailHtmlBuilder.salutation();
        var date = booking.getSelectedDate();

        String depStr = date.getDepartureDate().format(EmailHtmlBuilder.DATE_FMT);
        String retStr = date.getReturnDate() != null ? date.getReturnDate().format(EmailHtmlBuilder.DATE_FMT) : "-";
        int nights = date.getNumberOfNights();
        int travelers = booking.getNumberOfTravelers() != null ? booking.getNumberOfTravelers() : 1;
        String airline = booking.getAirlineName() != null && !booking.getAirlineName().isBlank()
                ? booking.getAirlineName() : "-";

        String body =
            "<p style=\"font-size:15px;line-height:1.7;color:#3d2e1a;margin:0 0 18px;\">" +
            salutation + " " + EmailHtmlBuilder.esc(vocativeService.vocative(booking.travellerFirstName())) + ",</p>" +

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
            "<a href=\"mailto:" + EmailHtmlBuilder.esc(contactEmail) + "\" style=\"color:#a85e44;font-weight:600;\">" +
            EmailHtmlBuilder.esc(contactEmail) + "</a>." +
            "</p>" +

            // Blok sa partnerskim linkovima - prazan string kad za destinaciju nema nijednog linka.
            addonsBlock(travelAddonsService.linksFor(booking.getAssignedDestination()));

        String html = EmailHtmlBuilder.wrapBase(
            "#2D5F6B", "",
            EmailHtmlBuilder.statusBadge("Podaci rezervacije", "blue"),
            "Tvoji detalji putovanja su stigli!",
            "Rezervacija " + booking.getBookingRef() + " · " + booking.getAssignedDestination(),
            "",
            body,
            EmailHtmlBuilder.customerFooter(contactEmail),
            false
        );

        String rawName = booking.getConfirmationDocumentFilename();
        String attachmentName = (rawName != null && !rawName.isBlank())
                ? rawName : "escapii-rezervacija-" + booking.getBookingRef() + ".pdf";

        boolean ok = emailSender.sendWithAttachment(
            // Putni dokumenti idu onome ko putuje - kod poklona obdarenom.
            booking.travellerEmail(),
            "Zvanični podaci tvoje rezervacije · Escapii",
            html,
            attachmentName,
            booking.getConfirmationDocument(),
            "application/pdf"
        );
        if (!ok) log.warn("[ConfirmationDocument] Email nije poslat za rezervaciju {}", booking.getBookingRef());
        return ok;
    }

    /**
     * Blok "Još par sitnica pre puta" - partnerski linkovi za destinaciju.
     *
     * <p>Mapa sadrži SAMO linkove koji postoje (ključevi {@code esim}, {@code tours},
     * {@code luggage}); prazna mapa znači da bloka nema i mejl ostaje kakav je bio.
     * Redosled je uvek eSIM, ture, prtljag - nezavisno od redosleda u mapi.
     * Tekst namerno kaže "kupuješ" - eSIM nije poklon uz putovanje, nego partnerska ponuda.
     */
    static String addonsBlock(Map<String, String> links) {
        if (links == null || links.isEmpty()) return "";

        StringBuilder rows = new StringBuilder();
        if (links.containsKey("esim")) {
            rows.append(addonRow("Internet od trenutka kad sletiš",
                "eSIM bez rominga. Kupuješ ga kod Holafly-ja, kod ESCAPII daje 5% popusta i obračuna se sam preko linka.",
                "Pogledaj eSIM", links.get("esim")));
        }
        if (links.containsKey("tours")) {
            rows.append(addonRow("Ulaznice i ture",
                "Za najtraženija mesta karte nestanu danima ranije. Većina se otkazuje besplatno do 24h pre.",
                "Pogledaj ture", links.get("tours")));
        }
        if (links.containsKey("luggage")) {
            rows.append(addonRow("Čuvanje prtljaga",
                "Odjava u 10, let u 22. Ostavi kofer u lokalu na par sati i iskoristi poslednji dan.",
                "Pogledaj lokacije", links.get("luggage")));
        }
        if (rows.isEmpty()) return "";

        return
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:28px 0 0;\">" +
              "<tr><td style=\"border-top:1px solid #ebe1cf;padding:22px 0 0;\">" +
                "<p style=\"margin:0 0 6px;font-size:10px;font-weight:800;letter-spacing:2px;text-transform:uppercase;color:#a89888;\">" +
                "Još par sitnica pre puta</p>" +
                "<p style=\"margin:0 0 14px;font-size:14px;line-height:1.8;color:#3d2e1a;\">" +
                "Ništa od ovoga nije obavezno. Ali većina reši bar jednu stvar unapred - i ne požali.</p>" +
                "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">" + rows + "</table>" +
                "<p style=\"margin:12px 0 0;font-size:11px;line-height:1.6;color:#a89888;\">" +
                "Partnerski linkovi. Kupuješ direktno kod partnera, bez dodatnih troškova za tebe.</p>" +
              "</td></tr>" +
            "</table>";
    }

    /** Jedan red bloka: naslov, rečenica-dve i link ka partneru. URL ide kroz esc jer stoji u atributu. */
    private static String addonRow(String naslov, String tekst, String linkTekst, String url) {
        return
            "<tr><td style=\"padding:12px 0;border-bottom:1px solid #ebe1cf;\">" +
              "<div style=\"font-size:14px;font-weight:600;color:#1a1410;margin-bottom:2px;\">" + naslov + "</div>" +
              "<div style=\"font-size:13px;line-height:1.55;color:#6b5d4f;margin-bottom:6px;\">" + tekst + "</div>" +
              "<a href=\"" + EmailHtmlBuilder.esc(url) + "\" style=\"font-size:13px;color:#a85e44;font-weight:600;text-decoration:none;\">" +
              linkTekst + " &rarr;</a>" +
            "</td></tr>";
    }
}
