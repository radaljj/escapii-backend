package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.service.email.DigestEmailService;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigestEmailServiceImpl implements DigestEmailService {

    private final EmailSender sender;

    @Value("${app.ops-email}")
    private String opsEmail;

    @Override
    public void sendDailyDigest(LocalDate today,
                                List<Booking> revealsSent,
                                List<Booking> forecastDue,
                                List<Booking> upcoming,
                                List<Booking> revealBoxPending) {

        String todayStr  = today.format(EmailHtmlBuilder.DATE_FMT);
        boolean hasToday = !revealsSent.isEmpty() || !forecastDue.isEmpty() || !revealBoxPending.isEmpty();

        StringBuilder body = new StringBuilder();

        // ── Meta bar ──────────────────────────────────────────────────────────
        body.append(metaBar(upcoming.size(), revealsSent.size(), forecastDue.size()));

        // ── Danas nema akcija ─────────────────────────────────────────────────
        if (!hasToday) {
            body.append("""
                <div style="background:#eef6f0;border:1px solid #c3d8c9;border-left:4px solid #1d6042;\
                border-radius:8px;padding:16px 20px;margin-bottom:20px;">
                  <p style="margin:0;font-size:14px;color:#1d6042;font-weight:700;">✅ Danas nema akcija — sve je u redu!</p>
                </div>""");
        }

        // ── Reveal poslan danas ───────────────────────────────────────────────
        if (!revealsSent.isEmpty()) {
            body.append(section(
                "✉ Reveal poslan danas (" + revealsSent.size() + ")",
                "#eef6f0", "#c3d8c9", "#1d6042",
                revealThead(),
                revealRows(today, revealsSent)
            ));
        }

        // ── Prognoza poslata danas ─────────────────────────────────────────────
        if (!forecastDue.isEmpty()) {
            body.append(section(
                "🌤 Prognoza poslata danas (" + forecastDue.size() + ")",
                "#fff5eb", "#e8c7b1", "#a85e44",
                forecastThead(),
                forecastRows(today, forecastDue)
            ));
        }

        // ── Reveal Box podsjetnik ─────────────────────────────────────────────
        if (!revealBoxPending.isEmpty()) {
            body.append(section(
                "📦 Pošalji Reveal Box (" + revealBoxPending.size() + ") — polazak za ≤ 5 dana!",
                "#fdf3e7", "#e8c7b1", "#a85e44",
                revealBoxThead(),
                revealBoxRows(today, revealBoxPending)
            ));
        }

        // ── Narednih 14 dana (preview) ────────────────────────────────────────
        // Isključi booking-e koji su već obrađeni danas da ne dupliramo
        List<Booking> preview = upcoming.stream()
                .filter(b -> revealsSent.stream().noneMatch(r -> r.getId().equals(b.getId())))
                .filter(b -> forecastDue.stream().noneMatch(f -> f.getId().equals(b.getId())))
                .toList();

        if (!preview.isEmpty()) {
            body.append(section(
                "📅 Narednih 14 dana (" + preview.size() + ")",
                "#eaf0f3", "#bcd0d6", "#1f4a57",
                previewThead(),
                previewRows(today, preview)
            ));
        }

        String html = EmailHtmlBuilder.wrapBase(
            "#2D5F6B",
            "#1e1b4b",
            EmailHtmlBuilder.statusBadge("Jutarnji pregled", "blue"),
            "Jutarnji pregled",
            todayStr + " &middot; " + upcoming.size() + " aktivnih rezervacija",
            "",
            body.toString(),
            "Escapii interni sistem &middot; Automatska poruka &middot; Ne odgovarati",
            false
        );

        sender.send(opsEmail, "📋 Escapii — " + todayStr, html);
        log.info("[Digest] Poslan. Reveal: {}, Prognoza: {}, Preview: {}",
                revealsSent.size(), forecastDue.size(), preview.size());
    }

    // ── Meta bar ──────────────────────────────────────────────────────────────

    private String metaBar(int total, int reveals, int forecasts) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" \
            style="background:#faf6ee;border:1px solid #ebe1cf;border-radius:8px;margin-bottom:20px;">
              <tr>
                <td style="padding:12px 16px;border-right:1px solid #ebe1cf;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:3px;">Aktivnih rezervacija</div>
                  <div style="font-size:16px;font-weight:700;color:#1a1410;">%d</div>
                </td>
                <td style="padding:12px 16px;border-right:1px solid #ebe1cf;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:3px;">✉ Reveal danas</div>
                  <div style="font-size:16px;font-weight:700;color:%s;">%d</div>
                </td>
                <td style="padding:12px 16px;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:3px;">🌤 Prognoza danas</div>
                  <div style="font-size:16px;font-weight:700;color:%s;">%d</div>
                </td>
              </tr>
            </table>""".formatted(
                total,
                reveals > 0 ? "#1d6042" : "#1a1410", reveals,
                forecasts > 0 ? "#a85e44" : "#1a1410", forecasts);
    }

    // ── Reveal sekcija ────────────────────────────────────────────────────────

    private String revealThead() {
        return thead("Putnik", "Email", "Ref", "Aerodrom", "Polazak", "Destinacija");
    }

    private String revealRows(LocalDate today, List<Booking> bookings) {
        StringBuilder sb = new StringBuilder();
        for (Booking b : bookings) {
            LocalDate dep   = b.getSelectedDate().getDepartureDate();
            long daysLeft   = today.until(dep).getDays();
            String daysLbl  = daysLabel(daysLeft);
            sb.append("""
                <tr style="border-bottom:1px solid #ebe1cf;">
                  <td style="padding:9px 10px;font-size:12px;vertical-align:middle;">
                    <strong style="color:#2D5F6B;">%s</strong>
                  </td>
                  <td style="padding:9px 10px;font-size:12px;color:#6b5d4f;vertical-align:middle;">
                    <a href="mailto:%s" style="color:#6b5d4f;text-decoration:none;">%s</a>
                  </td>
                  <td style="padding:9px 10px;font-size:12px;font-weight:700;color:#a85e44;vertical-align:middle;">%s</td>
                  <td style="padding:9px 10px;font-size:12px;vertical-align:middle;">%s</td>
                  <td style="padding:9px 10px;vertical-align:middle;">
                    <span style="font-size:12px;color:#1a1410;">%s</span>
                    <span style="display:inline-block;margin-left:6px;padding:2px 7px;border-radius:100px;font-size:10px;font-weight:700;background:#eef6f0;color:#1d6042;">%s</span>
                  </td>
                  <td style="padding:9px 10px;font-size:13px;font-weight:700;color:#1d6042;vertical-align:middle;">%s</td>
                </tr>""".formatted(
                    EmailHtmlBuilder.esc(b.getFirstName() + " " + b.getLastName()),
                    EmailHtmlBuilder.esc(b.getEmail()), EmailHtmlBuilder.esc(b.getEmail()),
                    EmailHtmlBuilder.esc(b.getBookingRef()),
                    EmailHtmlBuilder.esc(b.getDepartureAirport()),
                    dep.format(EmailHtmlBuilder.DATE_FMT), daysLbl,
                    EmailHtmlBuilder.esc(b.getAssignedDestination())
            ));
        }
        return sb.toString();
    }

    // ── Prognoza sekcija ──────────────────────────────────────────────────────

    private String forecastThead() {
        return thead("Putnik", "Email", "Ref", "Aerodrom", "Polazak", "Status");
    }

    private String forecastRows(LocalDate today, List<Booking> bookings) {
        StringBuilder sb = new StringBuilder();
        for (Booking b : bookings) {
            LocalDate dep  = b.getSelectedDate().getDepartureDate();
            long daysLeft  = today.until(dep).getDays();
            String daysLbl = daysLabel(daysLeft);
            sb.append("""
                <tr style="border-bottom:1px solid #ebe1cf;">
                  <td style="padding:9px 10px;font-size:12px;vertical-align:middle;">
                    <strong style="color:#2D5F6B;">%s</strong>
                  </td>
                  <td style="padding:9px 10px;font-size:12px;color:#6b5d4f;vertical-align:middle;">
                    <a href="mailto:%s" style="color:#6b5d4f;text-decoration:none;">%s</a>
                  </td>
                  <td style="padding:9px 10px;font-size:12px;font-weight:700;color:#a85e44;vertical-align:middle;">%s</td>
                  <td style="padding:9px 10px;font-size:12px;vertical-align:middle;">%s</td>
                  <td style="padding:9px 10px;vertical-align:middle;">
                    <span style="font-size:12px;color:#1a1410;">%s</span>
                    <span style="display:inline-block;margin-left:6px;padding:2px 7px;border-radius:100px;font-size:10px;font-weight:700;background:#fff5eb;color:#a85e44;">%s</span>
                  </td>
                  <td style="padding:9px 10px;font-size:12px;font-weight:700;color:#1d6042;vertical-align:middle;">🌤 poslato</td>
                </tr>""".formatted(
                    EmailHtmlBuilder.esc(b.getFirstName() + " " + b.getLastName()),
                    EmailHtmlBuilder.esc(b.getEmail()), EmailHtmlBuilder.esc(b.getEmail()),
                    EmailHtmlBuilder.esc(b.getBookingRef()),
                    EmailHtmlBuilder.esc(b.getDepartureAirport()),
                    dep.format(EmailHtmlBuilder.DATE_FMT), daysLbl
            ));
        }
        return sb.toString();
    }

    // ── Preview sekcija ───────────────────────────────────────────────────────

    private String previewThead() {
        return thead("Putnik · Ref · Polazak", "Dana", "Reveal", "Prognoza");
    }

    private String previewRows(LocalDate today, List<Booking> bookings) {
        StringBuilder sb = new StringBuilder();
        for (Booking b : bookings) {
            LocalDate dep      = b.getSelectedDate().getDepartureDate();
            long daysLeft      = today.until(dep).getDays();
            String daysLbl     = daysLabel(daysLeft);
            String badgeCss    = daysLeft <= 5
                    ? "background:#fff5eb;color:#a85e44;"
                    : "background:#eaf0f3;color:#1f4a57;";
            String revealDate   = dep.minusDays(2).format(EmailHtmlBuilder.DATE_FMT);
            String forecastDate = dep.minusDays(4).format(EmailHtmlBuilder.DATE_FMT);
            boolean revealDone   = b.getRevealSentAt()   != null;
            boolean forecastDone = b.getForecastSentAt() != null;

            sb.append("""
                <tr style="border-bottom:1px solid #ebe1cf;">
                  <td style="padding:9px 10px;font-size:12px;vertical-align:middle;">
                    <strong style="color:#2D5F6B;">%s</strong>
                    <span style="color:#a89888;"> · %s · %s</span>
                  </td>
                  <td style="padding:9px 10px;vertical-align:middle;">
                    <span style="display:inline-block;padding:2px 8px;border-radius:100px;font-size:11px;font-weight:700;%s">%s</span>
                  </td>
                  <td style="padding:9px 10px;font-size:11px;vertical-align:middle;color:%s;font-weight:%s;">%s</td>
                  <td style="padding:9px 10px;font-size:11px;vertical-align:middle;color:%s;font-weight:%s;">%s</td>
                </tr>""".formatted(
                    EmailHtmlBuilder.esc(b.getFirstName() + " " + b.getLastName()),
                    EmailHtmlBuilder.esc(b.getBookingRef()),
                    dep.format(EmailHtmlBuilder.DATE_FMT),
                    badgeCss, daysLbl,
                    revealDone   ? "#1d6042" : "#6b5d4f",
                    revealDone   ? "700" : "400",
                    revealDone   ? "✉ poslat"    : revealDate,
                    forecastDone ? "#1d6042" : "#a89888",
                    forecastDone ? "700" : "400",
                    forecastDone ? "🌤 poslata" : forecastDate
            ));
        }
        return sb.toString();
    }

    // ── Reveal Box sekcija ────────────────────────────────────────────────────

    private String revealBoxThead() {
        return thead("Putnik", "Ref", "Polazak", "Adresa dostave", "Telefon");
    }

    private String revealBoxRows(LocalDate today, List<Booking> bookings) {
        StringBuilder sb = new StringBuilder();
        for (Booking b : bookings) {
            LocalDate dep  = b.getSelectedDate().getDepartureDate();
            long daysLeft  = today.until(dep).getDays();
            String daysLbl = daysLabel(daysLeft);
            String address = (b.getDeliveryAddress() != null ? b.getDeliveryAddress() : "—")
                           + (b.getDeliveryCity() != null ? ", " + b.getDeliveryCity() : "");
            String phone   = b.getDeliveryPhone() != null ? b.getDeliveryPhone() : "—";
            sb.append("""
                <tr style="border-bottom:1px solid #ebe1cf;">
                  <td style="padding:9px 10px;font-size:12px;vertical-align:middle;">
                    <strong style="color:#a85e44;">%s</strong>
                    <div style="font-size:11px;color:#6b5d4f;margin-top:2px;">%s</div>
                  </td>
                  <td style="padding:9px 10px;font-size:12px;font-weight:700;color:#a85e44;vertical-align:middle;">%s</td>
                  <td style="padding:9px 10px;vertical-align:middle;">
                    <span style="font-size:12px;color:#1a1410;">%s</span>
                    <span style="display:inline-block;margin-left:6px;padding:2px 7px;border-radius:100px;font-size:10px;font-weight:700;background:#fff5eb;color:#a85e44;">%s</span>
                  </td>
                  <td style="padding:9px 10px;font-size:12px;color:#2b231b;vertical-align:middle;">%s</td>
                  <td style="padding:9px 10px;font-size:12px;color:#2b231b;vertical-align:middle;">%s</td>
                </tr>""".formatted(
                    EmailHtmlBuilder.esc(b.getFirstName() + " " + b.getLastName()),
                    EmailHtmlBuilder.esc(b.getEmail()),
                    EmailHtmlBuilder.esc(b.getBookingRef()),
                    dep.format(EmailHtmlBuilder.DATE_FMT), daysLbl,
                    EmailHtmlBuilder.esc(address),
                    EmailHtmlBuilder.esc(phone)
            ));
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String thead(String... cols) {
        StringBuilder sb = new StringBuilder("<thead><tr style=\"background:#f5efe2;border-bottom:1px solid #ebe1cf;\">");
        for (String col : cols) {
            sb.append("<th style=\"padding:7px 10px;text-align:left;font-size:10px;font-weight:700;"
                    + "letter-spacing:0.8px;text-transform:uppercase;color:#6b5d4f;\">")
              .append(col).append("</th>");
        }
        sb.append("</tr></thead>");
        return sb.toString();
    }

    private String section(String title, String bg, String border, String titleColor, String thead, String rows) {
        return """
            <div style="border-radius:8px;overflow:hidden;margin:0 0 16px;">
              <div style="padding:12px 18px;font-size:13px;font-weight:700;background:%s;color:%s;\
            border:1px solid %s;border-bottom:none;">%s</div>
              <table width="100%%" cellpadding="0" cellspacing="0" \
            style="border-collapse:collapse;border:1px solid %s;border-top:none;background:#fff;">
                %s<tbody>%s</tbody>
              </table>
            </div>""".formatted(bg, titleColor, border, title, border, thead, rows);
    }

    private String daysLabel(long days) {
        if (days == 0) return "danas";
        if (days == 1) return "za 1 dan";
        return "za " + days + " dana";
    }
}
