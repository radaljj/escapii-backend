package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.service.email.InvoiceEmailService;
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
public class InvoiceEmailServiceImpl implements InvoiceEmailService {

    private final EmailSender emailSender;

    @Value("${app.team-email}")
    private String teamEmail;

    @Override
    @Async("pdfExecutor")
    public void sendInvoiceToClient(Booking booking, byte[] pdfBytes, String invoiceNumber) {
        String salutation = EmailHtmlBuilder.salutation(booking);

        int total = booking.getTotalPriceAll()
                - (booking.getVoucherDiscount() != null ? booking.getVoucherDiscount() : 0);

        String body =
            "<p style=\"font-size:15px;line-height:1.7;color:#3d2e1a;margin:0 0 18px;\">" +
            salutation + " " + EmailHtmlBuilder.esc(booking.getFirstName()) + ",</p>" +

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
            "<a href=\"mailto:" + EmailHtmlBuilder.esc(teamEmail) + "\" style=\"color:#a85e44;font-weight:600;\">" +
            EmailHtmlBuilder.esc(teamEmail) + "</a> i odmah ćemo potvrditi tvoje mesto. " +
            "Ako imaš bilo kakvo pitanje — tu smo!" +
            "</p>";

        String html = EmailHtmlBuilder.wrapBase(
            "#a85e44", "",
            EmailHtmlBuilder.statusBadge("Profaktura", "orange"),
            "Tvoja Escapii rezervacija čeka na uplatu",
            "Rezervacija " + booking.getBookingRef(),
            invoiceNumber,
            body,
            EmailHtmlBuilder.customerFooter(teamEmail),
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
    }
}
