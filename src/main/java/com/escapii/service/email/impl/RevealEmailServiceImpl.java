package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.service.email.RevealEmailService;
import com.escapii.util.LogUtils;
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
public class RevealEmailServiceImpl implements RevealEmailService {

    private final EmailSender sender;

    @Value("${app.frontend-url:https://escapii.rs}")
    private String frontendUrl;

    @Value("${app.ops-email}")
    private String opsEmail;

    // ── Reveal email korisniku ───────────────────────────────────────────────

    @Override
    @Async
    public void sendRevealEmail(Booking booking) {
        sendRevealEmail(booking, frontendUrl);
    }

    @Override
    @Async
    public void sendRevealEmail(Booking booking, String siteUrl) {
        String usedUrl   = (siteUrl != null && !siteUrl.isBlank()) ? siteUrl : frontendUrl;
        String firstName = EmailHtmlBuilder.esc(booking.getFirstName());
        String ref       = EmailHtmlBuilder.esc(booking.getBookingRef());
        String magicLink = usedUrl.stripTrailing() + "/otkrivanje?token=" + booking.getRevealToken();
        String departure = booking.getSelectedDate().getDepartureDate()
                .format(EmailHtmlBuilder.DATE_FMT);

        String body = """
            <div style="text-align:center;padding:8px 0 24px;">
              <div style="font-size:52px;margin-bottom:14px;">✉</div>
              <div style="font-family:Georgia,'Times New Roman',serif;font-size:24px;color:#1a1410;margin-bottom:8px;font-weight:normal;">Vreme je, %s!</div>
              <div style="font-size:14px;color:#6b5d4f;line-height:1.65;">
                Tvoje putovanje počinje <strong style="color:#2D5F6B;">%s</strong>.<br>
                Koverta s tvojom tajnom destinacijom je gotova.
              </div>
            </div>

            <div style="height:1px;background:#ebe1cf;margin:0 0 24px;"></div>

            <div style="background:#faf6ee;border:1px solid #ebe1cf;border-left:3px solid #a85e44;border-radius:6px;padding:16px 20px;margin-bottom:28px;">
              <div style="font-size:13px;font-weight:700;color:#a85e44;margin-bottom:6px;">Šta dalje?</div>
              <div style="font-size:13px;color:#1a1410;line-height:1.7;">
                Klikni dugme ispod i otkrij svoju destinaciju - čeka te dramatično otvaranje koverte.<br>
                <span style="color:#a89888;font-size:12px;">Link je personalan i važi do dana polaska.</span>
              </div>
            </div>

            <div style="text-align:center;margin:0 0 28px;">
              <a href="%s"
                 style="display:inline-block;background:#a85e44;color:#fff;font-weight:800;font-size:16px;
                        padding:16px 44px;border-radius:100px;text-decoration:none;letter-spacing:0.3px;
                        box-shadow:0 4px 16px rgba(168,94,68,0.4);">
                Otkrij svoju destinaciju &rarr;
              </a>
            </div>

            <p style="margin:0;font-size:12px;color:#a89888;line-height:1.6;text-align:center;">
              Ako dugme ne radi, kopiraj ovaj link u browser:<br>
              <span style="color:#6b5d4f;word-break:break-all;">%s</span>
            </p>
            """.formatted(firstName, departure, magicLink, magicLink);

        String html = EmailHtmlBuilder.wrapBase(
            "#a85e44",
            "#0f1f3d",
            EmailHtmlBuilder.statusBadge("Otkrij destinaciju", "orange"),
            "Tvoja destinacija te čeka!",
            "48 sati pre polaska - stiglo je vreme.",
            ref,
            body,
            "Escapii · escapii.rs · Srećan put!",
            false
        );

        sender.send(
            booking.getEmail(),
            "✉ Tvoja destinacija je spremna - otkrij je! | Escapii",
            html
        );

        log.info("[Reveal] Email poslan korisniku {} za rezervaciju {}",
                LogUtils.maskEmail(booking.getEmail()), booking.getBookingRef());
    }

    // ── Interna notifikacija timu ────────────────────────────────────────────

    @Override
    @Async
    public void sendRevealTeamNotification(Booking booking) {
        String ref        = EmailHtmlBuilder.esc(booking.getBookingRef());
        String name       = EmailHtmlBuilder.esc(booking.getFirstName() + " " + booking.getLastName());
        String dest       = EmailHtmlBuilder.esc(booking.getAssignedDestination());
        String departure  = booking.getSelectedDate().getDepartureDate().format(EmailHtmlBuilder.DATE_FMT);
        String email      = EmailHtmlBuilder.esc(booking.getEmail());

        String body = """
            <table width="100%%" cellpadding="0" cellspacing="0">
              <tr>
                <td width="38%%" style="width:38%%;padding:7px 0;font-size:13px;color:#a89888;font-weight:600;border-bottom:1px solid #ebe1cf;">Rezervacija</td>
                <td width="62%%" style="width:62%%;padding:7px 0;font-size:13px;color:#2D5F6B;font-weight:800;border-bottom:1px solid #ebe1cf;">%s</td>
              </tr>
              <tr>
                <td width="38%%" style="width:38%%;padding:7px 0;font-size:13px;color:#a89888;font-weight:600;border-bottom:1px solid #ebe1cf;">Putnik</td>
                <td width="62%%" style="width:62%%;padding:7px 0;font-size:13px;color:#1a1410;border-bottom:1px solid #ebe1cf;">%s</td>
              </tr>
              <tr>
                <td width="38%%" style="width:38%%;padding:7px 0;font-size:13px;color:#a89888;font-weight:600;border-bottom:1px solid #ebe1cf;">Email</td>
                <td width="62%%" style="width:62%%;padding:7px 0;font-size:13px;color:#1a1410;border-bottom:1px solid #ebe1cf;">%s</td>
              </tr>
              <tr>
                <td width="38%%" style="width:38%%;padding:7px 0;font-size:13px;color:#a89888;font-weight:600;border-bottom:1px solid #ebe1cf;">Destinacija</td>
                <td width="62%%" style="width:62%%;padding:7px 0;font-size:15px;color:#a85e44;font-weight:800;border-bottom:1px solid #ebe1cf;">%s</td>
              </tr>
              <tr>
                <td width="38%%" style="width:38%%;padding:7px 0;font-size:13px;color:#a89888;font-weight:600;">Datum polaska</td>
                <td width="62%%" style="width:62%%;padding:7px 0;font-size:13px;color:#1a1410;">%s</td>
              </tr>
            </table>
            """.formatted(ref, name, email, dest, departure);

        String html = EmailHtmlBuilder.wrapBase(
            "#1d6042",
            "#064e3b",
            EmailHtmlBuilder.statusBadge("Reveal poslan", "green"),
            "✅ Destinacija poslata",
            "Reveal email je automatski poslan korisniku.",
            ref,
            body,
            "Escapii · interni sistem",
            false
        );

        sender.send(
            opsEmail,
            "[Escapii Ops] Destinacija poslata → " + booking.getBookingRef() + " → " + booking.getAssignedDestination(),
            html
        );

        log.info("[Reveal] Tim notifikovan za {} ({})", booking.getBookingRef(), booking.getAssignedDestination());
    }
}
