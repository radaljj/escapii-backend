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
                                List<Booking> upcoming) {

        String todayStr  = today.format(EmailHtmlBuilder.DATE_FMT);
        boolean hasToday = !revealsSent.isEmpty() || !forecastDue.isEmpty();

        StringBuilder body = new StringBuilder();

        // ── Meta bar ──────────────────────────────────────────────────────────
        body.append(metaBar(upcoming.size(), revealsSent.size(), forecastDue.size()));

        // ── Danas nema akcija ─────────────────────────────────────────────────
        if (!hasToday) {
            body.append("""
                <div style="background:#f0fdf4;border:1px solid #bbf7d0;border-left:4px solid #16a34a;\
                border-radius:8px;padding:16px 20px;margin-bottom:20px;">
                  <p style="margin:0;font-size:14px;color:#14532d;font-weight:700;">✅ Danas nema akcija — sve je u redu!</p>
                </div>""");
        }

        // ── Reveal poslan danas ───────────────────────────────────────────────
        if (!revealsSent.isEmpty()) {
            body.append(section(
                "✉ Reveal poslan danas (" + revealsSent.size() + ")",
                "#f0fdf4", "#bbf7d0", "#15803d",
                revealThead(),
                revealRows(today, revealsSent)
            ));
        }

        // ── Prognoza danas (informativno) ─────────────────────────────────────
        if (!forecastDue.isEmpty()) {
            body.append(section(
                "🌤 Prognoza vremena danas — pošalji ručno (" + forecastDue.size() + ")",
                "#fff7ed", "#fed7aa", "#c2410c",
                forecastThead(),
                forecastRows(today, forecastDue)
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
                "#eff6ff", "#bfdbfe", "#1d4ed8",
                previewThead(),
                previewRows(today, preview)
            ));
        }

        String html = EmailHtmlBuilder.wrapBase(
            "#0D2E38",
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
            style="background:#f8f9fa;border:1px solid #e5e7eb;border-radius:8px;margin-bottom:20px;">
              <tr>
                <td style="padding:12px 16px;border-right:1px solid #e5e7eb;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">Aktivnih rezervacija</div>
                  <div style="font-size:16px;font-weight:700;color:#1f2937;">%d</div>
                </td>
                <td style="padding:12px 16px;border-right:1px solid #e5e7eb;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">✉ Reveal danas</div>
                  <div style="font-size:16px;font-weight:700;color:%s;">%d</div>
                </td>
                <td style="padding:12px 16px;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">🌤 Prognoza danas</div>
                  <div style="font-size:16px;font-weight:700;color:%s;">%d</div>
                </td>
              </tr>
            </table>""".formatted(
                total,
                reveals > 0 ? "#16a34a" : "#1f2937", reveals,
                forecasts > 0 ? "#c2410c" : "#1f2937", forecasts);
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
                <tr style="border-bottom:1px solid #f3f4f6;">
                  <td style="padding:9px 10px;font-size:12px;vertical-align:middle;">
                    <strong style="color:#2D5F6B;">%s</strong>
                  </td>
                  <td style="padding:9px 10px;font-size:12px;color:#6b7280;vertical-align:middle;">
                    <a href="mailto:%s" style="color:#6b7280;text-decoration:none;">%s</a>
                  </td>
                  <td style="padding:9px 10px;font-size:12px;font-weight:700;color:#CA8A71;vertical-align:middle;">%s</td>
                  <td style="padding:9px 10px;font-size:12px;vertical-align:middle;">%s</td>
                  <td style="padding:9px 10px;vertical-align:middle;">
                    <span style="font-size:12px;color:#374151;">%s</span>
                    <span style="display:inline-block;margin-left:6px;padding:2px 7px;border-radius:100px;font-size:10px;font-weight:700;background:#dcfce7;color:#15803d;">%s</span>
                  </td>
                  <td style="padding:9px 10px;font-size:13px;font-weight:700;color:#15803d;vertical-align:middle;">%s</td>
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
        return thead("Putnik", "Email", "Ref", "Aerodrom", "Polazak", "");
    }

    private String forecastRows(LocalDate today, List<Booking> bookings) {
        StringBuilder sb = new StringBuilder();
        for (Booking b : bookings) {
            LocalDate dep  = b.getSelectedDate().getDepartureDate();
            long daysLeft  = today.until(dep).getDays();
            String daysLbl = daysLabel(daysLeft);
            sb.append("""
                <tr style="border-bottom:1px solid #f3f4f6;">
                  <td style="padding:9px 10px;font-size:12px;vertical-align:middle;">
                    <strong style="color:#2D5F6B;">%s</strong>
                  </td>
                  <td style="padding:9px 10px;font-size:12px;color:#6b7280;vertical-align:middle;">
                    <a href="mailto:%s" style="color:#6b7280;text-decoration:none;">%s</a>
                  </td>
                  <td style="padding:9px 10px;font-size:12px;font-weight:700;color:#CA8A71;vertical-align:middle;">%s</td>
                  <td style="padding:9px 10px;font-size:12px;vertical-align:middle;">%s</td>
                  <td style="padding:9px 10px;vertical-align:middle;">
                    <span style="font-size:12px;color:#374151;">%s</span>
                    <span style="display:inline-block;margin-left:6px;padding:2px 7px;border-radius:100px;font-size:10px;font-weight:700;background:#fff7ed;color:#c2410c;">%s</span>
                  </td>
                  <td style="padding:9px 10px;font-size:11px;color:#9ca3af;vertical-align:middle;font-style:italic;">pošalji ručno</td>
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
                    ? "background:#fff7ed;color:#ea580c;"
                    : "background:#e0f2fe;color:#0369a1;";
            String revealDate  = dep.minusDays(3).format(EmailHtmlBuilder.DATE_FMT);
            String forecastDate = dep.minusDays(5).format(EmailHtmlBuilder.DATE_FMT);
            boolean revealDone = b.getRevealSentAt() != null;

            sb.append("""
                <tr style="border-bottom:1px solid #f3f4f6;">
                  <td style="padding:9px 10px;font-size:12px;vertical-align:middle;">
                    <strong style="color:#2D5F6B;">%s</strong>
                    <span style="color:#9ca3af;"> · %s · %s</span>
                  </td>
                  <td style="padding:9px 10px;vertical-align:middle;">
                    <span style="display:inline-block;padding:2px 8px;border-radius:100px;font-size:11px;font-weight:700;%s">%s</span>
                  </td>
                  <td style="padding:9px 10px;font-size:11px;vertical-align:middle;color:%s;">%s</td>
                  <td style="padding:9px 10px;font-size:11px;color:#9ca3af;vertical-align:middle;">%s</td>
                </tr>""".formatted(
                    EmailHtmlBuilder.esc(b.getFirstName() + " " + b.getLastName()),
                    EmailHtmlBuilder.esc(b.getBookingRef()),
                    dep.format(EmailHtmlBuilder.DATE_FMT),
                    badgeCss, daysLbl,
                    revealDone ? "#16a34a" : "#6b7280",
                    revealDone ? "✉ poslat" : revealDate,
                    forecastDate
            ));
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String thead(String... cols) {
        StringBuilder sb = new StringBuilder("<thead><tr style=\"background:#f9fafb;border-bottom:1px solid #e5e7eb;\">");
        for (String col : cols) {
            sb.append("<th style=\"padding:7px 10px;text-align:left;font-size:10px;font-weight:700;"
                    + "letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;\">")
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
