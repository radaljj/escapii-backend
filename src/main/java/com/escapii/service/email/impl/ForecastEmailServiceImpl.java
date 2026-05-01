package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.service.email.ForecastEmailService;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.weather.DailyForecast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastEmailServiceImpl implements ForecastEmailService {

    private final EmailSender sender;

    @Async
    @Override
    public void sendForecastEmail(Booking booking, List<DailyForecast> forecast) {
        try {
            String firstName = EmailHtmlBuilder.esc(booking.getFirstName());
            LocalDate depDate = booking.getSelectedDate().getDepartureDate();
            LocalDate retDate = booking.getSelectedDate().getReturnDate();
            String depDateStr = depDate.format(EmailHtmlBuilder.DATE_FMT);

            DailyForecast today = forecast.get(0);
            String subject = "🌤 Tvoja prognoza za putovanje — " + depDateStr + " | Escapii";
            long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), depDate);
            String html = buildHtml(firstName, depDate, retDate, depDateStr, daysUntil, forecast, today);

            sender.send(booking.getEmail(), subject, html);
            log.info("[Forecast] ✅ Email poslan za {} ({})", booking.getBookingRef(), booking.getEmail());
        } catch (Exception e) {
            log.error("[Forecast] ❌ Greška pri slanju za {}: {}", booking.getBookingRef(), e.getMessage(), e);
        }
    }

    // ── HTML template ─────────────────────────────────────────────────────────

    private String buildHtml(String firstName, LocalDate depDate, LocalDate retDate,
                             String depDateStr, long daysUntil,
                             List<DailyForecast> forecast, DailyForecast today) {

        String dayCards       = buildDayCards(forecast, depDate, retDate);
        String travelDaysCard = buildTravelDaysCard(forecast, depDate, retDate);

        String body = """
            <!-- Hero weather card -->
            <table width="100%%" cellpadding="0" cellspacing="0" style="border-radius:16px;overflow:hidden;margin-bottom:16px;background:linear-gradient(160deg,#0a1628 0%%,#0d2d4f 50%%,#0a3d6b 100%%);">
              <tr><td style="padding:32px 28px 28px;">

                <!-- Pozdrav -->
                <p style="margin:0 0 24px;font-size:13px;color:rgba(255,255,255,0.55);letter-spacing:0.5px;">
                  Zdravo, %s! Tvoje putovanje je za <strong style="color:#7dd3fc;">%d %s</strong> — evo šta te čeka.
                </p>

                <!-- Glavna temperatura -->
                <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:24px;">
                  <tr>
                    <td style="vertical-align:middle;">
                      <div style="font-size:72px;line-height:1;margin-bottom:4px;">%s</div>
                      <div style="font-family:Georgia,serif;font-size:48px;font-weight:300;color:#fff;line-height:1;">%d°</div>
                      <div style="font-size:15px;color:rgba(255,255,255,0.7);margin-top:8px;">%s</div>
                    </td>
                    <td style="text-align:right;vertical-align:top;">
                      <div style="background:rgba(255,255,255,0.08);border:1px solid rgba(255,255,255,0.12);border-radius:12px;padding:12px 16px;display:inline-block;">
                        <div style="font-size:11px;color:rgba(255,255,255,0.45);margin-bottom:6px;letter-spacing:0.5px;">POLAZAK</div>
                        <div style="font-size:13px;font-weight:700;color:#7dd3fc;">%s</div>
                      </div>
                    </td>
                  </tr>
                </table>

                <!-- Min/Max bar -->
                <table cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
                  <tr>
                    <td style="background:rgba(255,255,255,0.08);border-radius:100px;padding:5px 14px;margin-right:8px;">
                      <span style="font-size:12px;color:rgba(255,255,255,0.5);">↑ </span>
                      <span style="font-size:13px;font-weight:700;color:#fca5a5;">%d°</span>
                    </td>
                    <td style="width:8px;"></td>
                    <td style="background:rgba(255,255,255,0.08);border-radius:100px;padding:5px 14px;">
                      <span style="font-size:12px;color:rgba(255,255,255,0.5);">↓ </span>
                      <span style="font-size:13px;font-weight:700;color:#93c5fd;">%d°</span>
                    </td>
                  </tr>
                </table>

                <!-- Separator -->
                <div style="height:1px;background:rgba(255,255,255,0.1);margin-bottom:20px;"></div>

                <!-- 11-day forecast strip -->
                <table width="100%%" cellpadding="0" cellspacing="0">
                  <tr>%s</tr>
                </table>

              </td></tr>
            </table>

            <!-- Travel days breakdown -->
            %s

            <!-- Reveal note -->
            <table width="100%%" cellpadding="0" cellspacing="0"
              style="background:#fff8f0;border:1px solid #fed7aa;border-left:3px solid #CA8A71;border-radius:8px;margin-bottom:12px;">
              <tr><td style="padding:14px 18px;">
                <div style="font-size:12px;font-weight:700;color:#CA8A71;margin-bottom:4px;">📬 Preporuka</div>
                <div style="font-size:12px;color:#374151;line-height:1.6;">
                  Kada dobiješ email sa otkrićem destinacije,
                  <strong>preporučujemo da ponovo proveriš prognozu</strong> direktno za tu destinaciju —
                  prognoza za toliko dana unapred može biti okvirna.
                </div>
              </td></tr>
            </table>

            <!-- Footer note -->
            <p style="font-size:11px;color:#9ca3af;text-align:center;margin:8px 0 0;line-height:1.6;">
              Prognoza se ažurira svakodnevno — moguća su manja odstupanja.<br>
              Srećan put! 🌍
            </p>
            """.formatted(
                firstName,
                daysUntil, daysUntil == 1 ? "dan" : "dana",
                today.emoji(), today.maxTemp(), today.description(),
                depDateStr,
                today.maxTemp(), today.minTemp(),
                dayCards,
                travelDaysCard
        );

        return EmailHtmlBuilder.wrapBase(
            "#0a1628",
            "Tvoja vremenska prognoza",
            "Putovanje · " + depDateStr,
            "",
            body,
            EmailHtmlBuilder.customerFooter("podrska@escapii.com"),
            false
        );
    }

    // ── 11-day forecast strip ─────────────────────────────────────────────────

    private String buildDayCards(List<DailyForecast> forecast, LocalDate depDate, LocalDate retDate) {
        StringBuilder sb = new StringBuilder();
        Locale sr = new Locale("sr", "RS");
        LocalDate today = LocalDate.now();

        for (int i = 0; i < forecast.size(); i++) {
            DailyForecast d = forecast.get(i);
            boolean isDep   = d.date().equals(depDate);
            boolean isRet   = d.date().equals(retDate);
            boolean isToday = d.date().equals(today);

            String label;
            String bg;

            if (isDep) {
                label = "✈ POLAZAK";
                // zlatno-narandžasti highlight za dan polaska
                bg = "background:rgba(251,191,36,0.18);border:1px solid rgba(251,191,36,0.45);";
            } else if (isRet) {
                label = "🏠 POVRATAK";
                // zelenkasti highlight za dan povratka
                bg = "background:rgba(52,211,153,0.15);border:1px solid rgba(52,211,153,0.35);";
            } else if (isToday) {
                label = "Danas";
                bg = "background:rgba(125,211,252,0.15);border:1px solid rgba(125,211,252,0.25);";
            } else {
                label = d.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, sr);
                bg = "background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.08);";
            }

            String dayDate = d.date().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"));

            // Dan polaska i povratka su vizualno malo veći
            String labelStyle = (isDep || isRet)
                ? "font-size:8px;font-weight:700;letter-spacing:0.3px;text-transform:uppercase;margin-bottom:2px;"
                  + (isDep ? "color:#fbbf24;" : "color:#34d399;")
                : "font-size:9px;color:rgba(255,255,255,0.5);margin-bottom:2px;text-transform:uppercase;letter-spacing:0.5px;";

            String tempStyle = (isDep || isRet)
                ? "font-size:13px;font-weight:800;"
                : "font-size:12px;font-weight:700;";

            sb.append("""
                <td style="text-align:center;padding:0 2px;">
                  <div style="%sborder-radius:12px;padding:10px 4px;">
                    <div style="%s">%s</div>
                    <div style="font-size:8px;color:rgba(255,255,255,0.3);margin-bottom:5px;">%s</div>
                    <div style="font-size:20px;margin-bottom:5px;">%s</div>
                    <div style="%scolor:#fca5a5;">%d°</div>
                    <div style="font-size:10px;color:#93c5fd;margin-top:2px;">%d°</div>
                    %s
                  </div>
                </td>""".formatted(
                    bg,
                    labelStyle, label,
                    dayDate,
                    d.emoji(),
                    tempStyle,
                    d.maxTemp(),
                    d.minTemp(),
                    d.precipitation() > 0.5
                        ? "<div style=\"font-size:9px;color:#7dd3fc;margin-top:3px;\">💧" + String.format("%.0f", d.precipitation()) + "mm</div>"
                        : ""
            ));
        }
        return sb.toString();
    }

    // ── Travel days breakdown (umesto packing tips) ───────────────────────────

    /**
     * Prikazuje vreme samo za dane putovanja (od polaska do povratka).
     * Relevantno i specifično — za razliku od generičkih saveta koji se
     * odnose na sve 11 dana prognoze.
     */
    private String buildTravelDaysCard(List<DailyForecast> forecast, LocalDate depDate, LocalDate retDate) {
        Locale sr = new Locale("sr", "RS");
        java.time.format.DateTimeFormatter dayFmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.");

        LocalDate lastForecastDate = forecast.isEmpty() ? depDate : forecast.get(forecast.size() - 1).date();

        // Mapa datum → prognoza za brzo traženje
        java.util.Map<LocalDate, DailyForecast> byDate = new java.util.HashMap<>();
        forecast.forEach(d -> byDate.put(d.date(), d));

        if (byDate.isEmpty()) return "";

        StringBuilder rows = new StringBuilder();

        // Prolazimo kroz sve dane putovanja (dep → ret)
        LocalDate cursor = depDate;
        while (!cursor.isAfter(retDate)) {
            boolean isDep = cursor.equals(depDate);
            boolean isRet = cursor.equals(retDate);
            DailyForecast d = byDate.get(cursor);

            String dayName = cursor.getDayOfWeek().getDisplayName(TextStyle.FULL, sr);
            dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
            String dayDate = cursor.format(dayFmt);

            String badge = "";
            if (isDep) badge = "<span style=\"background:#fbbf24;color:#1f2937;font-size:9px;font-weight:700;"
                    + "border-radius:4px;padding:1px 5px;margin-left:6px;vertical-align:middle;\">✈ POLAZAK</span>";
            else if (isRet) badge = "<span style=\"background:#34d399;color:#1f2937;font-size:9px;font-weight:700;"
                    + "border-radius:4px;padding:1px 5px;margin-left:6px;vertical-align:middle;\">🏠 POVRATAK</span>";

            if (d != null) {
                // Dan je unutar prognoze — prikaži vreme
                String precipitation = d.precipitation() > 0.5
                        ? "<span style=\"font-size:11px;color:#6b7280;\"> · 💧" + String.format("%.0f", d.precipitation()) + "mm</span>"
                        : "";

                rows.append("""
                    <tr style="border-bottom:1px solid #f3f4f6;">
                      <td style="padding:11px 0;width:28px;text-align:center;font-size:22px;vertical-align:middle;">%s</td>
                      <td style="padding:11px 8px;vertical-align:middle;">
                        <div style="font-size:13px;font-weight:600;color:#111827;">%s %s%s</div>
                        <div style="font-size:11px;color:#9ca3af;margin-top:2px;">%s%s</div>
                      </td>
                      <td style="padding:11px 0;text-align:right;vertical-align:middle;white-space:nowrap;">
                        <span style="font-size:14px;font-weight:700;color:#ef4444;">%d°</span>
                        <span style="font-size:12px;color:#9ca3af;margin:0 2px;">/</span>
                        <span style="font-size:13px;color:#3b82f6;">%d°</span>
                      </td>
                    </tr>""".formatted(
                        d.emoji(),
                        dayName, dayDate, badge,
                        d.description(), precipitation,
                        d.maxTemp(), d.minTemp()
                ));
            } else {
                // Dan je van dosega prognoze — prikaži placeholder
                rows.append("""
                    <tr style="border-bottom:1px solid #f3f4f6;">
                      <td style="padding:11px 0;width:28px;text-align:center;font-size:22px;vertical-align:middle;opacity:0.35;">🌡️</td>
                      <td style="padding:11px 8px;vertical-align:middle;">
                        <div style="font-size:13px;font-weight:600;color:#9ca3af;">%s %s%s</div>
                        <div style="font-size:11px;color:#d1d5db;margin-top:2px;">Prognoza dostupna bliže datumu</div>
                      </td>
                      <td style="padding:11px 0;text-align:right;vertical-align:middle;white-space:nowrap;">
                        <span style="font-size:12px;color:#d1d5db;">— / —</span>
                      </td>
                    </tr>""".formatted(dayName, dayDate, badge));
            }

            cursor = cursor.plusDays(1);
        }

        // Napomena ako deo putovanja nije pokriven prognozom
        String caveat = "";
        if (retDate.isAfter(lastForecastDate)) {
            long missing = ChronoUnit.DAYS.between(lastForecastDate, retDate);
            caveat = """
                <div style="margin-top:12px;font-size:11px;color:#9ca3af;line-height:1.5;">
                  ℹ️ Prognoza poslednja %d %s putovanja biće dostupna kako se datum polaska bude približavao.
                </div>""".formatted(missing, missing == 1 ? "dan" : "dana");
        }

        return """
            <table width="100%%" cellpadding="0" cellspacing="0"
              style="background:#fff;border:1px solid #e5e7eb;border-radius:12px;margin-bottom:16px;">
              <tr><td style="padding:20px 24px;">
                <div style="font-size:12px;font-weight:700;color:#6b7280;letter-spacing:1px;text-transform:uppercase;margin-bottom:14px;">
                  🗓 Tokom putovanja
                </div>
                <table width="100%%" cellpadding="0" cellspacing="0">%s</table>
                %s
              </td></tr>
            </table>""".formatted(rows.toString(), caveat);
    }
}
