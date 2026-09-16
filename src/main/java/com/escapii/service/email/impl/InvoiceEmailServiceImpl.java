package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.model.GiftVoucher;
import com.escapii.service.email.InvoiceEmailService;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceEmailServiceImpl implements InvoiceEmailService {

    private final EmailSender emailSender;
    /** "Zdravo, Uroše," - vokativ imena; nikad ne baca, na sve vraca nominativ. */
    private final com.escapii.service.VocativeService vocativeService;


    /** Javna kontakt adresa koju kupac vidi (nije adresa na koju tim prima). */
    @Value("${app.contact-email}")
    private String contactEmail;

    @Override
    public boolean sendInvoiceToClient(Booking booking, byte[] pdfBytes, String invoiceNumber) {
        String salutation = EmailHtmlBuilder.salutation();

        int total = booking.getTotalPriceAll();

        String body =
            "<p style=\"font-size:15px;line-height:1.7;color:#3d2e1a;margin:0 0 18px;\">" +
            salutation + " " + EmailHtmlBuilder.esc(vocativeService.vocative(booking.getFirstName())) + ",</p>" +

            "<p style=\"font-size:14px;line-height:1.8;color:#3d2e1a;margin:0 0 18px;\">" +
            "hvala na rezervaciji! Jedva čekamo da te pošaljemo na put 🌍" +
            "</p>" +

            "<p style=\"font-size:14px;line-height:1.8;color:#3d2e1a;margin:0 0 22px;\">" +
            "U prilogu se nalazi profaktura sa svim detaljima uplate. " +
            "Iznos se plaća u RSD po <strong>srednjem kursu NBS na dan uplate</strong> — " +
            "u PDF-u ćeš naći broj računa i IPS QR kod koji možeš skenirati direktno u banking aplikaciji." +
            "</p>" +

            EmailHtmlBuilder.detailsCard("Detalji rezervacije",
                EmailHtmlBuilder.dRow("Rezervacija", "<strong>" + EmailHtmlBuilder.esc(booking.getBookingRef()) + "</strong>") +
                EmailHtmlBuilder.dRow("Profaktura br.", EmailHtmlBuilder.esc(invoiceNumber)) +
                EmailHtmlBuilder.dRow("Iznos", "<strong style=\"color:#a85e44;\">" + total + " EUR</strong>") +
                (booking.getVoucherDiscount() != null && booking.getVoucherDiscount() > 0
                    ? EmailHtmlBuilder.dRow("Uključen vaučer popust", "<span style=\"color:#2e7d4a;\">−" + booking.getVoucherDiscount() + " EUR</span>")
                    : ""),
                "#a85e44") +

            "<p style=\"font-size:14px;line-height:1.8;color:#3d2e1a;margin:18px 0 0;\">" +
            "Kada uplatiš, pošalji nam potvrdu transakcije na " +
            "<a href=\"mailto:" + EmailHtmlBuilder.esc(contactEmail) + "\" style=\"color:#a85e44;font-weight:600;\">" +
            EmailHtmlBuilder.esc(contactEmail) + "</a> i odmah ćemo potvrditi tvoje mesto. " +
            "Ako imaš bilo kakvo pitanje — tu smo!" +
            "</p>";

        String html = EmailHtmlBuilder.wrapBase(
            "#a85e44", "",
            EmailHtmlBuilder.statusBadge("Profaktura", "orange"),
            "Tvoja Escapii rezervacija čeka na uplatu",
            "Rezervacija " + booking.getBookingRef(),
            invoiceNumber,
            body,
            EmailHtmlBuilder.customerFooter(contactEmail),
            false
        );

        String attachmentName = "escapii-profaktura-" + invoiceNumber + ".pdf";
        boolean ok = emailSender.sendWithAttachment(
            booking.getEmail(),
            "Profaktura za rezervaciju " + booking.getBookingRef() + " · Escapii",
            html,
            attachmentName,
            pdfBytes,
            "application/pdf"
        );
        if (!ok) log.warn("[Invoice] PDF email nije poslat za rezervaciju {}", booking.getBookingRef());
        return ok;
    }

    @Override
    public boolean sendVoucherInvoiceToClient(GiftVoucher voucher, byte[] pdfBytes, String invoiceNumber) {
        int amount = voucher.getAmount().intValue();

        String body =
            "<p style=\"font-size:15px;line-height:1.7;color:#3d2e1a;margin:0 0 18px;\">" +
            // buyerName je "Ime Prezime"; vocative() sam uzima prvo ime, pa u pozdravu
            // ostaje samo ono - "Zdravo, Uroše," a ne "Zdravo, Uroše Petrović,".
            EmailHtmlBuilder.salutation() + " " + EmailHtmlBuilder.esc(voucher.getBuyerName() != null ? vocativeService.vocative(voucher.getBuyerName()) : "kupče") + ",</p>" +

            "<p style=\"font-size:14px;line-height:1.8;color:#3d2e1a;margin:0 0 18px;\">" +
            "hvala na kupovini Escapii poklon vaučera! U prilogu se nalazi profaktura sa detaljima za uplatu." +
            "</p>" +

            "<p style=\"font-size:14px;line-height:1.8;color:#3d2e1a;margin:0 0 22px;\">" +
            "Iznos se plaća u RSD po <strong>srednjem kursu NBS na dan uplate</strong> — " +
            "u PDF-u ćeš naći broj računa i IPS QR kod." +
            "</p>" +

            EmailHtmlBuilder.detailsCard("Detalji vaučera",
                EmailHtmlBuilder.dRow("Profaktura br.", EmailHtmlBuilder.esc(invoiceNumber)) +
                EmailHtmlBuilder.dRow("Iznos vaučera", "<strong style=\"color:#a85e44;\">" + amount + " EUR</strong>"),
                "#a85e44") +

            "<p style=\"font-size:14px;line-height:1.8;color:#3d2e1a;margin:18px 0 0;\">" +
            "Kada uplatiš, pošalji nam potvrdu transakcije na " +
            "<a href=\"mailto:" + EmailHtmlBuilder.esc(contactEmail) + "\" style=\"color:#a85e44;font-weight:600;\">" +
            EmailHtmlBuilder.esc(contactEmail) + "</a> i odmah ćemo aktivirati tvoj vaučer." +
            "</p>";

        String html = EmailHtmlBuilder.wrapBase(
            "#a85e44", "",
            EmailHtmlBuilder.statusBadge("Poklon vaučer", "orange"),
            "Tvoj Escapii poklon vaučer čeka na uplatu",
            "Vaučer · " + amount + " EUR",
            invoiceNumber,
            body,
            EmailHtmlBuilder.customerFooter(contactEmail),
            false
        );

        String attachmentName = "escapii-profaktura-" + invoiceNumber + ".pdf";
        boolean ok = emailSender.sendWithAttachment(
            voucher.getBuyerEmail(),
            "Profaktura za poklon vaučer · Escapii",
            html,
            attachmentName,
            pdfBytes,
            "application/pdf"
        );
        if (!ok) log.warn("[Invoice] PDF email nije poslat za vaučer #{}", voucher.getId());
        return ok;
    }

    private static final java.time.format.DateTimeFormatter DATUM =
            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy.");

    @Override
    public boolean sendAgencyInvoice(com.escapii.model.AgencyInvoice inv, byte[] pdfBytes) {
        String broj   = inv.getInvoiceNumber();
        String iznos  = String.format(new java.util.Locale("sr", "RS"), "%,.2f", inv.getAmount()) + " EUR";
        String period = inv.getPeriodFrom() != null && inv.getPeriodTo() != null
                ? DATUM.format(inv.getPeriodFrom()) + " – " + DATUM.format(inv.getPeriodTo()) : "";
        String rok    = inv.getDueDate() != null ? DATUM.format(inv.getDueDate()) : "";

        String body =
            "<p style=\"font-size:15px;line-height:1.7;color:#3d2e1a;margin:0 0 18px;\">Poštovani,</p>" +
            "<p style=\"font-size:14px;line-height:1.8;color:#3d2e1a;margin:0 0 18px;\">" +
            "u prilogu je faktura broj <strong>" + EmailHtmlBuilder.esc(broj) + "</strong>" +
            (period.isEmpty() ? "" : " za period " + EmailHtmlBuilder.esc(period)) + "." +
            (rok.isEmpty() ? "" : " Rok plaćanja je <strong>" + EmailHtmlBuilder.esc(rok) + "</strong>.") +
            "</p>" +
            EmailHtmlBuilder.detailsCard("Detalji fakture",
                EmailHtmlBuilder.dRow("Broj fakture", "<strong>" + EmailHtmlBuilder.esc(broj) + "</strong>") +
                EmailHtmlBuilder.dRow("Stavka", EmailHtmlBuilder.esc(inv.getDescription())) +
                (period.isEmpty() ? "" : EmailHtmlBuilder.dRow("Period", EmailHtmlBuilder.esc(period))) +
                EmailHtmlBuilder.dRow("Iznos", "<strong style=\"color:#a85e44;\">" + EmailHtmlBuilder.esc(iznos) + "</strong>") +
                (rok.isEmpty() ? "" : EmailHtmlBuilder.dRow("Rok plaćanja", EmailHtmlBuilder.esc(rok))),
                "#a85e44") +
            "<p style=\"font-size:14px;line-height:1.8;color:#3d2e1a;margin:18px 0 0;\">" +
            "Za sva pitanja u vezi sa fakturom pišite nam na " +
            "<a href=\"mailto:" + EmailHtmlBuilder.esc(contactEmail) + "\" style=\"color:#a85e44;font-weight:600;\">" +
            EmailHtmlBuilder.esc(contactEmail) + "</a>. Hvala na saradnji!" +
            "</p>";

        String html = EmailHtmlBuilder.wrapBase(
            "#a85e44", "",
            EmailHtmlBuilder.statusBadge("Faktura", "orange"),
            "Faktura " + broj,
            EmailHtmlBuilder.esc(inv.getAgencyName()),
            broj,
            body,
            EmailHtmlBuilder.customerFooter(contactEmail),
            false
        );
        boolean ok = emailSender.sendWithAttachment(
            inv.getAgencyEmail(),
            "Faktura " + broj + " · Escapii",
            html,
            "escapii-faktura-" + broj + ".pdf",
            pdfBytes,
            "application/pdf"
        );
        if (!ok) log.warn("[Invoice] PDF fakture {} nije poslat agenciji {}", broj, inv.getAgencyName());
        return ok;
    }
}
