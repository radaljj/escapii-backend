package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.service.email.ForecastEmailService;
import com.escapii.util.LogUtils;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.weather.DailyForecast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public void sendForecastEmail(Booking booking, List<DailyForecast> forecast) {
        try {
            String firstName = EmailHtmlBuilder.esc(booking.getFirstName());
            LocalDate depDate = booking.getSelectedDate().getDepartureDate();
            LocalDate retDate = booking.getSelectedDate().getReturnDate();
            String depDateStr = depDate.format(EmailHtmlBuilder.DATE_FMT);

            DailyForecast today = forecast.get(0);
            String subject = "🌤 Tvoja prognoza za putovanje - " + depDateStr + " | Escapii";
            long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), depDate);
            String html = buildHtml(firstName, depDate, retDate, depDateStr, daysUntil, forecast, today);

            sender.send(booking.getEmail(), subject, html);
            log.info("[Forecast] ✅ Email poslan za {} ({})", booking.getBookingRef(), LogUtils.maskEmail(booking.getEmail()));
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
            <!-- Hero weather card - light -->
            <table width="100%%" cellpadding="0" cellspacing="0" style="border-radius:16px;overflow:hidden;margin-bottom:16px;background:#faf6ee;border:1px solid #ebe1cf;">
              <tr><td style="padding:32px 28px 28px;">

                <!-- Pozdrav -->
                <p style="margin:0 0 24px;font-size:13px;color:#6b5d4f;letter-spacing:0.5px;">
                  Zdravo, %s! Tvoje putovanje je za <strong style="color:#2D5F6B;">%d %s</strong> - evo šta te čeka.
                </p>

                <!-- Glavna temperatura -->
                <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:24px;">
                  <tr>
                    <td width="65%%" style="width:65%%;vertical-align:middle;">
                      <div style="font-size:10px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;margin-bottom:8px;">Trenutno vreme</div>
                      <div style="font-size:72px;line-height:1;margin-bottom:4px;">%s</div>
                      <div style="font-family:Georgia,serif;font-size:48px;font-weight:300;color:#1a1410;line-height:1;">%d°</div>
                      <div style="font-size:15px;color:#6b5d4f;margin-top:8px;">%s</div>
                    </td>
                    <td width="35%%" style="width:35%%;text-align:right;vertical-align:top;">
                      <div style="background:#ffffff;border:1px solid #ebe1cf;border-radius:12px;padding:12px 16px;display:inline-block;">
                        <div style="font-size:11px;color:#a89888;margin-bottom:6px;letter-spacing:0.5px;">POLAZAK</div>
                        <div style="font-size:13px;font-weight:700;color:#2D5F6B;">%s</div>
                      </div>
                    </td>
                  </tr>
                </table>

                <!-- Min/Max bar -->
                <table cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
                  <tr>
                    <td style="background:#ffffff;border:1px solid #ebe1cf;border-radius:100px;padding:5px 14px;margin-right:8px;">
                      <span style="font-size:12px;color:#a89888;">↑ </span>
                      <span style="font-size:13px;font-weight:700;color:#9b3a2a;">%d°</span>
                    </td>
                    <td style="width:8px;"></td>
                    <td style="background:#ffffff;border:1px solid #ebe1cf;border-radius:100px;padding:5px 14px;">
                      <span style="font-size:12px;color:#a89888;">↓ </span>
                      <span style="font-size:13px;font-weight:700;color:#1f4a57;">%d°</span>
                    </td>
                  </tr>
                </table>

                <!-- Separator -->
                <div style="height:1px;background:#ebe1cf;margin-bottom:20px;"></div>

                <!-- Forecast strip (travel days only) -->
                <table width="100%%" cellpadding="0" cellspacing="0">
                  <tr>%s</tr>
                </table>

              </td></tr>
            </table>

            <!-- Travel days breakdown -->
            %s

            <!-- Reveal note -->
            <table width="100%%" cellpadding="0" cellspacing="0"
              style="background:#faf6ee;border:1px solid #ebe1cf;border-left:3px solid #a85e44;border-radius:8px;margin-bottom:12px;">
              <tr><td style="padding:14px 18px;">
                <div style="font-size:12px;font-weight:700;color:#a85e44;margin-bottom:4px;">📬 Preporuka</div>
                <div style="font-size:12px;color:#1a1410;line-height:1.6;">
                  Kada dobiješ email sa otkrićem destinacije,
                  <strong>preporučujemo da ponovo proveriš prognozu</strong> direktno za tu destinaciju -
                  prognoza za toliko dana unapred može biti okvirna.
                </div>
              </td></tr>
            </table>

            <!-- Footer note -->
            <p style="font-size:11px;color:#a89888;text-align:center;margin:8px 0 0;line-height:1.6;">
              Prognoza se ažurira svakodnevno - moguća su manja odstupanja.<br>
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
            "#a85e44",
            "#0a1628",
            EmailHtmlBuilder.statusBadge("Prognoza", "orange"),
            "Tvoja vremenska prognoza",
            "Putovanje · " + depDateStr,
            "",
            body,
            EmailHtmlBuilder.customerFooter("escapii.team@gmail.com"),
            false
        );
    }

    // ── Travel days forecast strip ───────────────────────────────────────────

    private String buildDayCards(List<DailyForecast> forecast, LocalDate depDate, LocalDate retDate) {
        StringBuilder sb = new StringBuilder();
        Locale sr = new Locale("sr", "RS");
        LocalDate today = LocalDate.now();

        for (int i = 0; i < forecast.size(); i++) {
            DailyForecast d = forecast.get(i);
            if (d.date().isBefore(depDate) || d.date().isAfter(retDate)) continue;

            boolean isDep   = d.date().equals(depDate);
            boolean isRet   = d.date().equals(retDate);
            boolean isToday = d.date().equals(today);

            String label;
            String bg;

            if (isDep) {
                label = "✈ POLAZAK";
                bg = "background:#fff5eb;border:1px solid #e8c7b1;";
            } else if (isRet) {
                label = "🏠 POVRATAK";
                bg = "background:#eef6f0;border:1px solid #c3d8c9;";
            } else if (isToday) {
                label = "Danas";
                bg = "background:#eaf0f3;border:1px solid #bcd0d6;";
            } else {
                label = d.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, sr);
                bg = "background:#ffffff;border:1px solid #ebe1cf;";
            }

            String dayDate = d.date().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"));

            String labelStyle = (isDep || isRet)
                ? "font-size:8px;font-weight:700;letter-spacing:0.3px;text-transform:uppercase;margin-bottom:2px;"
                  + (isDep ? "color:#a85e44;" : "color:#1d6042;")
                : "font-size:9px;color:#a89888;margin-bottom:2px;text-transform:uppercase;letter-spacing:0.5px;";

            String tempStyle = (isDep || isRet)
                ? "font-size:13px;font-weight:800;"
                : "font-size:12px;font-weight:700;";

            sb.append("""
                <td width="60" style="text-align:center;padding:0 2px;">
                  <div style="%sborder-radius:12px;padding:10px 4px;">
                    <div style="%s">%s</div>
                    <div style="font-size:8px;color:#a89888;margin-bottom:5px;">%s</div>
                    <div style="font-size:20px;margin-bottom:5px;">%s</div>
                    <div style="%scolor:#9b3a2a;">%d°</div>
                    <div style="font-size:10px;color:#1f4a57;margin-top:2px;">%d°</div>
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
                        ? "<div style=\"font-size:9px;color:#2D5F6B;margin-top:3px;\">💧" + String.format("%.0f", d.precipitation()) + "mm</div>"
                        : ""
            ));
        }
        return sb.toString();
    }

    // ── Travel days breakdown ─────────────────────────────────────────────────

    private String buildTravelDaysCard(List<DailyForecast> forecast, LocalDate depDate, LocalDate retDate) {
        Locale sr = new Locale("sr", "RS");
        java.time.format.DateTimeFormatter dayFmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.");

        LocalDate lastForecastDate = forecast.isEmpty() ? depDate : forecast.get(forecast.size() - 1).date();

        java.util.Map<LocalDate, DailyForecast> byDate = new java.util.HashMap<>();
        forecast.forEach(d -> byDate.put(d.date(), d));

        if (byDate.isEmpty()) return "";

        StringBuilder rows = new StringBuilder();

        LocalDate cursor = depDate;
        while (!cursor.isAfter(retDate)) {
            boolean isDep = cursor.equals(depDate);
            boolean isRet = cursor.equals(retDate);
            DailyForecast d = byDate.get(cursor);

            String dayName = cursor.getDayOfWeek().getDisplayName(TextStyle.FULL, sr);
            dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
            String dayDate = cursor.format(dayFmt);

            String badge = "";
            if (isDep) badge = "<span style=\"background:#fff5eb;color:#a85e44;font-size:9px;font-weight:700;"
                    + "border-radius:4px;padding:1px 5px;margin-left:6px;vertical-align:middle;\">✈ POLAZAK</span>";
            else if (isRet) badge = "<span style=\"background:#eef6f0;color:#1d6042;font-size:9px;font-weight:700;"
                    + "border-radius:4px;padding:1px 5px;margin-left:6px;vertical-align:middle;\">🏠 POVRATAK</span>";

            if (d != null) {
                String precipitation = d.precipitation() > 0.5
                        ? "<span style=\"font-size:11px;color:#6b5d4f;\"> · 💧" + String.format("%.0f", d.precipitation()) + "mm</span>"
                        : "";

                rows.append("""
                    <tr style="border-bottom:1px solid #ebe1cf;">
                      <td width="32" style="width:32px;padding:11px 0;text-align:center;font-size:22px;vertical-align:middle;">%s</td>
                      <td style="padding:11px 8px;vertical-align:middle;">
                        <div style="font-size:13px;font-weight:600;color:#1a1410;">%s %s%s</div>
                        <div style="font-size:11px;color:#a89888;margin-top:2px;">%s%s</div>
                      </td>
                      <td width="80" style="width:80px;padding:11px 0;text-align:right;vertical-align:middle;white-space:nowrap;">
                        <span style="font-size:14px;font-weight:700;color:#9b3a2a;">%d°</span>
                        <span style="font-size:12px;color:#a89888;margin:0 2px;">/</span>
                        <span style="font-size:13px;color:#1f4a57;">%d°</span>
                      </td>
                    </tr>""".formatted(
                        d.emoji(),
                        dayName, dayDate, badge,
                        d.description(), precipitation,
                        d.maxTemp(), d.minTemp()
                ));
            } else {
                rows.append("""
                    <tr style="border-bottom:1px solid #ebe1cf;">
                      <td style="padding:11px 0;width:28px;text-align:center;font-size:22px;vertical-align:middle;opacity:0.35;">🌡️</td>
                      <td style="padding:11px 8px;vertical-align:middle;">
                        <div style="font-size:13px;font-weight:600;color:#a89888;">%s %s%s</div>
                        <div style="font-size:11px;color:#ebe1cf;margin-top:2px;">Prognoza dostupna bliže datumu</div>
                      </td>
                      <td style="padding:11px 0;text-align:right;vertical-align:middle;white-space:nowrap;">
                        <span style="font-size:12px;color:#ebe1cf;">- / -</span>
                      </td>
                    </tr>""".formatted(dayName, dayDate, badge));
            }

            cursor = cursor.plusDays(1);
        }

        String caveat = "";
        if (retDate.isAfter(lastForecastDate)) {
            long missing = ChronoUnit.DAYS.between(lastForecastDate, retDate);
            caveat = """
                <div style="margin-top:12px;font-size:11px;color:#a89888;line-height:1.5;">
                  ℹ️ Prognoza poslednja %d %s putovanja biće dostupna kako se datum polaska bude približavao.
                </div>""".formatted(missing, missing == 1 ? "dan" : "dana");
        }

        return """
            <table width="100%%" cellpadding="0" cellspacing="0"
              style="background:#fff;border:1px solid #ebe1cf;border-radius:12px;margin-bottom:16px;">
              <tr><td style="padding:20px 24px;">
                <div style="font-size:12px;font-weight:700;color:#a89888;letter-spacing:1px;text-transform:uppercase;margin-bottom:14px;">
                  🗓 Tokom putovanja
                </div>
                <table width="100%%" cellpadding="0" cellspacing="0">%s</table>
                %s
              </td></tr>
            </table>""".formatted(rows.toString(), caveat);
    }
}
