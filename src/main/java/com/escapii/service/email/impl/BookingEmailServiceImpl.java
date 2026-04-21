package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.model.PassengerInfo;
import com.escapii.service.DestinationService;
import com.escapii.service.email.BookingEmailService;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEmailServiceImpl implements BookingEmailService {

    private final EmailSender sender;
    private final DestinationService destinationService;

    @Value("${app.team-email}")
    private String teamEmail;

    /** ISO kod → srpski naziv, popunjava se iz iste liste koja se šalje frontendu. */
    private Map<String, String> countryNames;

    @PostConstruct
    void initCountryNames() {
        countryNames = destinationService.fetchCountries()
                .stream()
                .collect(Collectors.toMap(c -> c.getCode().toUpperCase(), c -> c.getNameSr()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Javni API
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Async
    public void sendTeamNotification(Booking booking) {
        sender.send(
            teamEmail,
            "Novi upit %s — %s %s".formatted(booking.getBookingRef(), booking.getFirstName(), booking.getLastName()),
            buildTeamEmailHtml(booking)
        );
    }

    @Override
    @Async
    public void sendCustomerConfirmation(Booking booking) {
        sender.send(
            booking.getEmail(),
            "Upit primljen — %s".formatted(booking.getBookingRef()),
            buildCustomerReceivedHtml(booking)
        );
    }

    @Override
    @Async
    public void sendBookingConfirmed(Booking booking) {
        sender.send(
            booking.getEmail(),
            "Rezervacija potvrđena — %s".formatted(booking.getBookingRef()),
            buildCustomerStatusHtml(booking, true)
        );
        log.info("[Email] Poslat CONFIRMED email na adresu {} za booking {}", booking.getEmail(), booking.getBookingRef());
    }

    @Override
    @Async
    public void sendBookingCancelled(Booking booking) {
        sender.send(
            booking.getEmail(),
            "Rezervacija otkazana — %s".formatted(booking.getBookingRef()),
            buildCustomerStatusHtml(booking, false)
        );
        log.info("[Email] Poslat CANCELLED email na adresu {} za booking {}", booking.getEmail(), booking.getBookingRef());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tim — interni email
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildTeamEmailHtml(Booking booking) {
        String depDate = booking.getSelectedDate().getDepartureDate().format(EmailHtmlBuilder.DATE_FMT);
        String retDate = booking.getSelectedDate().getReturnDate().format(EmailHtmlBuilder.DATE_FMT);
        int n = booking.getNumberOfTravelers();

        String body = """
            %s
            %s
            %s
            %s
            %s
            """.formatted(
            metaSection(booking, depDate, retDate, n),
            teamSection("Kontakt",
                tRow("Ime i prezime", "<a href='mailto:" + booking.getEmail() + "' style='color:#2D5F6B;font-weight:700;text-decoration:none;'>" + EmailHtmlBuilder.esc(booking.getFirstName() + " " + booking.getLastName()) + "</a>") +
                tRow("Email", "<a href='mailto:" + booking.getEmail() + "' style='color:#CA8A71;text-decoration:none;'>" + booking.getEmail() + "</a>") +
                tRow("Telefon", "<a href='tel:" + booking.getPhone() + "' style='color:#CA8A71;text-decoration:none;'>" + booking.getPhone() + "</a>")
            ),
            teamSection("Putovanje",
                tRow("Aerodrom", booking.getDepartureAirport()) +
                tRow("Datum", "<strong>" + depDate + " &rarr; " + retDate + "</strong>") +
                tRow("Noći", booking.getSelectedDate().getNumberOfNights() + " noći") +
                tRow("Putnici", n + (n == 1 ? " putnik" : " putnika")) +
                tRow("Smeštaj", EmailHtmlBuilder.resolveAccomLabel(booking.getAccommodationType())) +
                tRow("Isključene dest.", buildExclusionsText(booking)) +
                tRow("Presedanje OK", Boolean.TRUE.equals(booking.getHasConnectingFlights()) ? "✔ Da" : "✘ Ne — samo direktni letovi")
            ),
            buildPassengersSection(booking),
            buildPriceTable(booking, n)
        );

        String notes = buildNotesBox(booking.getNotes());

        return EmailHtmlBuilder.wrapBase(
            "Escapii — Interni",
            "#0D2E38",
            "Novi upit stigao",
            depDate + " → " + retDate + " · " + n + (n == 1 ? " putnik" : " putnika"),
            booking.getBookingRef(),
            "#CA8A71",
            "NOVI UPIT",
            body + notes,
            "Interni email — escapii ops tim · Nije za prosleđivanje",
            false
        );
    }

    private String metaSection(Booking booking, String dep, String ret, int n) {
        String deadline = booking.getCreatedAt().plusHours(24).format(EmailHtmlBuilder.DATETIME_FMT);
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px;background:#f8f9fa;border:1px solid #e5e7eb;border-radius:6px;">
              <tr>
                <td style="padding:12px 16px;border-right:1px solid #e5e7eb;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">Ref. broj</div>
                  <div style="font-size:14px;font-weight:700;color:#2D5F6B;">%s</div>
                </td>
                <td style="padding:12px 16px;border-right:1px solid #e5e7eb;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">Primljeno</div>
                  <div style="font-size:13px;font-weight:600;color:#374151;">%s</div>
                </td>
                <td style="padding:12px 16px;border-right:1px solid #e5e7eb;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">Ukupno</div>
                  <div style="font-size:14px;font-weight:700;color:#CA8A71;">%s €</div>
                </td>
                <td style="padding:12px 16px;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">Rok odgovora</div>
                  <div style="font-size:13px;font-weight:700;color:#dc2626;">%s</div>
                </td>
              </tr>
            </table>
            """.formatted(booking.getBookingRef(), booking.getCreatedAt().format(EmailHtmlBuilder.DATETIME_FMT),
                booking.getTotalPriceAll(), deadline);
    }

    private String teamSection(String title, String rows) {
        return """
            <div style="margin-bottom:28px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;margin-bottom:12px;padding-bottom:8px;border-bottom:2px solid #f3f4f6;">%s</div>
              <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;">%s</table>
            </div>
            """.formatted(title, rows);
    }

    private String tRow(String label, String value) {
        return """
            <tr>
              <td style="padding:9px 0;font-size:13px;color:#9ca3af;font-weight:600;width:38%%;border-bottom:1px solid #f3f4f6;">%s</td>
              <td style="padding:9px 0;font-size:14px;color:#111827;border-bottom:1px solid #f3f4f6;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Korisnički — upit primljen
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildCustomerReceivedHtml(Booking booking) {
        String depDate = booking.getSelectedDate().getDepartureDate().format(EmailHtmlBuilder.DATE_FMT);
        String retDate = booking.getSelectedDate().getReturnDate().format(EmailHtmlBuilder.DATE_FMT);
        int n = booking.getNumberOfTravelers();

        String body = """
            <p style="margin:0 0 20px;font-size:15px;color:#374151;line-height:1.65;">
              Draga/i <strong style="color:#2D5F6B;">%s</strong>,<br><br>
              uspešno smo primili vaš upit za putovanje. Naš tim pregledava vaše preference
              i kontaktiraće vas u roku od <strong style="color:#2D5F6B;">24 sata</strong> sa svim detaljima i potvrdom rezervacije.
            </p>
            %s
            %s
            %s
            """.formatted(
            EmailHtmlBuilder.esc(booking.getFirstName()),
            customerTripCard(booking, depDate, retDate, n),
            EmailHtmlBuilder.totalBox(booking.getTotalPriceAll(), n),
            nextStepsBlock()
        );

        return EmailHtmlBuilder.wrapBase(
            "Escapii",
            "#0D2E38",
            "Vaš upit je primljen",
            "Hvala što ste nam se obratili — naš tim će vas kontaktirati u roku od 24 sata.",
            booking.getBookingRef(),
            "#CA8A71",
            "NA ČEKANJU",
            body,
            EmailHtmlBuilder.customerFooter(sender.getFrom()),
            true
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Korisnički — CONFIRMED / CANCELLED
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildCustomerStatusHtml(Booking booking, boolean confirmed) {
        String depDate = booking.getSelectedDate().getDepartureDate().format(EmailHtmlBuilder.DATE_FMT);
        String retDate = booking.getSelectedDate().getReturnDate().format(EmailHtmlBuilder.DATE_FMT);
        int n = booking.getNumberOfTravelers();

        String accentColor = confirmed ? "#16a34a" : "#dc2626";
        String badgeLabel  = confirmed ? "POTVRĐENA" : "OTKAZANA";
        String heading     = confirmed ? "Rezervacija potvrđena!" : "Rezervacija otkazana";
        String subtitle    = confirmed
            ? "vaša rezervacija je zvanično potvrđena! Sve je spremno — vi samo spakujte stvari i prepustite se misteriji. ✦"
            : "sa žaljenjem vam obaveštavamo da je vaša rezervacija otkazana. Razumemo da su planovi nekad nepredvidivi — i nadamo se da ćete nam ponovo ukazati poverenje.<br><br>Vaša avantura nas čeka — kada budete spremni, mi ćemo biti tu. ✦";

        String content;
        if (confirmed) {
            content = """
                <p style="margin:0 0 20px;font-size:15px;color:#374151;line-height:1.65;">
                  Draga/i <strong style="color:#2D5F6B;">%s</strong>,<br><br>%s
                </p>
                %s
                %s
                %s
                %s
                %s
                """.formatted(
                EmailHtmlBuilder.esc(booking.getFirstName()),
                subtitle,
                customerTripCardStyled(booking, depDate, retDate, n, false),
                EmailHtmlBuilder.totalBox(booking.getTotalPriceAll(), n),
                buildConfirmedTimeline(booking),
                buildPassengersSection(booking),
                buildPriceTable(booking, n)
            );
        } else {
            content = """
                <p style="margin:0 0 20px;font-size:15px;color:#374151;line-height:1.65;">
                  Draga/i <strong style="color:#2D5F6B;">%s</strong>,<br><br>%s
                </p>
                %s
                <div style="background:#fff5f5;border:1px solid #fee2e2;border-left:3px solid #dc2626;border-radius:6px;padding:16px 20px;margin-bottom:24px;">
                  <div style="font-size:13px;font-weight:700;color:#dc2626;margin-bottom:6px;">Pitanja ili žalba?</div>
                  <p style="margin:0;font-size:13px;color:#374151;line-height:1.7;">
                    Kontaktirajte nas na
                    <a href="mailto:%s" style="color:#dc2626;font-weight:600;text-decoration:none;">%s</a>.
                    Radujemo se vašem sledećem putovanju sa nama!
                  </p>
                </div>
                """.formatted(
                EmailHtmlBuilder.esc(booking.getFirstName()),
                subtitle,
                customerTripCardStyled(booking, depDate, retDate, n, true),
                sender.getFrom(), sender.getFrom()
            );
        }

        return EmailHtmlBuilder.wrapBase(
            "Escapii",
            confirmed ? "#064e3b" : "#450a0a",
            heading,
            confirmed
                ? "Vaše putovanje je zvanično u kalendaru. Jedino što ne znate — kuda idete! ✦"
                : "Nadamo se da ćemo vas videti na sledećem putovanju.",
            booking.getBookingRef(),
            accentColor,
            badgeLabel,
            content,
            EmailHtmlBuilder.customerFooter(sender.getFrom()),
            confirmed
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Confirmed timeline
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildConfirmedTimeline(Booking booking) {
        var dep         = booking.getSelectedDate().getDepartureDate();
        var weatherDate = dep.minusDays(7);
        var revealDate  = dep.minusDays(3);

        String today      = java.time.LocalDate.now().format(EmailHtmlBuilder.DATE_FMT);
        String weatherStr = weatherDate.format(EmailHtmlBuilder.DATE_FMT);
        String revealStr  = revealDate.format(EmailHtmlBuilder.DATE_FMT);
        String depStr     = dep.format(EmailHtmlBuilder.DATE_FMT);

        return """
            <div style="margin-bottom:24px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;margin-bottom:16px;">Šta vas čeka</div>
              %s
              %s
              %s
              %s
            </div>
            """.formatted(
            EmailHtmlBuilder.timelineItem("✓", "#dcfce7", "#16a34a",
                "Rezervacija potvrđena",
                "Danas · " + today,
                "Sve je rezervisano — letovi, smeštaj, transfer. Možete se opustiti — doslovno."),
            EmailHtmlBuilder.timelineItem("🌤", "#EDF4F5", "#CA8A71",
                "Vremenska prognoza",
                weatherStr + " · 7 dana pre polaska",
                "Dobijate prognozu da znate šta da spakujete. Destinacija? I dalje tajna!"),
            EmailHtmlBuilder.timelineItem("✉", "#BFD8DE", "#2D5F6B",
                "Koverta s destinacijom",
                revealStr + " · 72h pre polaska",
                "Konačno — otkrivate gde idete!"),
            EmailHtmlBuilder.timelineItem("✈", "#0D2E38", "#F5C9A8",
                "Avantura počinje!",
                depStr + " · Dan polaska",
                "Dođite na aerodrom i dozvolite sebi da budete iznenađeni.")
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Shared content blocks
    // ═══════════════════════════════════════════════════════════════════════════

    private String customerTripCard(Booking booking, String depDate, String retDate, int n) {
        return customerTripCardStyled(booking, depDate, retDate, n, false);
    }

    private String customerTripCardStyled(Booking booking, String depDate, String retDate, int n, boolean cancelled) {
        String borderColor = cancelled ? "#dc2626" : "#CA8A71"; // purple for active, red for cancelled
        String cardTitle   = cancelled ? "Otkazano putovanje" : "Detalji putovanja";

        StringBuilder rows = new StringBuilder();
        rows.append(EmailHtmlBuilder.dRow("Polazni aerodrom", EmailHtmlBuilder.esc(booking.getDepartureAirport())));
        if (cancelled) {
            rows.append(EmailHtmlBuilder.dRowStrike("Datum polaska",  depDate));
            rows.append(EmailHtmlBuilder.dRowStrike("Datum povratka", retDate));
            rows.append(EmailHtmlBuilder.dRowStrike("Trajanje",       booking.getSelectedDate().getNumberOfNights() + " noći"));
        } else {
            rows.append(EmailHtmlBuilder.dRow("Datum polaska",  depDate));
            rows.append(EmailHtmlBuilder.dRow("Datum povratka", retDate));
            rows.append(EmailHtmlBuilder.dRow("Trajanje",       booking.getSelectedDate().getNumberOfNights() + " noći"));
        }
        rows.append(EmailHtmlBuilder.dRow("Putnici",   n + (n == 1 ? " putnik" : " putnika")));
        rows.append(EmailHtmlBuilder.dRow("Smeštaj",   EmailHtmlBuilder.resolveAccomLabel(booking.getAccommodationType())));
        if (booking.getExclusionCount() > 0) rows.append(EmailHtmlBuilder.dRow("Isključene dest.", buildExclusionsText(booking)));
        if (!cancelled) rows.append(EmailHtmlBuilder.dRowMystery("Destinacija", "✦ Iznenađenje!"));

        return """
            <div style="background:#f8f9fa;border:1px solid #e5e7eb;border-left:3px solid %s;border-radius:6px;padding:18px 20px;margin:0 0 20px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;margin-bottom:14px;">%s</div>
              <table width="100%%" cellpadding="0" cellspacing="0">%s</table>
            </div>
            """.formatted(borderColor, cardTitle, rows);
    }

    private String cRow(String label, String value, boolean shaded) {
        String bg = shaded ? "background:#fafafa;" : "";
        return """
            <tr style="%s">
              <td style="padding:11px 20px;font-size:13px;color:#9ca3af;font-weight:600;width:40%%;border-bottom:1px solid #f3f4f6;">%s</td>
              <td style="padding:11px 20px;font-size:14px;color:#111827;font-weight:500;border-bottom:1px solid #f3f4f6;">%s</td>
            </tr>
            """.formatted(bg, label, value);
    }

    private String nextStepsBlock() {
        return """
            <div style="margin-bottom:24px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;margin-bottom:14px;">Šta vas čeka</div>
              %s
              %s
              %s
            </div>
            """.formatted(
            EmailHtmlBuilder.step("1", "Tim Escapii vam se javlja u roku od <strong style='color:#2D5F6B;'>24 sata</strong>",
                      "Proveravamo dostupnost i potvrđujemo vašu rezervaciju."),
            EmailHtmlBuilder.step("2", "Vremenska prognoza — <strong style='color:#2D5F6B;'>7 dana pre polaska</strong>",
                      "Dobijate prognozu da znate šta da spakujete. Destinacija? I dalje tajna. 🌤"),
            EmailHtmlBuilder.step("3", "Koverta s destinacijom — <strong style='color:#2D5F6B;'>72h pre polaska</strong>",
                      "Koverta otkriva gde putujete. ✉")
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Putnici
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildPassengersSection(Booking booking) {
        List<PassengerInfo> passengers = booking.getPassengers();
        if (passengers == null || passengers.isEmpty()) return "";

        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < passengers.size(); i++) {
            PassengerInfo p = passengers.get(i);
            String bg    = i % 2 == 0 ? "#fafafa" : "#ffffff";
            String name  = EmailHtmlBuilder.esc(p.getName());
            String dob   = p.getDateOfBirth() != null ? p.getDateOfBirth().format(EmailHtmlBuilder.DATE_FMT) : "—";
            String gender = "M".equals(p.getGender()) ? "Muški" : "Ženski";
            String visaInfo = (p.getVisaInfo() != null && !p.getVisaInfo().isBlank())
                    ? EmailHtmlBuilder.esc(p.getVisaInfo()) : "—";
            rows.append("""
                <tr style="background:%s;">
                  <td style="padding:11px 16px;font-size:13px;font-weight:700;color:#9ca3af;border-bottom:1px solid #f3f4f6;">Putnik %d</td>
                  <td style="padding:11px 16px;font-size:14px;font-weight:600;color:#111827;border-bottom:1px solid #f3f4f6;">%s</td>
                  <td style="padding:11px 16px;font-size:13px;color:#6b7280;border-bottom:1px solid #f3f4f6;">%s · %s</td>
                  <td style="padding:11px 16px;font-size:13px;color:#6b7280;border-bottom:1px solid #f3f4f6;">Vize: %s</td>
                </tr>
                """.formatted(bg, i + 1, name, gender, dob, visaInfo));
        }

        return """
            <div style="margin-bottom:28px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;margin-bottom:12px;padding-bottom:8px;border-bottom:2px solid #f3f4f6;">Putnici</div>
              <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                <thead>
                  <tr style="background:#f3f4f6;">
                    <th style="padding:10px 16px;text-align:left;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#9ca3af;">Redni br.</th>
                    <th style="padding:10px 16px;text-align:left;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#9ca3af;">Ime</th>
                    <th style="padding:10px 16px;text-align:left;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#9ca3af;">Pol · Datum rođenja</th>
                    <th style="padding:10px 16px;text-align:left;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#9ca3af;">Aktivne vize</th>
                  </tr>
                </thead>
                <tbody>%s</tbody>
              </table>
            </div>
            """.formatted(rows);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cenovnik
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildPriceTable(Booking booking, int n) {
        StringBuilder rows = new StringBuilder();

        rows.append(priceRow("Osnovna cena", EmailHtmlBuilder.eur(booking.getBasePricePerPerson()) + " / os", n, booking.getBasePricePerPerson() * n, false));
        if (booking.getAccommodationExtra() > 0)
            rows.append(priceRow(EmailHtmlBuilder.resolveAccomLabel(booking.getAccommodationType()) + " upgrade", EmailHtmlBuilder.eur(booking.getAccommodationExtra()) + " / os", n, booking.getAccommodationExtra() * n, false));
        if (Boolean.TRUE.equals(booking.getHasBreakfast()))
            rows.append(priceRow("Doručak", "15 € / os", n, 15 * n, false));
        if (Boolean.TRUE.equals(booking.getHasSeatsTogther()))
            rows.append(priceRow("Sedišta zajedno", "10 € / os", n, 10 * n, false));
        if (Boolean.TRUE.equals(booking.getHasInsurance()))
            rows.append(priceRow("Putno osiguranje", "15 € / os", n, 15 * n, false));
        if (booking.getCabinSuitcaseCount() > 0)
            rows.append(priceRow("Kabinski kofer (×2 smera)", "80 € / os", booking.getCabinSuitcaseCount(), booking.getCabinSuitcaseCount() * 80, false));
        if (booking.getExclusionCostEur() > 0) {
            int paid = booking.getExclusionCount() - 1;
            rows.append(priceRow("Isključivanja (%d× 10€)".formatted(paid), "—", null, booking.getExclusionCostEur(), true));
        }

        return """
            <div style="margin-bottom:28px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;margin-bottom:12px;padding-bottom:8px;border-bottom:2px solid #f3f4f6;">Pregled cene</div>
              <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                <thead>
                  <tr style="background:#f9fafb;">
                    <th style="padding:10px 16px;text-align:left;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#9ca3af;border-bottom:1px solid #e5e7eb;">Stavka</th>
                    <th style="padding:10px 16px;text-align:center;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#9ca3af;border-bottom:1px solid #e5e7eb;">Po osobi</th>
                    <th style="padding:10px 16px;text-align:center;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#9ca3af;border-bottom:1px solid #e5e7eb;">× Putnika</th>
                    <th style="padding:10px 16px;text-align:right;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#9ca3af;border-bottom:1px solid #e5e7eb;">Ukupno</th>
                  </tr>
                </thead>
                <tbody>%s</tbody>
                <tfoot>
                  <tr style="background:#0D2E38;">
                    <td colspan="3" style="padding:14px 16px;font-size:13px;font-weight:700;color:#fff;letter-spacing:0.5px;">SVE UKUPNO</td>
                    <td style="padding:14px 16px;text-align:right;font-size:18px;font-weight:900;color:#F5C9A8;">%s</td>
                  </tr>
                </tfoot>
              </table>
            </div>
            """.formatted(rows, EmailHtmlBuilder.eur(booking.getTotalPriceAll()));
    }

    private String priceRow(String label, String perPerson, Integer count, int total, boolean flat) {
        String ppCell    = flat ? "<td style='padding:11px 16px;text-align:center;color:#d1d5db;border-bottom:1px solid #f3f4f6;'>—</td>" :
                                  "<td style='padding:11px 16px;text-align:center;font-size:13px;color:#6b7280;border-bottom:1px solid #f3f4f6;'>" + perPerson + "</td>";
        String countCell = flat ? "<td style='padding:11px 16px;text-align:center;color:#d1d5db;border-bottom:1px solid #f3f4f6;'>flat</td>" :
                                  "<td style='padding:11px 16px;text-align:center;font-size:13px;color:#6b7280;border-bottom:1px solid #f3f4f6;'>" + count + "</td>";
        return """
            <tr>
              <td style="padding:11px 16px;font-size:14px;color:#111827;border-bottom:1px solid #f3f4f6;">%s</td>
              %s
              %s
              <td style="padding:11px 16px;text-align:right;font-size:14px;font-weight:700;color:#111827;border-bottom:1px solid #f3f4f6;">%s</td>
            </tr>
            """.formatted(label, ppCell, countCell, EmailHtmlBuilder.eur(total));
    }

    private String buildNotesBox(String notes) {
        if (notes == null || notes.isBlank()) return "";
        return """
            <div style="background:#fffbeb;border-left:3px solid #f59e0b;border-radius:0 6px 6px 0;padding:14px 18px;margin-bottom:28px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#b45309;margin-bottom:6px;">Napomena korisnika</div>
              <div style="font-size:14px;color:#78350f;line-height:1.6;">%s</div>
            </div>
            """.formatted(EmailHtmlBuilder.esc(notes));
    }

    private String buildExclusionsText(Booking booking) {
        StringBuilder sb = new StringBuilder();
        List<com.escapii.model.Destination> excl = Arrays.asList(
            booking.getExcludedDestination1(),
            booking.getExcludedDestination2(),
            booking.getExcludedDestination3(),
            booking.getExcludedDestination4(),
            booking.getExcludedDestination5()
        );
        for (com.escapii.model.Destination d : excl) {
            if (d != null) { if (!sb.isEmpty()) sb.append(", "); sb.append(d.getName()); }
        }
        return sb.isEmpty() ? "Nema" : sb.toString();
    }
}
