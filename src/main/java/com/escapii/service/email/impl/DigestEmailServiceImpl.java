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

    private static final String DIGEST_THEAD = """
        <thead><tr style="background:#f9fafb;border-bottom:1px solid #e5e7eb;">
          <th style="padding:7px 10px;text-align:left;font-size:10px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;">Putnik</th>
          <th style="padding:7px 10px;text-align:left;font-size:10px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;">Ref</th>
          <th style="padding:7px 10px;text-align:left;font-size:10px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;">Aerodrom</th>
          <th style="padding:7px 10px;text-align:left;font-size:10px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;">Polazak</th>
          <th style="padding:7px 10px;text-align:left;font-size:10px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;">Dana</th>
          <th style="padding:7px 10px;text-align:left;font-size:10px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;">Akcija</th>
        </tr></thead>
        """;

    private static final String PREVIEW_THEAD = """
        <thead><tr style="background:#f9fafb;border-bottom:1px solid #e5e7eb;">
          <th style="padding:7px 10px;text-align:left;font-size:10px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;">Putnik · Ref · Polazak</th>
          <th style="padding:7px 10px;text-align:left;font-size:10px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;">Dana</th>
          <th style="padding:7px 10px;text-align:left;font-size:10px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;">Sledeće</th>
        </tr></thead>
        """;

    @Override
    public void sendDailyDigest(LocalDate today, List<Booking> bookings) {
        LocalDate weatherDay     = today.plusDays(7);
        LocalDate destinationDay = today.plusDays(3);

        StringBuilder weatherRows     = new StringBuilder();
        StringBuilder destinationRows = new StringBuilder();
        StringBuilder previewRows     = new StringBuilder();

        for (Booking b : bookings) {
            LocalDate dep  = b.getSelectedDate().getDepartureDate();
            String name    = EmailHtmlBuilder.esc(b.getFirstName() + " " + b.getLastName());
            String email   = EmailHtmlBuilder.esc(b.getEmail());
            String ref     = EmailHtmlBuilder.esc(b.getBookingRef());
            String depStr  = dep.format(EmailHtmlBuilder.DATE_FMT);
            String airport = EmailHtmlBuilder.esc(b.getDepartureAirport());
            long daysLeft = today.until(dep).getDays();

            if (dep.equals(weatherDay)) {
                weatherRows.append(row(name, email, ref, airport, depStr, daysLeft, "🌤 Prognoza"));
            } else if (dep.equals(destinationDay)) {
                destinationRows.append(row(name, email, ref, airport, depStr, daysLeft, "✉ Koverta"));
            } else {
                String nextTask = dep.isAfter(weatherDay)
                    ? "🌤 prognoza: " + dep.minusDays(7).format(EmailHtmlBuilder.DATE_FMT) + " · ✉ koverta: " + dep.minusDays(3).format(EmailHtmlBuilder.DATE_FMT)
                    : "✉ koverta: " + dep.minusDays(3).format(EmailHtmlBuilder.DATE_FMT);
                previewRows.append(previewRow(name, ref, airport, depStr, daysLeft, nextTask));
            }
        }

        String todayStr = today.format(EmailHtmlBuilder.DATE_FMT);

        String destinationSection = !destinationRows.isEmpty() ? section(
            "✉ Danas pošalji kovertu s destinacijom", "#fff1f2", "#fecaca", "#b91c1c",
            DIGEST_THEAD, destinationRows.toString()) : "";

        String weatherSection = !weatherRows.isEmpty() ? section(
            "🌤 Danas pošalji prognozu vremena", "#fff7ed", "#fed7aa", "#c2410c",
            DIGEST_THEAD, weatherRows.toString()) : "";

        String nothingToday = weatherRows.isEmpty() && destinationRows.isEmpty() ? """
            <div style="background:#f0fdf4;border:1px solid #bbf7d0;border-left:3px solid #16a34a;border-radius:6px;padding:14px 20px;margin-bottom:20px;">
              <p style="margin:0;font-size:13px;color:#14532d;font-weight:600;">✓ Nema zadataka za danas — sve je u redu!</p>
            </div>""" : "";

        String previewSection = !previewRows.isEmpty() ? section(
            "📅 Narednih 14 dana", "#eff6ff", "#bfdbfe", "#1d4ed8",
            PREVIEW_THEAD, previewRows.toString()) : "";

        long weatherCount     = weatherRows.isEmpty() ? 0 : weatherRows.toString().split("<tr").length - 1;
        long destinationCount = destinationRows.isEmpty() ? 0 : destinationRows.toString().split("<tr").length - 1;
        String metaBar = """
            <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f8f9fa;border:1px solid #e5e7eb;border-radius:6px;margin-bottom:20px;">
              <tr>
                <td style="padding:12px 16px;border-right:1px solid #e5e7eb;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">Ukupno aktivnih</div>
                  <div style="font-size:15px;font-weight:700;color:#1f2937;">%d rezervacija</div>
                </td>
                <td style="padding:12px 16px;border-right:1px solid #e5e7eb;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">&#127780; Prognoza danas</div>
                  <div style="font-size:15px;font-weight:700;color:%s;">%d</div>
                </td>
                <td style="padding:12px 16px;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">&#9993; Koverta danas</div>
                  <div style="font-size:15px;font-weight:700;color:%s;">%d</div>
                </td>
              </tr>
            </table>
            """.formatted(
            bookings.size(),
            weatherCount > 0 ? "#8B2FC9" : "#1f2937", weatherCount,
            destinationCount > 0 ? "#dc2626" : "#1f2937", destinationCount);

        String body = metaBar + nothingToday + destinationSection + weatherSection + previewSection;

        boolean hasUrgent = !weatherRows.isEmpty() || !destinationRows.isEmpty();
        String html = EmailHtmlBuilder.wrapBase(
            "Escapii Ops",
            "#1e1b4b",
            "Jutarnji pregled",
            todayStr + " &middot; " + bookings.size() + " rezervacija u narednih 14 dana",
            "",
            hasUrgent ? "#8B2FC9" : "#16a34a",
            hasUrgent ? "AKCIJA POTREBNA" : "SVE U REDU",
            body,
            "Escapii interni sistem &middot; Automatska poruka &middot; Ne odgovarati",
            false
        );

        sender.send(opsEmail, "📋 Escapii — " + todayStr, html);
    }

    private String row(String name, String email, String ref, String airport, String dep, long daysLeft, String action) {
        String badgeCss = daysLeft <= 3
            ? "background:#fee2e2;color:#dc2626;"
            : daysLeft <= 7 ? "background:#fff7ed;color:#ea580c;"
            : "background:#dcfce7;color:#16a34a;";
        String daysLabel = daysLeft == 0 ? "danas" : "za " + daysLeft + (daysLeft == 1 ? " dan" : " dana");
        return """
            <tr style="border-bottom:1px solid #f3f4f6;">
              <td style="padding:8px 10px;font-size:12px;color:#374151;vertical-align:middle;">
                <strong style="color:#111344;">%s</strong><br>
                <a href="mailto:%s" style="color:#9ca3af;font-size:11px;text-decoration:none;">%s</a>
              </td>
              <td style="padding:8px 10px;font-size:12px;font-weight:700;color:#8B2FC9;vertical-align:middle;">%s</td>
              <td style="padding:8px 10px;font-size:12px;color:#374151;vertical-align:middle;">%s</td>
              <td style="padding:8px 10px;font-size:12px;color:#374151;vertical-align:middle;">%s</td>
              <td style="padding:8px 10px;vertical-align:middle;">
                <span style="display:inline-block;padding:2px 8px;border-radius:100px;font-size:11px;font-weight:700;%s">%s</span>
              </td>
              <td style="padding:8px 10px;font-size:12px;font-weight:700;color:#374151;vertical-align:middle;">%s</td>
            </tr>""".formatted(name, email, email, ref, airport, dep, badgeCss, daysLabel, action);
    }

    private String previewRow(String name, String ref, String airport, String dep, long daysLeft, String nextTask) {
        String badgeCss = daysLeft <= 7
            ? "background:#fff7ed;color:#ea580c;"
            : "background:#dcfce7;color:#16a34a;";
        String daysLabel = daysLeft == 0 ? "danas" : "za " + daysLeft + (daysLeft == 1 ? " dan" : " dana");
        return """
            <tr style="border-bottom:1px solid #f3f4f6;">
              <td style="padding:8px 10px;font-size:12px;color:#374151;vertical-align:middle;">
                <strong style="color:#111344;">%s</strong>
                <span style="color:#9ca3af;font-size:11px;"> · %s · %s</span>
              </td>
              <td style="padding:8px 10px;vertical-align:middle;">
                <span style="display:inline-block;padding:2px 8px;border-radius:100px;font-size:11px;font-weight:700;%s">%s</span>
              </td>
              <td style="padding:8px 10px;font-size:11px;color:#9ca3af;vertical-align:middle;">%s</td>
            </tr>""".formatted(name, ref, dep, badgeCss, daysLabel, nextTask);
    }

    private String section(String title, String bg, String border, String titleColor, String thead, String rows) {
        return """
            <div style="border-radius:8px;overflow:hidden;margin:0 0 16px;">
              <div style="padding:12px 18px;font-size:13px;font-weight:700;background:%s;color:%s;border:1px solid %s;border-bottom:none;">%s</div>
              <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;border:1px solid %s;border-top:none;font-size:12px;background:#fff;">
                %s
                <tbody>%s</tbody>
              </table>
            </div>""".formatted(bg, titleColor, border, title, border, thead, rows);
    }
}
