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
import java.time.temporal.ChronoUnit;
import java.time.format.TextStyle;
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
            String depDate   = booking.getSelectedDate().getDepartureDate()
                                      .format(EmailHtmlBuilder.DATE_FMT);

            DailyForecast today = forecast.get(0);
            String subject = "🌤 Tvoja prognoza za putovanje — " + depDate + " | Escapii";
            long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(),
                                booking.getSelectedDate().getDepartureDate());
            String html    = buildHtml(firstName, depDate, daysUntil, forecast, today);

            sender.send(booking.getEmail(), subject, html);
            log.info("[Forecast] ✅ Email poslan za {} ({})", booking.getBookingRef(), booking.getEmail());
        } catch (Exception e) {
            log.error("[Forecast] ❌ Greška pri slanju za {}: {}", booking.getBookingRef(), e.getMessage(), e);
        }
    }

    // ── HTML template ─────────────────────────────────────────────────────────

    private String buildHtml(String firstName, String depDate, long daysUntil,
                             List<DailyForecast> forecast, DailyForecast today) {

        String packingTips = buildPackingTips(forecast);
        String dayCards    = buildDayCards(forecast);

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

                <!-- 7-day forecast -->
                <table width="100%%" cellpadding="0" cellspacing="0">
                  <tr>%s</tr>
                </table>

              </td></tr>
            </table>

            <!-- Packing tips -->
            %s

            <!-- Reveal note -->
            <table width="100%%" cellpadding="0" cellspacing="0"
              style="background:#fff8f0;border:1px solid #fed7aa;border-left:3px solid #CA8A71;border-radius:8px;margin-bottom:12px;">
              <tr><td style="padding:14px 18px;">
                <div style="font-size:12px;font-weight:700;color:#CA8A71;margin-bottom:4px;">📬 Preporuka</div>
                <div style="font-size:12px;color:#374151;line-height:1.6;">
                  Kada dobiješ email sa otkrićem destinacije (3 dana pre polaska),
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
                depDate,
                today.maxTemp(), today.minTemp(),
                dayCards,
                packingTips
        );

        return EmailHtmlBuilder.wrapBase(
            "#0a1628",
            "Tvoja vremenska prognoza",
            "Putovanje · " + depDate,
            "",
            body,
            EmailHtmlBuilder.customerFooter("podrska@escapii.com"),
            false
        );
    }

    // ── 11-day cards ──────────────────────────────────────────────────────────

    private String buildDayCards(List<DailyForecast> forecast) {
        StringBuilder sb = new StringBuilder();
        Locale sr = new Locale("sr", "RS");

        for (int i = 0; i < forecast.size(); i++) {
            DailyForecast d = forecast.get(i);
            boolean isFirst = i == 0;
            String dayName  = isFirst ? "Danas"
                    : d.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, sr);
            // Datum ispod dana (npr. "25.04")
            String dayDate  = d.date().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"));
            String bg       = isFirst
                    ? "background:rgba(125,211,252,0.15);border:1px solid rgba(125,211,252,0.25);"
                    : "background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.08);";

            sb.append("""
                <td style="text-align:center;padding:0 2px;">
                  <div style="%sborder-radius:12px;padding:10px 4px;">
                    <div style="font-size:9px;color:rgba(255,255,255,0.5);margin-bottom:2px;text-transform:uppercase;letter-spacing:0.5px;">%s</div>
                    <div style="font-size:8px;color:rgba(255,255,255,0.3);margin-bottom:5px;">%s</div>
                    <div style="font-size:20px;margin-bottom:5px;">%s</div>
                    <div style="font-size:12px;font-weight:700;color:#fca5a5;">%d°</div>
                    <div style="font-size:10px;color:#93c5fd;margin-top:2px;">%d°</div>
                    %s
                  </div>
                </td>""".formatted(
                    bg,
                    dayName,
                    dayDate,
                    d.emoji(),
                    d.maxTemp(),
                    d.minTemp(),
                    d.precipitation() > 0.5
                        ? "<div style=\"font-size:9px;color:#7dd3fc;margin-top:3px;\">💧" + String.format("%.0f", d.precipitation()) + "mm</div>"
                        : ""
            ));
        }
        return sb.toString();
    }

    // ── Packing tips ──────────────────────────────────────────────────────────

    private String buildPackingTips(List<DailyForecast> forecast) {
        boolean hasRain    = forecast.stream().anyMatch(d -> d.precipitation() > 1.0);
        boolean hotDays    = forecast.stream().anyMatch(d -> d.maxTemp() >= 28);
        boolean coolNights = forecast.stream().anyMatch(d -> d.minTemp() < 14);
        boolean hasSnow    = forecast.stream().anyMatch(d -> d.weatherCode() >= 71 && d.weatherCode() <= 77);
        boolean hasStorm   = forecast.stream().anyMatch(d -> d.weatherCode() >= 95);
        int avgMax         = (int) forecast.stream().mapToInt(DailyForecast::maxTemp).average().orElse(20);

        if (!hasRain && !hotDays && !coolNights && !hasSnow && !hasStorm) {
            return """
                <table width="100%%" cellpadding="0" cellspacing="0"
                  style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:12px;margin-bottom:16px;">
                  <tr><td style="padding:20px 24px;">
                    <div style="font-size:13px;font-weight:700;color:#15803d;margin-bottom:8px;">✅ Odlično vreme te čeka!</div>
                    <div style="font-size:13px;color:#166534;line-height:1.6;">Prognoza izgleda stabilno i lepo. Lagano pakovanje je sve što trebaš.</div>
                  </td></tr>
                </table>""";
        }

        StringBuilder tips = new StringBuilder();
        if (hotDays)    tips.append(tip("🧴", "Sunčana krema je obavezna",  avgMax + "° — visoke temperature"));
        if (hotDays)    tips.append(tip("😎", "Naočare za sunce",            "Jak UV indeks tokom dana"));
        if (hasRain)    tips.append(tip("🌂", "Ponesi kišobran",             "Očekuju se padavine"));
        if (coolNights) tips.append(tip("🧥", "Lagana jakna za veče",        "Noći su svežije od " + forecast.stream().mapToInt(DailyForecast::minTemp).min().orElse(12) + "°"));
        if (hasSnow)    tips.append(tip("🧤", "Tople rukavice i kapa",       "Moguć sneg"));
        if (hasStorm)   tips.append(tip("⚡", "Prati upozorenja",            "Moguća grmljavinska nevremena"));

        return """
            <table width="100%%" cellpadding="0" cellspacing="0"
              style="background:#fff;border:1px solid #e5e7eb;border-radius:12px;margin-bottom:16px;">
              <tr><td style="padding:20px 24px;">
                <div style="font-size:13px;font-weight:700;color:#1f2937;margin-bottom:14px;">🎒 Šta poneti?</div>
                %s
              </td></tr>
            </table>""".formatted(tips.toString());
    }

    private String tip(String icon, String title, String sub) {
        return """
            <table cellpadding="0" cellspacing="0" style="margin-bottom:10px;width:100%%;">
              <tr>
                <td style="width:32px;vertical-align:top;padding-top:1px;">
                  <span style="font-size:18px;">%s</span>
                </td>
                <td style="vertical-align:top;">
                  <div style="font-size:13px;font-weight:600;color:#1f2937;">%s</div>
                  <div style="font-size:11px;color:#9ca3af;margin-top:1px;">%s</div>
                </td>
              </tr>
            </table>""".formatted(icon, title, sub);
    }
}
