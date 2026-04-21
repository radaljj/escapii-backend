package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.service.email.RevealEmailService;
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

    @Value("${app.frontend-url:https://escapii.com}")
    private String frontendUrl;

    @Value("${app.ops-email}")
    private String opsEmail;

    // ── Reveal email korisniku ───────────────────────────────────────────────

    @Override
    @Async
    public void sendRevealEmail(Booking booking) {
        String firstName = EmailHtmlBuilder.esc(booking.getFirstName());
        String ref       = EmailHtmlBuilder.esc(booking.getBookingRef());
        String magicLink = frontendUrl + "/otkrivanje?token=" + booking.getRevealToken();
        String departure = booking.getSelectedDate().getDepartureDate()
                .format(EmailHtmlBuilder.DATE_FMT);

        String body = """
            <div style="text-align:center;padding:8px 0 24px;">
              <div style="font-size:52px;margin-bottom:14px;">✉</div>
              <div style="font-family:Georgia,'Times New Roman',serif;font-size:24px;color:#2D5F6B;margin-bottom:8px;font-weight:normal;">Vreme je, %s!</div>
              <div style="font-size:14px;color:#6b7280;line-height:1.65;">
                Tvoje putovanje polazi <strong style="color:#2D5F6B;">%s</strong>.<br>
                Koverta s tvojom tajnom destinacijom je gotova.
              </div>
            </div>

            <div style="height:1px;background:#f3f4f6;margin:0 0 24px;"></div>

            <div style="background:#f5f0fa;border:1px solid #e8d5f5;border-left:3px solid #CA8A71;border-radius:6px;padding:16px 20px;margin-bottom:28px;">
              <div style="font-size:13px;font-weight:700;color:#CA8A71;margin-bottom:6px;">Šta dalje?</div>
              <div style="font-size:13px;color:#374151;line-height:1.7;">
                Klikni dugme ispod i otkrij svoju destinaciju — čeka te dramatično otvaranje koverte.<br>
                <span style="color:#9ca3af;font-size:12px;">Link je personalan i važi do dana polaska.</span>
              </div>
            </div>

            <div style="text-align:center;margin:0 0 28px;">
              <a href="%s"
                 style="display:inline-block;background:#CA8A71;color:#fff;font-weight:800;font-size:16px;
                        padding:16px 44px;border-radius:100px;text-decoration:none;letter-spacing:0.3px;
                        box-shadow:0 4px 16px rgba(202,138,113,0.4);">
                Otkrij svoju destinaciju &rarr;
              </a>
            </div>

            <p style="margin:0;font-size:12px;color:#9ca3af;line-height:1.6;text-align:center;">
              Ako dugme ne radi, kopiraj ovaj link u browser:<br>
              <span style="color:#6b7280;word-break:break-all;">%s</span>
            </p>
            """.formatted(firstName, departure, magicLink, magicLink);

        String html = EmailHtmlBuilder.wrapBase(
            "Escapii",
            "#0f1f3d",
            "Tvoja destinacija te čeka!",
            "72 sata pre polaska — stiglo je vreme.",
            ref,
            "#CA8A71",
            "OTKRIJ",
            body,
            "Escapii · escapii.com · Srećan put!",
            false
        );

        sender.send(
            booking.getEmail(),
            "✉ Tvoja destinacija je spremna — otkrij je! | Escapii",
            html
        );

        log.info("[Reveal] Email poslan korisniku {} za rezervaciju {}",
                booking.getEmail(), booking.getBookingRef());
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
                <td style="padding:6px 0;font-size:13px;color:#374151;width:140px;font-weight:600;">Rezervacija</td>
                <td style="padding:6px 0;font-size:13px;color:#2D5F6B;font-weight:800;">%s</td>
              </tr>
              <tr>
                <td style="padding:6px 0;font-size:13px;color:#374151;font-weight:600;">Putnik</td>
                <td style="padding:6px 0;font-size:13px;color:#2D5F6B;">%s</td>
              </tr>
              <tr>
                <td style="padding:6px 0;font-size:13px;color:#374151;font-weight:600;">Email</td>
                <td style="padding:6px 0;font-size:13px;color:#2D5F6B;">%s</td>
              </tr>
              <tr>
                <td style="padding:6px 0;font-size:13px;color:#374151;font-weight:600;">Destinacija</td>
                <td style="padding:6px 0;font-size:15px;color:#CA8A71;font-weight:800;">%s</td>
              </tr>
              <tr>
                <td style="padding:6px 0;font-size:13px;color:#374151;font-weight:600;">Datum polaska</td>
                <td style="padding:6px 0;font-size:13px;color:#2D5F6B;">%s</td>
              </tr>
            </table>
            """.formatted(ref, name, email, dest, departure);

        String html = EmailHtmlBuilder.wrapBase(
            "Escapii Ops",
            "#064e3b",
            "✅ Destinacija poslata",
            "Reveal email je automatski poslan korisniku.",
            ref,
            "#16a34a",
            "REVEAL POSLAN",
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
