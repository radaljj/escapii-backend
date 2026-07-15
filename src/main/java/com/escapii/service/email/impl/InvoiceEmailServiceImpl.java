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

        String body =
            "<p style=\"font-size:15px;line-height:1.6;color:#3d2e1a;margin:0 0 22px;\">" +
            salutation + " " + EmailHtmlBuilder.esc(booking.getFirstName()) + ", " +
            "u prilogu se nalazi profaktura za tvoju Escapii rezervaciju. " +
            "Uplatom u roku navedenom na dokumentu potvrđuješ svoju rezervaciju." +
            "</p>" +
            EmailHtmlBuilder.detailsCard("Detalji rezervacije",
                EmailHtmlBuilder.dRow("Rezervacija", "<strong>" + EmailHtmlBuilder.esc(booking.getBookingRef()) + "</strong>") +
                EmailHtmlBuilder.dRow("Broj fakture", EmailHtmlBuilder.esc(invoiceNumber)) +
                EmailHtmlBuilder.dRow("Iznos", "<strong style=\"color:#a85e44;\">" + booking.getTotalPriceAll() + " EUR</strong>") +
                (booking.getVoucherDiscount() != null && booking.getVoucherDiscount() > 0
                    ? EmailHtmlBuilder.dRow("Vaučer popust", "<span style=\"color:#2e7d4a;\">−" + booking.getVoucherDiscount() + " EUR</span>")
                    : ""),
                "#a85e44") +
            "<p style=\"font-size:13px;color:#6b5d4f;line-height:1.7;margin:16px 0 0;\">" +
            "Nakon uplate, potvrdu transakcije pošalji nam na <a href=\"mailto:" + EmailHtmlBuilder.esc(teamEmail) +
            "\" style=\"color:#a85e44;font-weight:600;\">" + EmailHtmlBuilder.esc(teamEmail) + "</a>. " +
            "Detalji plaćanja (uključujući IPS QR kod za mobilno bankarstvo) nalaze se u prilogu." +
            "</p>";

        String html = EmailHtmlBuilder.wrapBase(
            "#a85e44", "",
            EmailHtmlBuilder.statusBadge("Profaktura", "orange"),
            "Profaktura za tvoju rezervaciju",
            "Rezervacija " + booking.getBookingRef(),
            invoiceNumber,
            body,
            EmailHtmlBuilder.customerFooter(teamEmail),
            false
        );

        String attachmentName = "escapii-profaktura-" + invoiceNumber + ".pdf";
        boolean ok = emailSender.sendWithAttachment(
            booking.getEmail(),
            "📄 Profaktura " + invoiceNumber + " · Escapii rezervacija " + booking.getBookingRef(),
            html,
            attachmentName,
            pdfBytes,
            "application/pdf"
        );
        if (!ok) log.warn("[Invoice] PDF email nije poslat za rezervaciju {}", booking.getBookingRef());
    }
}
