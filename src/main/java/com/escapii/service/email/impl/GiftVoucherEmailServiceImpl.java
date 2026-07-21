package com.escapii.service.email.impl;

import com.escapii.model.GiftVoucher;
import com.escapii.service.email.GiftVoucherEmailService;
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
public class GiftVoucherEmailServiceImpl implements GiftVoucherEmailService {

    private final EmailSender emailSender;

    @Value("${app.team-email}")
    private String teamEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Javna kontakt adresa koju kupac vidi (nije adresa na koju tim prima). */
    @Value("${app.contact-email}")
    private String contactEmail;

    // ── Tim notifikacija (novi upit) ─────────────────────────────────────────

    @Override
    @Async
    public void sendTeamAlert(GiftVoucher v) {
        String buyerNameRow = (v.getBuyerName() != null && !v.getBuyerName().isBlank())
                ? EmailHtmlBuilder.dRow("Ime kupca", EmailHtmlBuilder.esc(v.getBuyerName()))
                : "";
        String messageRow = (v.getGiftMessage() != null && !v.getGiftMessage().isBlank())
                ? EmailHtmlBuilder.dRow("Poruka", EmailHtmlBuilder.esc(v.getGiftMessage()))
                : "";

        String body = EmailHtmlBuilder.detailsCard("Detalji vaučera",
                EmailHtmlBuilder.dRow("Iznos", "<strong style=\"color:#a85e44;\">" + v.getAmount().toPlainString() + " EUR</strong>") +
                EmailHtmlBuilder.dRow("Email kupca", "<a href=\"mailto:" + EmailHtmlBuilder.esc(v.getBuyerEmail()) + "\" style=\"color:#a85e44;\">" + EmailHtmlBuilder.esc(v.getBuyerEmail()) + "</a>") +
                buyerNameRow +
                messageRow,
                "#a85e44") +
                "<p style=\"font-size:13px;color:#6b5d4f;line-height:1.6;margin:0;\">" +
                "Nakon uplate, aktiviraj vaučer u admin panelu — sistem će automatski generisati PDF i poslati ga kupcu." +
                "</p>";

        String html = EmailHtmlBuilder.wrapBase(
                "#a85e44", "",
                EmailHtmlBuilder.statusBadge("Nov vaučer", "green"),
                "Gift vaučer #" + v.getId(),
                v.getAmount().toPlainString() + " EUR · " + v.getBuyerEmail(),
                "",
                body,
                "<strong style=\"color:#1a1410;\">escapii</strong> · Interno obaveštenje",
                false
        );

        boolean ok = emailSender.send(teamEmail,
                "🎁 Nov gift vaučer #" + v.getId() + " - " + v.getAmount().toPlainString() + " EUR",
                html);
        if (!ok) log.warn("[GiftVoucher] Tim notifikacija nije poslata za vaučer id={}", v.getId());
    }

    // ── PDF vaučer kupcu (nakon aktivacije) ─────────────────────────────────

    @Override
    @Async("pdfExecutor")
    public void sendVoucherPdfToBuyer(GiftVoucher v, byte[] pdfBytes) {
        String body =
                "<p style=\"font-size:15px;line-height:1.6;color:#3d2e1a;margin:0 0 22px;\">" +
                "Tvoj poklon vaučer je u prilogu kao PDF — možeš ga odštampati ili prikazati sa telefona." +
                "</p>" +
                EmailHtmlBuilder.detailsCard("Detalji vaučera",
                        EmailHtmlBuilder.dRow("Iznos vaučera", "<strong style=\"color:#a85e44;\">" + v.getAmount().toPlainString() + " EUR</strong>") +
                        EmailHtmlBuilder.dRow("Vaučer kod", "<span style=\"font-family:'Courier New',monospace;letter-spacing:2px;font-weight:700;\">" + v.getCode() + "</span>") +
                        EmailHtmlBuilder.dRow("Važi do", "1 godinu od aktivacije"),
                        "#a85e44") +
                "<p style=\"font-size:14px;color:#6b5d4f;line-height:1.7;margin:0 0 16px;\">" +
                "Kod vaučera unosi se pri rezervaciji putovanja na <a href=\"" + frontendUrl + "\" style=\"color:#a85e44;font-weight:600;\">escapii.rs</a> — " +
                "iznos se automatski odbija od cene putovanja." +
                "</p>" +
                "<p style=\"font-size:14px;color:#6b5d4f;line-height:1.7;margin:0 0 28px;\">" +
                "Vaučer važi za bilo koje Escapii putovanje iznenađenja i može se iskoristiti u celosti. Ne može se zameniti za gotovinu." +
                "</p>" +
                "<div style=\"text-align:center;\">" +
                "<a href=\"" + frontendUrl + "\" style=\"display:inline-block;padding:14px 36px;background:#a85e44;color:#fff;" +
                "text-decoration:none;border-radius:8px;font-size:15px;font-weight:700;letter-spacing:.3px;\">Rezerviši putovanje →</a>" +
                "</div>";

        String html = EmailHtmlBuilder.wrapBase(
                "#a85e44", "",
                EmailHtmlBuilder.statusBadge("Vaučer spreman", "green"),
                "Tvoj poklon vaučer je spreman!",
                "Vaučer je u prilogu kao PDF",
                v.getCode(),
                body,
                EmailHtmlBuilder.customerFooter(contactEmail),
                false
        );

        String attachmentName = "escapii-poklon-" + v.getCode() + ".pdf";
        boolean ok = emailSender.sendWithAttachment(
                v.getBuyerEmail(),
                "🎁 Tvoj Escapii poklon vaučer - " + v.getAmount().toPlainString() + " EUR",
                html,
                attachmentName,
                pdfBytes,
                "application/pdf");
        if (!ok) log.warn("[GiftVoucher] PDF email nije poslat kupcu za vaučer id={}", v.getId());
    }
}
