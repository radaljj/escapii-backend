package com.escapii.service.impl;

import com.escapii.model.Booking;
import com.escapii.model.PassengerInfo;
import com.escapii.service.DestinationService;
import com.escapii.service.EmailService;
import java.time.LocalDate;
import java.util.List;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final DestinationService destinationService;

    @Value("${app.mail-from}")
    private String fromEmail;

    @Value("${app.team-email}")
    private String teamEmail;

    @Value("${app.ops-email}")
    private String opsEmail;

    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

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
        sendEmail(
            teamEmail,
            "Novi upit %s — %s %s".formatted(booking.getBookingRef(), booking.getFirstName(), booking.getLastName()),
            buildTeamEmailHtml(booking)
        );
    }

    @Override
    @Async
    public void sendCustomerConfirmation(Booking booking) {
        sendEmail(
            booking.getEmail(),
            "Upit primljen — %s".formatted(booking.getBookingRef()),
            buildCustomerReceivedHtml(booking)
        );
    }

    @Override
    @Async
    public void sendBookingConfirmed(Booking booking) {
        sendEmail(
            booking.getEmail(),
            "Rezervacija potvrđena — %s".formatted(booking.getBookingRef()),
            buildCustomerStatusHtml(booking, true)
        );
        log.info("[Email] Poslat CONFIRMED email na adresu {} za booking {}", booking.getEmail(), booking.getBookingRef());
    }

    @Override
    @Async
    public void sendBookingCancelled(Booking booking) {
        sendEmail(
            booking.getEmail(),
            "Rezervacija otkazana — %s".formatted(booking.getBookingRef()),
            buildCustomerStatusHtml(booking, false)
        );
        log.info("[Email] Poslat CANCELLED email na adresu {} za booking {}", booking.getEmail(), booking.getBookingRef());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Slanje
    // ═══════════════════════════════════════════════════════════════════════════

    private static String resolveAirportName(String iata) {
        return switch (iata == null ? "" : iata.toUpperCase()) {
            case "BEG" -> "Beograd";
            case "INI" -> "Niš";
            case "ZAG" -> "Zagreb";
            case "BUD" -> "Budimpešta";
            case "TIM" -> "Temišvar";
            default    -> iata;
        };
    }

    private void sendEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("[EmailService] Greška pri slanju emaila na {}: {}", to, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tim — interni email
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildTeamEmailHtml(Booking booking) {
        String depDate = booking.getSelectedDate().getDepartureDate().format(DATE_FMT);
        String retDate = booking.getSelectedDate().getReturnDate().format(DATE_FMT);
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
                tRow("Ime i prezime", "<a href='mailto:" + booking.getEmail() + "' style='color:#08112a;font-weight:700;text-decoration:none;'>" + esc(booking.getFirstName() + " " + booking.getLastName()) + "</a>") +
                tRow("Email", "<a href='mailto:" + booking.getEmail() + "' style='color:#f97316;text-decoration:none;'>" + booking.getEmail() + "</a>") +
                tRow("Telefon", "<a href='tel:" + booking.getPhone() + "' style='color:#f97316;text-decoration:none;'>" + booking.getPhone() + "</a>")
            ),
            teamSection("Putovanje",
                tRow("Aerodrom", booking.getDepartureAirport()) +
                tRow("Datum", "<strong>" + depDate + " &rarr; " + retDate + "</strong>") +
                tRow("Noći", booking.getSelectedDate().getNumberOfNights() + " noći") +
                tRow("Putnici", n + (n == 1 ? " putnik" : " putnika")) +
                tRow("Smeštaj", resolveAccomLabel(booking.getAccommodationType())) +
                tRow("Isključene dest.", buildExclusionsText(booking)) +
                tRow("Presedanje OK", Boolean.TRUE.equals(booking.getHasConnectingFlights()) ? "✔ Da" : "✘ Ne — samo direktni letovi")
            ),
            buildPassengersSection(booking),
            buildPriceTable(booking, n)
        );

        String notes = buildNotesBox(booking.getNotes());

        return wrapBase(
            "Escapii — Interni",
            "#1e1b4b",
            "Novi upit stigao",
            depDate + " → " + retDate + " · " + n + (n == 1 ? " putnik" : " putnika"),
            booking.getBookingRef(),
            "#f97316",
            "NOVI UPIT",
            body + notes,
            "Interni email — escapii ops tim · Nije za prosleđivanje",
            false
        );
    }

    private String metaSection(Booking booking, String dep, String ret, int n) {
        String deadline = booking.getCreatedAt().plusHours(24).format(DATETIME_FMT);
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px;background:#f8f9fa;border:1px solid #e5e7eb;border-radius:6px;">
              <tr>
                <td style="padding:12px 16px;border-right:1px solid #e5e7eb;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">Ref. broj</div>
                  <div style="font-size:14px;font-weight:700;color:#08112a;">%s</div>
                </td>
                <td style="padding:12px 16px;border-right:1px solid #e5e7eb;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">Primljeno</div>
                  <div style="font-size:13px;font-weight:600;color:#374151;">%s</div>
                </td>
                <td style="padding:12px 16px;border-right:1px solid #e5e7eb;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">Ukupno</div>
                  <div style="font-size:14px;font-weight:700;color:#f97316;">%s €</div>
                </td>
                <td style="padding:12px 16px;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#9ca3af;margin-bottom:3px;">Rok odgovora</div>
                  <div style="font-size:13px;font-weight:700;color:#dc2626;">%s</div>
                </td>
              </tr>
            </table>
            """.formatted(booking.getBookingRef(), booking.getCreatedAt().format(DATETIME_FMT),
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
        String depDate = booking.getSelectedDate().getDepartureDate().format(DATE_FMT);
        String retDate = booking.getSelectedDate().getReturnDate().format(DATE_FMT);
        int n = booking.getNumberOfTravelers();

        String body = """
            <p style="margin:0 0 20px;font-size:15px;color:#374151;line-height:1.65;">
              Draga/i <strong style="color:#08112a;">%s</strong>,<br><br>
              uspešno smo primili vaš upit za putovanje. Naš tim pregledava vaše preference
              i kontaktiraće vas u roku od <strong style="color:#08112a;">24 sata</strong> sa svim detaljima i potvrdom rezervacije.
            </p>
            %s
            %s
            %s
            """.formatted(
            esc(booking.getFirstName()),
            customerTripCard(booking, depDate, retDate, n),
            totalBox(booking.getTotalPriceAll(), n),
            nextStepsBlock()
        );

        return wrapBase(
            "Escapii",
            "#08112a",
            "Vaš upit je primljen",
            "Hvala što ste nam se obratili — naš tim će vas kontaktirati u roku od 24 sata.",
            booking.getBookingRef(),
            "#f97316",
            "NA ČEKANJU",
            body,
            customerFooter(fromEmail),
            true
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Korisnički — CONFIRMED / CANCELLED
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildCustomerStatusHtml(Booking booking, boolean confirmed) {
        String depDate = booking.getSelectedDate().getDepartureDate().format(DATE_FMT);
        String retDate = booking.getSelectedDate().getReturnDate().format(DATE_FMT);
        int n = booking.getNumberOfTravelers();

        String accentColor = confirmed ? "#16a34a" : "#dc2626";
        String badgeLabel  = confirmed ? "POTVRĐENA" : "OTKAZANA";
        String heading     = confirmed ? "Rezervacija potvrđena!" : "Rezervacija otkazana";
        String subtitle    = confirmed
            ? "vaša rezervacija je zvanično potvrđena! Sve je spremno — vi samo spakovajte kofere i pustite uzbuđenje da raste. ✦"
            : "sa žaljenjem vam obaveštavamo da je vaša rezervacija otkazana. Razumemo da su planovi nekad nepredvidivi — i nadamo se da ćete nam ponovo ukazati poverenje.<br><br>Vaša avantura nas čeka — kada budete spremni, mi ćemo biti tu. ✦";

        String content;
        if (confirmed) {
            content = """
                <p style="margin:0 0 20px;font-size:15px;color:#374151;line-height:1.65;">
                  Draga/i <strong style="color:#08112a;">%s</strong>,<br><br>%s
                </p>
                %s
                %s
                %s
                %s
                %s
                """.formatted(
                esc(booking.getFirstName()),
                subtitle,
                customerTripCardStyled(booking, depDate, retDate, n, false),
                totalBox(booking.getTotalPriceAll(), n),
                buildConfirmedTimeline(booking),
                buildPassengersSection(booking),
                buildPriceTable(booking, n)
            );
        } else {
            content = """
                <p style="margin:0 0 20px;font-size:15px;color:#374151;line-height:1.65;">
                  Draga/i <strong style="color:#08112a;">%s</strong>,<br><br>%s
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
                esc(booking.getFirstName()),
                subtitle,
                customerTripCardStyled(booking, depDate, retDate, n, true),
                fromEmail, fromEmail
            );
        }

        return wrapBase(
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
            customerFooter(fromEmail),
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

        String today      = java.time.LocalDate.now().format(DATE_FMT);
        String weatherStr = weatherDate.format(DATE_FMT);
        String revealStr  = revealDate.format(DATE_FMT);
        String depStr     = dep.format(DATE_FMT);

        return """
            <div style="margin-bottom:24px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;margin-bottom:16px;">Šta vas čeka</div>
              %s
              %s
              %s
              %s
            </div>
            """.formatted(
            timelineItem("✓", "#dcfce7", "#16a34a",
                "Rezervacija potvrđena",
                "Danas · " + today,
                "Sve je rezervisano — letovi, smeštaj, transfer. Možete se opustiti — doslovno."),
            timelineItem("🌤", "#fff7ed", "#f97316",
                "Vremenska prognoza",
                weatherStr + " · 7 dana pre polaska",
                "Dobijate prognozu da znate šta da spakujete. Destinacija? I dalje tajna!"),
            timelineItem("✉", "#eff6ff", "#3b82f6",
                "Koverta s destinacijom",
                revealStr + " · 72h pre polaska",
                "Fizička koverta na vašoj adresi. Konačno — otkrivate gde idete!"),
            timelineItem("✈", "#08112a", "#f97316",
                "Avantura počinje!",
                depStr + " · Dan polaska",
                "Dođite na aerodrom i dozvolite sebi da budete iznenađeni.")
        );
    }

    private String timelineItem(String icon, String iconBg, String accentColor,
                                String title, String when, String description) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:16px;">
              <tr>
                <td style="width:38px;vertical-align:top;">
                  <div style="width:36px;height:36px;background:%s;border:2px solid %s;border-radius:50%%;text-align:center;line-height:32px;font-size:15px;">%s</div>
                </td>
                <td style="padding-left:14px;vertical-align:top;">
                  <div style="font-size:14px;font-weight:700;color:#08112a;margin-bottom:2px;">%s</div>
                  <div style="font-size:11px;color:#9ca3af;margin-bottom:4px;">%s</div>
                  <div style="font-size:13px;color:#6b7280;line-height:1.5;">%s</div>
                </td>
              </tr>
            </table>
            """.formatted(iconBg, accentColor, icon, title, when, description);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Shared content blocks
    // ═══════════════════════════════════════════════════════════════════════════

    private String customerTripCard(Booking booking, String depDate, String retDate, int n) {
        return customerTripCardStyled(booking, depDate, retDate, n, false);
    }

    private String customerTripCardStyled(Booking booking, String depDate, String retDate, int n, boolean cancelled) {
        String borderColor = cancelled ? "#dc2626" : "#f97316";
        String cardTitle   = cancelled ? "Otkazano putovanje" : "Detalji putovanja";

        StringBuilder rows = new StringBuilder();
        rows.append(dRow("Polazni aerodrom", esc(booking.getDepartureAirport())));
        if (cancelled) {
            rows.append(dRowStrike("Datum polaska",  depDate));
            rows.append(dRowStrike("Datum povratka", retDate));
            rows.append(dRowStrike("Trajanje",       booking.getSelectedDate().getNumberOfNights() + " noći"));
        } else {
            rows.append(dRow("Datum polaska",  depDate));
            rows.append(dRow("Datum povratka", retDate));
            rows.append(dRow("Trajanje",       booking.getSelectedDate().getNumberOfNights() + " noći"));
        }
        rows.append(dRow("Putnici",   n + (n == 1 ? " putnik" : " putnika")));
        rows.append(dRow("Smeštaj",   resolveAccomLabel(booking.getAccommodationType())));
        if (booking.getExclusionCount() > 0) rows.append(dRow("Isključene dest.", buildExclusionsText(booking)));
        if (!cancelled) rows.append(dRowMystery("Destinacija", "✦ Iznenađenje!"));

        return """
            <div style="background:#f8f9fa;border:1px solid #e5e7eb;border-left:3px solid %s;border-radius:6px;padding:18px 20px;margin:0 0 20px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;margin-bottom:14px;">%s</div>
              <table width="100%%" cellpadding="0" cellspacing="0">%s</table>
            </div>
            """.formatted(borderColor, cardTitle, rows);
    }

    private String dRow(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #f3f4f6;">
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;width:45%%;">%s</td>
              <td style="padding:7px 0;font-size:13px;color:#1f2937;font-weight:500;text-align:right;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    private String dRowStrike(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #f3f4f6;">
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;width:45%%;">%s</td>
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;font-weight:500;text-align:right;text-decoration:line-through;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    private String dRowMystery(String label, String value) {
        return """
            <tr>
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;width:45%%;">%s</td>
              <td style="padding:7px 0;font-size:13px;color:#f97316;font-weight:600;font-style:italic;text-align:right;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    // Stari cRow zadr&zan za interni email (teamSection)
    private String cRow(String label, String value, boolean shaded) {
        String bg = shaded ? "background:#fafafa;" : "";
        return """
            <tr style="%s">
              <td style="padding:11px 20px;font-size:13px;color:#9ca3af;font-weight:600;width:40%%;border-bottom:1px solid #f3f4f6;">%s</td>
              <td style="padding:11px 20px;font-size:14px;color:#111827;font-weight:500;border-bottom:1px solid #f3f4f6;">%s</td>
            </tr>
            """.formatted(bg, label, value);
    }

    private String totalBox(int total, int n) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:24px;background:#08112a;border-radius:8px;">
              <tr>
                <td style="padding:20px 24px;">
                  <div style="font-size:10px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:rgba(255,255,255,0.45);margin-bottom:6px;">Ukupna cena putovanja</div>
                  <div style="font-family:Georgia,'Times New Roman',serif;font-size:34px;font-weight:700;color:#f97316;line-height:1;margin-bottom:4px;">%s €</div>
                  <div style="font-size:12px;color:rgba(255,255,255,0.4);">za %d %s · sve uključeno</div>
                </td>
              </tr>
            </table>
            """.formatted(total, n, n == 1 ? "putnika" : "putnika");
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
            step("1", "Tim Escapii vam se javlja u roku od <strong style='color:#08112a;'>24 sata</strong>",
                      "Proveravamo dostupnost i potvrđujemo vašu rezervaciju."),
            step("2", "Vremenska prognoza — <strong style='color:#08112a;'>7 dana pre polaska</strong>",
                      "Dobijate prognozu da znate šta da spakujete. Destinacija? I dalje tajna. 🌤"),
            step("3", "Koverta s destinacijom — <strong style='color:#08112a;'>72h pre polaska</strong>",
                      "Fizička koverta na vašoj adresi otkriva gde putujete. ✉")
        );
    }

    private String step(String num, String title, String description) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:12px;">
              <tr>
                <td style="width:36px;vertical-align:top;padding-top:2px;">
                  <div style="width:28px;height:28px;background:#f97316;border-radius:50%%;text-align:center;line-height:28px;font-size:13px;font-weight:800;color:#fff;">%s</div>
                </td>
                <td style="padding-left:12px;vertical-align:top;">
                  <div style="font-size:14px;color:#08112a;font-weight:600;margin-bottom:2px;">%s</div>
                  <div style="font-size:13px;color:#6b7280;line-height:1.5;">%s</div>
                </td>
              </tr>
            </table>
            """.formatted(num, title, description);
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
            String name  = esc(p.getName());
            String dob   = p.getDateOfBirth() != null ? p.getDateOfBirth().format(DATE_FMT) : "—";
            String gender = "M".equals(p.getGender()) ? "Muški" : "Ženski";
            String visaInfo = (p.getVisaInfo() != null && !p.getVisaInfo().isBlank())
                    ? esc(p.getVisaInfo()) : "—";
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

        rows.append(priceRow("Osnovna cena", eur(booking.getBasePricePerPerson()) + " / os", n, booking.getBasePricePerPerson() * n, false));
        if (booking.getAccommodationExtra() > 0)
            rows.append(priceRow(resolveAccomLabel(booking.getAccommodationType()) + " upgrade", eur(booking.getAccommodationExtra()) + " / os", n, booking.getAccommodationExtra() * n, false));
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
                  <tr style="background:#08112a;">
                    <td colspan="3" style="padding:14px 16px;font-size:13px;font-weight:700;color:#fff;letter-spacing:0.5px;">SVE UKUPNO</td>
                    <td style="padding:14px 16px;text-align:right;font-size:18px;font-weight:900;color:#f97316;">%s</td>
                  </tr>
                </tfoot>
              </table>
            </div>
            """.formatted(rows, eur(booking.getTotalPriceAll()));
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
            """.formatted(label, ppCell, countCell, eur(total));
    }

    private String buildNotesBox(String notes) {
        if (notes == null || notes.isBlank()) return "";
        return """
            <div style="background:#fffbeb;border-left:3px solid #f59e0b;border-radius:0 6px 6px 0;padding:14px 18px;margin-bottom:28px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#b45309;margin-bottom:6px;">Napomena korisnika</div>
              <div style="font-size:14px;color:#78350f;line-height:1.6;">%s</div>
            </div>
            """.formatted(esc(notes));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Base wrapper — shared shell za sve emailove
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Zajednički wrapper koji svi emailovi dele.
     * subheading — opcioni podnaslov ispod headinga (može biti prazan string)
     * mysteryStrip — prikazuje "Vaša destinacija ostaje tajna" traku (samo korisnički emailovi)
     */
    private String wrapBase(
        String logoLabel,
        String headerBg,
        String headingText,
        String subheading,
        String refCode,
        String badgeColor,
        String badgeLabel,
        String bodyContent,
        String footerText,
        boolean mysteryStrip
    ) {
        // Badge style — providna pozadina sa obojenim rubom
        String badgeBg     = switch (badgeColor) {
            case "#16a34a" -> "rgba(22,163,74,0.2)";
            case "#dc2626" -> "rgba(220,38,38,0.2)";
            default        -> "rgba(249,115,22,0.2)";
        };
        String badgeText   = switch (badgeColor) {
            case "#16a34a" -> "#4ade80";
            case "#dc2626" -> "#f87171";
            default        -> "#fb923c";
        };
        String badgeBorder = switch (badgeColor) {
            case "#16a34a" -> "rgba(22,163,74,0.4)";
            case "#dc2626" -> "rgba(220,38,38,0.4)";
            default        -> "rgba(249,115,22,0.4)";
        };

        String subheadingHtml = subheading.isBlank() ? "" :
            "<p style=\"margin:8px 0 0;font-size:13px;color:rgba(255,255,255,0.55);line-height:1.5;\">%s</p>".formatted(subheading);

        String refHtml = refCode.isBlank() ? "" :
            "<div style=\"display:inline-block;background:rgba(249,115,22,0.15);border:1px solid rgba(249,115,22,0.3);color:#fb923c;font-size:11px;font-weight:700;padding:3px 10px;border-radius:4px;letter-spacing:0.5px;margin-top:10px;\">&#10022; %s</div>".formatted(refCode);

        String mysteryHtml = mysteryStrip ? """
            <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0f1f3d;">
              <tr><td style="padding:10px 36px;font-size:11px;color:rgba(255,255,255,0.5);text-align:center;letter-spacing:1px;">
                <span style="color:#f97316;">&#9679; &#9679; &#9679;</span>
                &nbsp;&nbsp;Vaša destinacija ostaje tajna sve do 72h pre polaska&nbsp;&nbsp;
                <span style="color:#f97316;">&#9679; &#9679; &#9679;</span>
              </td></tr>
            </table>
            """ : "";

        return """
            <!DOCTYPE html>
            <html lang="sr" xmlns:v="urn:schemas-microsoft-com:vml">
            <head>
              <meta charset="UTF-8">
              <meta name="x-apple-disable-message-reformatting">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="format-detection" content="telephone=no, date=no, address=no, email=no">
              <meta name="color-scheme" content="light">
              <!--[if mso]>
              <noscript><xml><o:OfficeDocumentSettings xmlns:o="urn:schemas-microsoft-com:office:office">
                <o:PixelsPerInch>96</o:PixelsPerInch>
              </o:OfficeDocumentSettings></xml></noscript>
              <style>td,th,div,p,a,h1,h2,h3{font-family:"Segoe UI",sans-serif;mso-line-height-rule:exactly;}</style>
              <![endif]-->
              <style>
                @media (max-width:620px) {
                  .mob-full { width:100%% !important; }
                  .mob-pad  { padding:20px !important; }
                }
              </style>
            </head>
            <body style="margin:0;padding:0;word-break:break-word;-webkit-font-smoothing:antialiased;background:#f3f4f6;">

              <!-- Preheader -->
              <div style="display:none;max-height:0;overflow:hidden;mso-hide:all;">
                %s &#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&zwnj;&nbsp;
              </div>

              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;">
                <tr><td align="center" style="padding:32px 16px;">
                  <table class="mob-full" style="width:600px;max-width:600px;" cellpadding="0" cellspacing="0">

                    <!-- LOGO -->
                    <tr><td style="padding-bottom:16px;text-align:center;">
                      <div style="font-family:Georgia,'Times New Roman',serif;font-size:20px;font-weight:700;color:#08112a;letter-spacing:0.5px;">escapii<span style="color:#f97316;">.</span></div>
                      <div style="font-size:9px;color:#9ca3af;letter-spacing:2.5px;text-transform:uppercase;margin-top:3px;">mystery travel</div>
                    </td></tr>

                    <!-- CARD -->
                    <tr><td style="background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;">

                      <!-- Accent bar -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td style="background:%s;height:4px;font-size:0;line-height:0;">&nbsp;</td></tr>
                      </table>

                      <!-- Header -->
                      <table width="100%%" cellpadding="0" cellspacing="0" style="background:%s;">
                        <tr><td style="padding:24px 36px 22px;" class="mob-pad">
                          <!-- Logo + Badge -->
                          <table width="100%%" cellpadding="0" cellspacing="0">
                            <tr>
                              <td style="vertical-align:top;">
                                <div style="font-family:Georgia,'Times New Roman',serif;font-size:19px;font-weight:700;color:#fff;letter-spacing:0.5px;">escapii<span style="color:#f97316;">.</span></div>
                                <div style="font-size:9px;color:rgba(255,255,255,0.38);letter-spacing:2px;text-transform:uppercase;margin-top:2px;">mystery travel</div>
                              </td>
                              <td style="text-align:right;vertical-align:top;">
                                <span style="display:inline-block;background:%s;color:%s;border:1px solid %s;font-size:10px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;padding:4px 12px;border-radius:100px;">%s</span>
                              </td>
                            </tr>
                          </table>
                          <!-- Heading -->
                          <h1 style="font-family:Georgia,'Times New Roman',serif;font-size:26px;color:#fff;line-height:1.3;margin:16px 0 0;font-weight:normal;">%s</h1>
                          %s
                          %s
                        </td></tr>
                      </table>

                      <!-- Mystery strip -->
                      %s

                      <!-- Body -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td style="padding:28px 36px;background:#ffffff;" class="mob-pad">
                          %s
                        </td></tr>
                      </table>

                      <!-- Footer -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td style="background:%s;border-top:1px solid #e5e7eb;padding:16px 36px;text-align:center;font-size:11px;color:#9ca3af;line-height:1.8;">
                          %s
                        </td></tr>
                      </table>

                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(
            headingText,    // preheader
            badgeColor,     // accent bar
            headerBg,       // header bg
            badgeBg,        // badge bg
            badgeText,      // badge text color
            badgeBorder,    // badge border
            badgeLabel,     // badge label
            headingText,    // h1
            subheadingHtml, // subtitle
            refHtml,        // ref chip
            mysteryHtml,    // mystery strip
            bodyContent,    // body
            "#1e1b4b".equals(headerBg) ? "#f0f0f5" : "#f8f9fa", // footer bg
            footerText      // footer
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildExclusionsText(Booking booking) {
        StringBuilder sb = new StringBuilder();
        java.util.List<com.escapii.model.Destination> excl = java.util.Arrays.asList(
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

    private String resolveAccomLabel(String type) {
        if (type == null) return "Standard (3*)";
        return switch (type.toUpperCase()) {
            case "SUPERIOR" -> "Superior (4*)";
            case "PREMIUM"  -> "Premium (5*)";
            default         -> "Standard (3*)";
        };
    }

    private String countryName(String isoCode) {
        if (isoCode == null) return "—";
        return countryNames.getOrDefault(isoCode.toUpperCase(), isoCode.toUpperCase());
    }

    /** Vraća ime putnika za personalizaciju emaila. */
    private String firstName(String firstName) {
        if (firstName == null || firstName.isBlank()) return "putniče";
        return firstName.trim();
    }

    private String eur(int amount) { return amount + " €"; }

    private String customerFooter(String email) {
        return """
            <strong style="color:#08112a;">escapii</strong> — mystery travel d.o.o.<br>
            Beograd, Srbija · <a href="mailto:%s" style="color:#6b7280;text-decoration:underline;">%s</a><br><br>
            <a href="#" style="color:#6b7280;text-decoration:underline;">Politika privatnosti</a>
            """.formatted(email, email);
    }

    private String esc(String input) {
        if (input == null) return "";
        return input.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Jutarnji operativni digest
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void sendDailyDigest(LocalDate today, List<Booking> bookings) {
        // Prognoza: polazak tačno za 7 dana
        // Koverta:  polazak tačno za 3 dana
        LocalDate weatherDay     = today.plusDays(7);
        LocalDate destinationDay = today.plusDays(3);

        StringBuilder weatherRows     = new StringBuilder();
        StringBuilder destinationRows = new StringBuilder();
        StringBuilder previewRows     = new StringBuilder();

        for (Booking b : bookings) {
            LocalDate dep  = b.getSelectedDate().getDepartureDate();
            String name    = esc(b.getFirstName() + " " + b.getLastName());
            String email   = esc(b.getEmail());
            String ref     = esc(b.getBookingRef());
            String depStr  = dep.format(DATE_FMT);
            String airport = esc(b.getDepartureAirport());
            long daysLeft = today.until(dep).getDays();

            if (dep.equals(weatherDay)) {
                weatherRows.append(row(name, email, ref, airport, depStr, daysLeft, "🌤 Prognoza"));
            } else if (dep.equals(destinationDay)) {
                destinationRows.append(row(name, email, ref, airport, depStr, daysLeft, "✉ Koverta"));
            } else {
                String nextTask = dep.isAfter(weatherDay)
                    ? "🌤 prognoza: " + dep.minusDays(7).format(DATE_FMT) + " · ✉ koverta: " + dep.minusDays(3).format(DATE_FMT)
                    : "✉ koverta: " + dep.minusDays(3).format(DATE_FMT);
                previewRows.append(previewRow(name, ref, airport, depStr, daysLeft, nextTask));
            }
        }

        String todayStr = today.format(DATE_FMT);

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

        // Meta bar — summary bro jevi
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
            weatherCount > 0 ? "#ea580c" : "#1f2937", weatherCount,
            destinationCount > 0 ? "#dc2626" : "#1f2937", destinationCount);

        String body = metaBar + nothingToday + destinationSection + weatherSection + previewSection;

        boolean hasUrgent = !weatherRows.isEmpty() || !destinationRows.isEmpty();
        String html = wrapBase(
            "Escapii Ops",
            "#1e1b4b",
            "Jutarnji pregled",
            todayStr + " &middot; " + bookings.size() + " rezervacija u narednih 14 dana",
            "",
            hasUrgent ? "#f97316" : "#16a34a",
            hasUrgent ? "AKCIJA POTREBNA" : "SVE U REDU",
            body,
            "Escapii interni sistem &middot; Automatska poruka &middot; Ne odgovarati",
            false
        );

        sendEmail(opsEmail, "📋 Escapii — " + todayStr, html);
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
                <strong style="color:#08112a;">%s</strong><br>
                <a href="mailto:%s" style="color:#9ca3af;font-size:11px;text-decoration:none;">%s</a>
              </td>
              <td style="padding:8px 10px;font-size:12px;font-weight:700;color:#f97316;vertical-align:middle;">%s</td>
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
                <strong style="color:#08112a;">%s</strong>
                <span style="color:#9ca3af;font-size:11px;"> · %s · %s</span>
              </td>
              <td style="padding:8px 10px;vertical-align:middle;">
                <span style="display:inline-block;padding:2px 8px;border-radius:100px;font-size:11px;font-weight:700;%s">%s</span>
              </td>
              <td style="padding:8px 10px;font-size:11px;color:#9ca3af;vertical-align:middle;">%s</td>
            </tr>""".formatted(name, ref, dep, badgeCss, daysLabel, nextTask);
    }

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

    // ═══════════════════════════════════════════════════════════════════════════
    // Waitlist
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Async
    public void sendWaitlistConfirmation(String email, String airport) {
        String airportName = resolveAirportName(airport);
        String body = """
            <div style="text-align:center;padding:8px 0 20px;">
              <div style="font-size:44px;margin-bottom:12px;">✉</div>
              <div style="font-family:Georgia,'Times New Roman',serif;font-size:22px;color:#08112a;margin-bottom:8px;font-weight:normal;">Dobrodošli na listu čekanja!</div>
              <div style="font-size:14px;color:#6b7280;line-height:1.6;">Prijavili ste se za sledeće odlaske iz:</div>
              <div style="margin:12px 0;">
                <span style="display:inline-block;background:#f3f4f6;border:1px solid #e5e7eb;padding:6px 16px;border-radius:100px;font-size:12px;font-weight:700;color:#374151;letter-spacing:0.5px;">✈ %s</span>
              </div>
            </div>
            <div style="height:1px;background:#f3f4f6;margin:0 0 20px;"></div>
            <p style="margin:0 0 20px;font-size:13px;color:#6b7280;line-height:1.65;">
              Novi termini se dodaju redovno. Čim se pojave mesta za vaš polazni aerodrom, odmah ćemo vas obavestiti — i imaćete <strong style="color:#08112a;">48 sati</strong> da rezervišete pre nego što ponuda bude dostupna svima.
            </p>
            <div style="background:#fffbf5;border:1px solid #fed7aa;border-left:3px solid #f97316;border-radius:6px;padding:14px 18px;margin-bottom:20px;">
              <div style="font-size:13px;font-weight:700;color:#c2410c;margin-bottom:4px;">Šta sad?</div>
              <div style="font-size:13px;color:#374151;line-height:1.6;">Nema ništa što treba da radite — sedite, opustite se, i čekajte naš email. Biće vredno čekanja. ✦</div>
            </div>
            <p style="margin:0;font-size:12px;color:#9ca3af;line-height:1.6;">
              Ako ste se prijavili greškom, jednostavno ignorišite ovaj email.
            </p>
            """.formatted(esc(airportName));

        String html = wrapBase(
            "Escapii", "#08112a", "Na listi ste čekanja",
            "Bićete prvi koji će saznati čim se otvore novi termini.",
            "", "#16a34a", "USKORO", body,
            "Escapii · escapii.com",
            false
        );
        sendEmail(email, "Na listi ste čekanja — Escapii", html);
    }

    @Override
    @Async
    public void sendWaitlistNotification(String email, String airport) {
        String airportName = resolveAirportName(airport);
        String body = """
            <div style="text-align:center;padding:8px 0 20px;">
              <div style="font-size:44px;margin-bottom:12px;">✈</div>
              <div style="font-family:Georgia,'Times New Roman',serif;font-size:22px;color:#08112a;margin-bottom:8px;font-weight:normal;">Termini su otvoreni!</div>
              <div style="font-size:14px;color:#6b7280;line-height:1.6;">Čekali ste — i isplatilo se.</div>
            </div>
            <div style="height:1px;background:#f3f4f6;margin:0 0 20px;"></div>
            <p style="margin:0 0 20px;font-size:15px;color:#374151;line-height:1.65;">
              Dostupni su novi termini za polazak sa aerodroma <strong style="color:#08112a;">%s</strong>.
              Termini se brzo popunjavaju — rezervišite na vreme!
            </p>
            <div style="text-align:center;margin:24px 0;">
              <a href="https://escapii.com" style="display:inline-block;background:#f97316;color:#fff;font-weight:700;font-size:15px;padding:14px 40px;border-radius:100px;text-decoration:none;letter-spacing:0.3px;">
                Rezerviši sada &rarr;
              </a>
            </div>
            <p style="margin:0;font-size:12px;color:#9ca3af;line-height:1.6;">
              Primili ste ovaj email jer ste se prijavili na listu čekanja za aerodrom %s.
            </p>
            """.formatted(esc(airportName), esc(airportName));

        String html = wrapBase(
            "Escapii", "#08112a", "Otvorili su se novi termini!",
            "Termini se brzo popunjavaju — rezervišite na vreme!",
            "", "#f97316", "NOVI TERMINI", body,
            "Escapii · escapii.com",
            false
        );
        sendEmail(email, "Otvorili su se novi termini — Escapii", html);
    }
}
