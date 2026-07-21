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

        StringBuilder body = new StringBuilder();

        body.append(metaBar(upcoming.size(), revealsSent.size(), forecastDue.size(),
                            revealBoxPending.size(), notViewedUrgent.size()));

        if (!newLaunchSubscribers.isEmpty()) {
            body.append(launchSubscribersCard(newLaunchSubscribers));
        }

        if (!hasActions) {
            body.append("""
                <div style="background:#eef6f0;border:1px solid #c3d8c9;border-left:4px solid #1d6042;\
                border-radius:8px;padding:16px 20px;margin-bottom:20px;">
                  <p style="margin:0;font-size:14px;color:#1d6042;font-weight:700;">✅ Danas nema akcija - sve je u redu!</p>
                </div>""");
        }

        if (!notViewedUrgent.isEmpty()) {
            body.append(urgentAlert(notViewedUrgent, today));
        }

        if (!upcoming.isEmpty()) {
            body.append(timeline(today, upcoming, revealIds, forecastIds, boxIds, viewedIds, urgentIds));
        }

        String html = EmailHtmlBuilder.wrapBase(
            "#2D5F6B", "#1e1b4b",
            EmailHtmlBuilder.statusBadge("Jutarnji pregled", "blue"),
            "Jutarnji pregled",
            todayStr + " &middot; " + upcoming.size() + " aktivnih rezervacija",
            "",
            body.toString(),
            "Escapii interni sistem &middot; Automatska poruka &middot; Ne odgovarati",
            false
        );

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
                <tr style="%s">
                  <td style="padding:9px 14px;font-size:13px;color:#1a1410;">%s</td>
                </tr>""".formatted(border, esc(subs.get(i).getEmail())));
        }
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" \
            style="border:1px solid #bcd0d6;border-radius:8px;overflow:hidden;margin-bottom:20px;background:#fff;">
              <tr><td style="padding:11px 16px;font-size:13px;font-weight:700;\
            background:#eaf0f3;color:#1f4a57;border-bottom:1px solid #bcd0d6;">
                🚀 Nove prijave za obaveštenje o lansiranju (%d)
              </td></tr>
              %s
            </table>""".formatted(subs.size(), rows);
    }

    // ── Meta bar ──────────────────────────────────────────────────────────────

    private String metaBar(int total, int reveals, int forecasts, int boxes, int urgent) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" \
            style="background:#faf6ee;border:1px solid #ebe1cf;border-radius:8px;margin-bottom:20px;">
              <tr>
                <td class="dg-stat" style="padding:12px 14px;border-right:1px solid #ebe1cf;text-align:center;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:4px;">Aktivnih</div>
                  <div style="font-size:20px;font-weight:700;color:#1a1410;">%d</div>
                </td>
                <td class="dg-stat" style="padding:12px 14px;border-right:1px solid #ebe1cf;text-align:center;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:4px;">✉ Reveal</div>
                  <div style="font-size:20px;font-weight:700;color:%s;">%d</div>
                </td>
                <td class="dg-stat" style="padding:12px 14px;border-right:1px solid #ebe1cf;text-align:center;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:4px;">🌤 Prognoza</div>
                  <div style="font-size:20px;font-weight:700;color:%s;">%d</div>
                </td>
                <td class="dg-stat" style="padding:12px 14px;border-right:1px solid #ebe1cf;text-align:center;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:4px;">📦 Kutija</div>
                  <div style="font-size:20px;font-weight:700;color:%s;">%d</div>
                </td>
                <td class="dg-stat" style="padding:12px 14px;text-align:center;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:4px;">🚨 Hitno</div>
                  <div style="font-size:20px;font-weight:700;color:%s;">%d</div>
                </td>
              </tr>
            </table>""".formatted(
                total,
                reveals  > 0 ? "#1d6042" : "#a89888", reveals,
                forecasts > 0 ? "#a85e44" : "#a89888", forecasts,
                boxes    > 0 ? "#a85e44" : "#a89888", boxes,
                urgent   > 0 ? "#9b3a2a" : "#a89888", urgent);
    }

    // ── Urgent alert ──────────────────────────────────────────────────────────

    private String urgentAlert(List<Booking> bookings, LocalDate today) {
        StringBuilder rows = new StringBuilder();
        for (Booking b : bookings) {
            LocalDate dep  = b.getSelectedDate().getDepartureDate();
            long days      = ChronoUnit.DAYS.between(today, dep);
            String when    = days == 0 ? "DANAS!" : days == 1 ? "SUTRA!" : "za " + days + " dana";
            rows.append("""
                <tr>
                  <td class="dg-cell dg-cell-first" style="padding:9px 14px;font-size:13px;font-weight:700;color:#9b3a2a;">%s</td>
                  <td class="dg-cell" style="padding:9px 14px;font-size:12px;color:#9b3a2a;">%s</td>
                  <td class="dg-cell" style="padding:9px 14px;font-size:12px;color:#9b3a2a;">%s</td>
                  <td class="dg-cell dg-cell-last" style="padding:9px 14px;font-size:12px;font-weight:700;color:#9b3a2a;text-align:right;">%s</td>
                </tr>""".formatted(
                    esc(b.getFirstName() + " " + b.getLastName()),
                    esc(b.getBookingRef()),
                    esc(b.getDepartureAirport()),
                    when));
        }
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" \
            style="border:1px solid #f5c6c6;border-radius:8px;overflow:hidden;margin-bottom:20px;background:#fff8f8;">
              <tr><td style="padding:11px 16px;font-size:13px;font-weight:700;\
            background:#fff0f0;color:#9b3a2a;border-bottom:1px solid #f5c6c6;">
                🚨 HITNO: korisnik nije otvorio reveal, polazak ≤ 2 dana (%d)
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
            text-transform:uppercase;color:#a89888;margin-bottom:12px;">📅 Narednih 14 dana</div>""");

        for (Map.Entry<LocalDate, List<Booking>> entry : byDate.entrySet()) {
            LocalDate date    = entry.getKey();
            List<Booking> grp = entry.getValue();
            long daysLeft     = ChronoUnit.DAYS.between(today, date);

            boolean hasUrgent = grp.stream().anyMatch(b -> urgentIds.contains(b.getId()));
            boolean hasAction = grp.stream().anyMatch(b ->
                viewedIds.contains(b.getId()) || boxIds.contains(b.getId()));

            // Header colors
            String hBg     = hasUrgent ? "#fff0f0" : hasAction ? "#fffbf3" : "#f5f0e8";
            String hColor  = hasUrgent ? "#9b3a2a" : hasAction ? "#7a4e1e" : "#3d2e1a";
            String hBorder = hasUrgent ? "#f5c6c6" : hasAction ? "#e8c7b1" : "#e0d5c0";

            // "Za X dana" pill
            String pillBg    = daysLeft == 0 ? "#fff0f0" : daysLeft <= 2 ? "#fff5eb" : "#eaf0f3";
            String pillColor = daysLeft == 0 ? "#9b3a2a" : daysLeft <= 2 ? "#a85e44" : "#1f4a57";
            String pillText  = daysLeft == 0 ? "DANAS" : daysLeft == 1 ? "SUTRA" : "za " + daysLeft + " dana";

            sb.append("""
                <table width="100%%" cellpadding="0" cellspacing="0" \
                style="border:1px solid %s;border-radius:8px;overflow:hidden;margin-bottom:16px;background:#fff;">
                  <tr style="background:%s;">
                    <td style="padding:10px 16px;background:%s;border-bottom:1px solid %s;">
                      <span style="font-size:13px;font-weight:700;color:%s;">%s</span>
                      <span style="font-size:12px;color:%s;margin-left:8px;">%s</span>
                    </td>
                    <td style="padding:10px 16px;text-align:right;white-space:nowrap;\
                background:%s;border-bottom:1px solid %s;">
                      <span style="display:inline-block;padding:3px 10px;border-radius:100px;\
                font-size:11px;font-weight:700;background:%s;color:%s;">%s</span>
                    </td>
                  </tr>
                  %s
                </table>""".formatted(
                    hBorder,
                    hBg,
                    hBg, hBorder,
                    hColor, date.format(SHORT_FMT),
                    hColor, dayName(date.getDayOfWeek()),
                    hBg, hBorder,
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
            if (isUrgent)   badges.append(badge("🚨 Nije otvorio!", "#fff0f0", "#9b3a2a"));
            if (needsConf)  badges.append(badge("📎 Uploaduj dokument", "#eef6f0", "#1d6042"));
            if (needsBox)   badges.append(badge("📦 Pošalji kutiju", "#fff5eb", "#a85e44"));
            if (revealOk)   badges.append(badge("✉ Reveal poslan", "#eef3ff", "#2b5fd9"));
            if (forecastOk) badges.append(badge("🌤 Prognoza poslata", "#f3f0ff", "#5b3ea8"));
            if (badges.isEmpty()) badges.append(badge("Nema akcija danas", "#f5f5f5", "#a89888"));

            sb.append("""
                <tr style="background:%s;%s">
                  <td class="dg-cell dg-cell-first" style="padding:11px 14px;font-size:13px;vertical-align:middle;width:35%%;">
                    <strong style="color:#2D5F6B;">%s</strong>
                    <div style="font-size:11px;color:#a89888;margin-top:2px;">%s</div>
                  </td>
                  <td class="dg-cell" style="padding:11px 14px;font-size:12px;vertical-align:middle;width:18%%;white-space:nowrap;">
                    <span style="font-weight:700;color:#a85e44;">%s</span>
                    &nbsp;
                    <span style="display:inline-block;padding:2px 7px;border-radius:100px;\
                font-size:10px;font-weight:700;background:#f0f0f0;color:#666;">%s</span>
                  </td>
                  <td class="dg-cell dg-cell-last" style="padding:11px 14px;vertical-align:middle;">%s</td>
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
        return "<span style=\"display:inline-block;margin:2px 3px 2px 0;padding:3px 9px;"
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
}
