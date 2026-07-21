package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.model.LaunchSubscriber;
import com.escapii.service.email.DigestEmailService;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Jutarnji digest timu. Okvir (header/statistika/footer) dolazi iz MJML
 * template-a /email/jutarnji-pregled.html, a dinamički blokovi (liste
 * rezervacija) se generišu ovde i ubrizgavaju kroz {{BLOCK_*}} tokene -
 * MJML je statički kompajler pa ne može petlje.
 *
 * Svi generisani blokovi su namerno građeni tako da rade BEZ media query-ja
 * (Gmail app ih ignoriše): maksimalno 2 kolone po redu, ostalo se slaže
 * jedno ispod drugog.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigestEmailServiceImpl implements DigestEmailService {

    private final EmailSender sender;

    @Value("${app.ops-email}")
    private String opsEmail;

    private static final DateTimeFormatter SHORT_FMT = DateTimeFormatter.ofPattern("d. MM.");

    @Override
    public void sendDailyDigest(LocalDate today,
                                List<Booking> revealsSent,
                                List<Booking> forecastDue,
                                List<Booking> upcoming,
                                List<Booking> revealBoxPending,
                                List<Booking> revealedAndViewed,
                                List<Booking> notViewedUrgent,
                                List<LaunchSubscriber> newLaunchSubscribers) {

        String todayStr = today.format(EmailHtmlBuilder.DATE_FMT);

        Set<Long> revealIds   = ids(revealsSent);
        Set<Long> forecastIds = ids(forecastDue);
        Set<Long> boxIds      = ids(revealBoxPending);
        Set<Long> viewedIds   = ids(revealedAndViewed);
        Set<Long> urgentIds   = ids(notViewedUrgent);

        boolean hasActions = !revealsSent.isEmpty() || !forecastDue.isEmpty()
                          || !revealBoxPending.isEmpty() || !revealedAndViewed.isEmpty()
                          || !notViewedUrgent.isEmpty();

        String noActionBlock = hasActions ? "" : """
            <table width="100%" cellpadding="0" cellspacing="0" style="margin-bottom:18px;">
              <tr><td style="background:#eef6f0;border:1px solid #c3d8c9;border-left:4px solid #1d6042;\
            border-radius:8px;padding:16px 20px;font-size:14px;color:#1d6042;font-weight:700;">
                &#9989; Danas nema akcija - sve je u redu!
              </td></tr>
            </table>""";

        String html = loadTemplate("jutarnji-pregled.html")
            .replace("{{DATE}}",                 esc(todayStr))
            .replace("{{ACTIVE_COUNT}}",         String.valueOf(upcoming.size()))
            .replace("{{STAT_TOTAL}}",           String.valueOf(upcoming.size()))
            .replace("{{STAT_REVEAL}}",          String.valueOf(revealsSent.size()))
            .replace("{{STAT_REVEAL_COLOR}}",    revealsSent.isEmpty()      ? "#a89888" : "#1d6042")
            .replace("{{STAT_FORECAST}}",        String.valueOf(forecastDue.size()))
            .replace("{{STAT_FORECAST_COLOR}}",  forecastDue.isEmpty()      ? "#a89888" : "#a85e44")
            .replace("{{STAT_BOX}}",             String.valueOf(revealBoxPending.size()))
            .replace("{{STAT_BOX_COLOR}}",       revealBoxPending.isEmpty() ? "#a89888" : "#a85e44")
            .replace("{{STAT_URGENT}}",          String.valueOf(notViewedUrgent.size()))
            .replace("{{STAT_URGENT_COLOR}}",    notViewedUrgent.isEmpty()  ? "#a89888" : "#9b3a2a")
            .replace("{{BLOCK_LAUNCH}}",         newLaunchSubscribers.isEmpty() ? "" : launchSubscribersCard(newLaunchSubscribers))
            .replace("{{BLOCK_NOACTION}}",       noActionBlock)
            .replace("{{BLOCK_URGENT}}",         notViewedUrgent.isEmpty() ? "" : urgentAlert(notViewedUrgent, today))
            .replace("{{BLOCK_TIMELINE}}",       upcoming.isEmpty() ? ""
                    : timeline(today, upcoming, revealIds, forecastIds, boxIds, viewedIds, urgentIds));

        sender.send(opsEmail, "📋 Escapii - " + todayStr, html);
        log.info("[Digest] Poslan. Reveal: {}, Prognoza: {}, Preview: {}",
                revealsSent.size(), forecastDue.size(), upcoming.size());
    }

    // ── Launch notify prijave (privremeno - uklanja se posle lansiranja) ────────

    private String launchSubscribersCard(List<LaunchSubscriber> subs) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < subs.size(); i++) {
            String border = i < subs.size() - 1 ? "border-bottom:1px solid #f0e8dc;" : "";
            rows.append("""
                <tr>
                  <td style="padding:9px 14px;font-size:13px;color:#1a1410;word-break:break-all;%s">%s</td>
                </tr>""".formatted(border, esc(subs.get(i).getEmail())));
        }
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" \
            style="border:1px solid #bcd0d6;border-radius:8px;overflow:hidden;margin-bottom:18px;background:#fff;">
              <tr><td style="padding:11px 14px;font-size:13px;font-weight:700;\
            background:#eaf0f3;color:#1f4a57;border-bottom:1px solid #bcd0d6;">
                &#128640; Nove prijave za obaveštenje o lansiranju (%d)
              </td></tr>
              %s
            </table>""".formatted(subs.size(), rows);
    }

    // ── Urgent alert ──────────────────────────────────────────────────────────

    private String urgentAlert(List<Booking> bookings, LocalDate today) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < bookings.size(); i++) {
            Booking b   = bookings.get(i);
            LocalDate dep = b.getSelectedDate().getDepartureDate();
            long days   = ChronoUnit.DAYS.between(today, dep);
            String when = days == 0 ? "DANAS!" : days == 1 ? "SUTRA!" : "za " + days + " dana";
            String border = i < bookings.size() - 1 ? "border-bottom:1px solid #f5c6c6;" : "";

            // Maks 2 kolone - ime levo, rok desno; ref/aerodrom u drugom redu
            rows.append("""
                <tr><td style="padding:10px 14px;%s">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr>
                      <td style="font-size:13px;font-weight:700;color:#9b3a2a;">%s</td>
                      <td style="font-size:12px;font-weight:700;color:#9b3a2a;text-align:right;white-space:nowrap;">%s</td>
                    </tr>
                    <tr>
                      <td colspan="2" style="font-size:12px;color:#c07d6d;padding-top:3px;">%s &middot; %s</td>
                    </tr>
                  </table>
                </td></tr>""".formatted(
                    border,
                    esc(b.getFirstName() + " " + b.getLastName()),
                    when,
                    esc(b.getBookingRef()),
                    esc(b.getDepartureAirport())));
        }
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" \
            style="border:1px solid #f5c6c6;border-radius:8px;overflow:hidden;margin-bottom:18px;background:#fff8f8;">
              <tr><td style="padding:11px 16px;font-size:13px;font-weight:700;\
            background:#fff0f0;color:#9b3a2a;border-bottom:1px solid #f5c6c6;">
                &#128680; HITNO: korisnik nije otvorio reveal, polazak &le; 2 dana (%d)
              </td></tr>
              %s
            </table>""".formatted(bookings.size(), rows);
    }

    // ── Timeline ──────────────────────────────────────────────────────────────

    private String timeline(LocalDate today, List<Booking> upcoming,
                             Set<Long> revealIds, Set<Long> forecastIds,
                             Set<Long> boxIds, Set<Long> viewedIds, Set<Long> urgentIds) {

        Map<LocalDate, List<Booking>> byDate = upcoming.stream()
            .sorted(Comparator.comparing(b -> b.getSelectedDate().getDepartureDate()))
            .collect(Collectors.groupingBy(
                b -> b.getSelectedDate().getDepartureDate(),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        StringBuilder sb = new StringBuilder();
        sb.append("""
            <div style="font-size:10px;font-weight:700;letter-spacing:1px;\
            text-transform:uppercase;color:#a89888;padding-bottom:12px;">&#128197; Narednih 14 dana</div>""");

        for (Map.Entry<LocalDate, List<Booking>> entry : byDate.entrySet()) {
            LocalDate date    = entry.getKey();
            List<Booking> grp = entry.getValue();
            long daysLeft     = ChronoUnit.DAYS.between(today, date);

            boolean hasUrgent = grp.stream().anyMatch(b -> urgentIds.contains(b.getId()));
            boolean hasAction = grp.stream().anyMatch(b ->
                viewedIds.contains(b.getId()) || boxIds.contains(b.getId()));

            String hBg     = hasUrgent ? "#fff0f0" : hasAction ? "#fffbf3" : "#f5f0e8";
            String hColor  = hasUrgent ? "#9b3a2a" : hasAction ? "#7a4e1e" : "#3d2e1a";
            String hBorder = hasUrgent ? "#f5c6c6" : hasAction ? "#e8c7b1" : "#e0d5c0";

            String pillBg    = daysLeft == 0 ? "#fff0f0" : daysLeft <= 2 ? "#fff5eb" : "#eaf0f3";
            String pillColor = daysLeft == 0 ? "#9b3a2a" : daysLeft <= 2 ? "#a85e44" : "#1f4a57";
            String pillText  = daysLeft == 0 ? "DANAS" : daysLeft == 1 ? "SUTRA" : "za " + daysLeft + " dana";

            sb.append("""
                <table width="100%%" cellpadding="0" cellspacing="0" \
                style="border:1px solid %s;border-radius:8px;overflow:hidden;margin-bottom:14px;background:#fff;">
                  <tr>
                    <td style="padding:10px 14px;background:%s;border-bottom:1px solid %s;">
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr>
                          <td style="font-size:13px;font-weight:700;color:%s;">%s
                            <span style="font-size:12px;font-weight:400;">%s</span>
                          </td>
                          <td style="text-align:right;white-space:nowrap;">
                            <span style="display:inline-block;padding:3px 10px;border-radius:100px;\
                font-size:11px;font-weight:700;background:%s;color:%s;">%s</span>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                  %s
                </table>""".formatted(
                    hBorder,
                    hBg, hBorder,
                    hColor, date.format(SHORT_FMT),
                    dayName(date.getDayOfWeek()),
                    pillBg, pillColor, pillText,
                    bookingRows(grp, revealIds, forecastIds, boxIds, viewedIds, urgentIds)
                ));
        }
        return sb.toString();
    }

    private String bookingRows(List<Booking> bookings,
                                Set<Long> revealIds, Set<Long> forecastIds,
                                Set<Long> boxIds, Set<Long> viewedIds, Set<Long> urgentIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bookings.size(); i++) {
            Booking b         = bookings.get(i);
            Long id           = b.getId();
            boolean isUrgent  = urgentIds.contains(id);
            boolean needsConf = viewedIds.contains(id);
            boolean needsBox  = boxIds.contains(id);
            boolean revealOk  = revealIds.contains(id);
            boolean forecastOk = forecastIds.contains(id);

            String rowBg  = isUrgent ? "#fff8f8" : (needsConf || needsBox) ? "#fffdf7" : "#ffffff";
            String border = i < bookings.size() - 1 ? "border-bottom:1px solid #f0e8dc;" : "";

            StringBuilder badges = new StringBuilder();
            if (isUrgent)   badges.append(badge("&#128680; Nije otvorio!", "#fff0f0", "#9b3a2a"));
            if (needsConf)  badges.append(badge("&#128206; Uploaduj dokument", "#eef6f0", "#1d6042"));
            if (needsBox)   badges.append(badge("&#128230; Pošalji kutiju", "#fff5eb", "#a85e44"));
            if (revealOk)   badges.append(badge("&#9993; Reveal poslan", "#eef3ff", "#2b5fd9"));
            if (forecastOk) badges.append(badge("&#127780; Prognoza poslata", "#f3f0ff", "#5b3ea8"));
            if (badges.isEmpty()) badges.append(badge("Nema akcija danas", "#f5f5f5", "#a89888"));

            // Maks 2 kolone (ime | ref), bedževi u zasebnom redu - čitljivo i na 320px
            sb.append("""
                <tr>
                  <td style="padding:11px 14px;background:%s;%s">
                    <table width="100%%" cellpadding="0" cellspacing="0">
                      <tr>
                        <td style="font-size:13px;vertical-align:top;">
                          <strong style="color:#2D5F6B;">%s</strong>
                          <div style="font-size:11px;color:#a89888;padding-top:2px;word-break:break-all;">%s</div>
                        </td>
                        <td style="font-size:12px;text-align:right;white-space:nowrap;vertical-align:top;">
                          <span style="font-weight:700;color:#a85e44;">%s</span>
                          <span style="display:inline-block;padding:2px 7px;border-radius:100px;\
                font-size:10px;font-weight:700;background:#f0f0f0;color:#666;">%s</span>
                        </td>
                      </tr>
                      <tr><td colspan="2" style="padding-top:8px;">%s</td></tr>
                    </table>
                  </td>
                </tr>""".formatted(
                    rowBg, border,
                    esc(b.getFirstName() + " " + b.getLastName()),
                    esc(b.getEmail()),
                    esc(b.getBookingRef()),
                    esc(b.getDepartureAirport()),
                    badges));
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String badge(String text, String bg, String color) {
        return "<span style=\"display:inline-block;margin:0 4px 4px 0;padding:3px 9px;"
             + "border-radius:100px;font-size:11px;font-weight:700;background:" + bg
             + ";color:" + color + ";white-space:nowrap;\">" + text + "</span>";
    }

    private Set<Long> ids(List<Booking> list) {
        return list.stream().map(Booking::getId).collect(Collectors.toSet());
    }

    private String esc(String s) {
        return EmailHtmlBuilder.esc(s);
    }

    private String dayName(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY    -> "Ponedeljak";
            case TUESDAY   -> "Utorak";
            case WEDNESDAY -> "Sreda";
            case THURSDAY  -> "Četvrtak";
            case FRIDAY    -> "Petak";
            case SATURDAY  -> "Subota";
            case SUNDAY    -> "Nedelja";
        };
    }

    /** Učitava MJML-kompajlirani HTML template iz src/main/resources/email/. */
    private static String loadTemplate(String filename) {
        try (var is = DigestEmailServiceImpl.class.getResourceAsStream("/email/" + filename)) {
            if (is == null) {
                throw new IllegalStateException("Email template nije pronađen: /email/" + filename);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Nije moguće učitati email template: " + filename, e);
        }
    }
}
