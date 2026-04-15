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
            "#08112a",
            "Novi upit stigao",
            booking.getBookingRef(),
            "#f97316",
            "NOVI UPIT",
            body + notes,
            "Escapii automatska notifikacija · Ne odgovarati na ovaj email"
        );
    }

    private String metaSection(Booking booking, String dep, String ret, int n) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px; background:#fafafa; border:1px solid #e5e7eb; border-radius:8px; overflow:hidden;">
              <tr>
                <td style="padding:16px 20px; border-right:1px solid #e5e7eb; width:33%%;">
                  <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;margin-bottom:4px;">Ref. broj</div>
                  <div style="font-size:15px;font-weight:800;color:#08112a;">%s</div>
                </td>
                <td style="padding:16px 20px; border-right:1px solid #e5e7eb; width:33%%;">
                  <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;margin-bottom:4px;">Vreme upita</div>
                  <div style="font-size:13px;font-weight:600;color:#374151;">%s</div>
                </td>
                <td style="padding:16px 20px; width:33%%;">
                  <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;margin-bottom:4px;">Ukupno</div>
                  <div style="font-size:15px;font-weight:800;color:#f97316;">%s €</div>
                </td>
              </tr>
            </table>
            """.formatted(booking.getBookingRef(), booking.getCreatedAt().format(DATETIME_FMT), booking.getTotalPriceAll());
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
            <p style="margin:0 0 6px;font-family:Georgia,'Times New Roman',serif;font-size:22px;font-weight:600;color:#08112a;line-height:1.3;">
              Zdravo,
            </p>
            <p style="margin:0 0 28px;font-size:15px;color:#6b7280;line-height:1.7;">
              Primili smo vaš upit. Tim Escapii će Vas kontaktirati u roku od
              <strong style="color:#111827;">24 sata</strong> sa svim detaljima putovanja.
            </p>
            %s
            %s
            %s
            """.formatted(
            customerTripCard(booking, depDate, retDate, n),
            totalBox(booking.getTotalPriceAll(), n),
            nextStepsBlock()
        );

        return wrapBase(
            "Escapii",
            "#08112a",
            "Upit uspešno primljen",
            booking.getBookingRef(),
            "#f97316",
            "NA ČEKANJU",
            body,
            "© 2026 Escapii · Pitanja? Pišite nam na " + fromEmail
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
        String heading     = confirmed
            ? "Rezervacija potvrđena!"
            : "Rezervacija otkazana";
        String subtitle    = confirmed
            ? "Vaše putovanje je zvanično potvrđeno. Svi detalji su navedeni ispod."
            : "Vaša rezervacija je nažalost otkazana. Za pitanja, slobodno nas kontaktirajte.";

        String content;
        if (confirmed) {
            content = """
                <p style="margin:0 0 6px;font-family:Georgia,'Times New Roman',serif;font-size:22px;font-weight:600;color:#08112a;line-height:1.3;">
                  Zdravo,
                </p>
                <p style="margin:0 0 28px;font-size:15px;color:#6b7280;line-height:1.7;">%s</p>
                %s
                %s
                %s
                %s
                %s
                """.formatted(
                subtitle,
                customerTripCard(booking, depDate, retDate, n),
                totalBox(booking.getTotalPriceAll(), n),
                buildConfirmedTimeline(booking),
                buildPassengersSection(booking),
                buildPriceTable(booking, n)
            );
        } else {
            content = """
                <p style="margin:0 0 6px;font-family:Georgia,'Times New Roman',serif;font-size:22px;font-weight:600;color:#08112a;line-height:1.3;">
                  Zdravo,
                </p>
                <p style="margin:0 0 28px;font-size:15px;color:#6b7280;line-height:1.7;">%s</p>
                %s
                <div style="background:#fef2f2;border-left:3px solid #dc2626;border-radius:0 6px 6px 0;padding:16px 20px;margin-bottom:28px;">
                  <p style="margin:0;font-size:14px;color:#7f1d1d;line-height:1.7;">
                    Ukoliko smatrate da je ovo greška ili imate pitanja, kontaktirajte nas na
                    <a href="mailto:%s" style="color:#dc2626;font-weight:700;text-decoration:none;">%s</a>.
                    Radujemo se vašem sledećem putovanju sa nama!
                  </p>
                </div>
                """.formatted(
                subtitle,
                customerTripCard(booking, depDate, retDate, n),
                fromEmail, fromEmail
            );
        }

        return wrapBase(
            "Escapii",
            confirmed ? "#064e3b" : "#450a0a",
            heading,
            booking.getBookingRef(),
            accentColor,
            badgeLabel,
            content,
            "© 2026 Escapii · Pitanja? Pišite nam na " + fromEmail
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
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
              <tr><td>
                <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;
                            color:#9ca3af;margin-bottom:16px;padding-bottom:8px;border-bottom:2px solid #f3f4f6;">
                  Šta te čeka
                </div>
              </td></tr>
              <tr><td>
                <table width="100%%" cellpadding="0" cellspacing="0">

                  <tr>
                    <td style="width:44px;vertical-align:top;">
                      <div style="width:44px;height:44px;background:#dcfce7;border-radius:12px;text-align:center;line-height:44px;font-size:20px;">&#10003;</div>
                    </td>
                    <td style="width:12px;"></td>
                    <td style="vertical-align:top;padding-bottom:8px;">
                      <div style="font-size:11px;font-weight:700;color:#16a34a;letter-spacing:0.5px;text-transform:uppercase;margin-bottom:2px;">Danas &middot; %s</div>
                      <div style="font-size:15px;font-weight:700;color:#08112a;margin-bottom:3px;">Rezervacija potvrđena</div>
                      <div style="font-size:13px;color:#6b7280;line-height:1.5;">Sve je rezervisano. Možeš da se oputiš — doslovno.</div>
                    </td>
                  </tr>
                  <tr>
                    <td style="width:44px;text-align:center;padding:4px 0;">
                      <div style="width:2px;height:20px;background:#e5e7eb;margin:0 auto;"></div>
                    </td>
                    <td colspan="2"></td>
                  </tr>

                  <tr>
                    <td style="width:44px;vertical-align:top;">
                      <div style="width:44px;height:44px;background:#eff6ff;border-radius:12px;text-align:center;line-height:44px;font-size:20px;">&#127780;</div>
                    </td>
                    <td style="width:12px;"></td>
                    <td style="vertical-align:top;padding-bottom:8px;">
                      <div style="font-size:11px;font-weight:700;color:#3b82f6;letter-spacing:0.5px;text-transform:uppercase;margin-bottom:2px;">%s &middot; 7 dana pre polaska</div>
                      <div style="font-size:15px;font-weight:700;color:#08112a;margin-bottom:3px;">Prognoza vremena</div>
                      <div style="font-size:13px;color:#6b7280;line-height:1.5;">Šaljemo ti prognozu da znaš kako da se pakuješ — pre nego što saznaš kuda ideš.</div>
                    </td>
                  </tr>
                  <tr>
                    <td style="width:44px;text-align:center;padding:4px 0;">
                      <div style="width:2px;height:20px;background:#e5e7eb;margin:0 auto;"></div>
                    </td>
                    <td colspan="2"></td>
                  </tr>

                  <tr>
                    <td style="width:44px;vertical-align:top;">
                      <div style="width:44px;height:44px;background:#fff7ed;border-radius:12px;text-align:center;line-height:44px;font-size:20px;">&#9993;</div>
                    </td>
                    <td style="width:12px;"></td>
                    <td style="vertical-align:top;padding-bottom:8px;">
                      <div style="font-size:11px;font-weight:700;color:#f97316;letter-spacing:0.5px;text-transform:uppercase;margin-bottom:2px;">%s &middot; 72h pre polaska</div>
                      <div style="font-size:15px;font-weight:700;color:#08112a;margin-bottom:3px;">Koverta sa destinacijom</div>
                      <div style="font-size:13px;color:#6b7280;line-height:1.5;">Dobijaš kovertu sa destinacijom.</div>
                    </td>
                  </tr>
                  <tr>
                    <td style="width:44px;text-align:center;padding:4px 0;">
                      <div style="width:2px;height:20px;background:#e5e7eb;margin:0 auto;"></div>
                    </td>
                    <td colspan="2"></td>
                  </tr>

                  <tr>
                    <td style="width:44px;vertical-align:top;">
                      <div style="width:44px;height:44px;background:#08112a;border-radius:12px;text-align:center;line-height:44px;font-size:20px;">&#9992;</div>
                    </td>
                    <td style="width:12px;"></td>
                    <td style="vertical-align:top;">
                      <div style="font-size:11px;font-weight:700;color:#f97316;letter-spacing:0.5px;text-transform:uppercase;margin-bottom:2px;">%s &middot; Dan polaska</div>
                      <div style="font-size:15px;font-weight:700;color:#08112a;margin-bottom:3px;">Avantura počinje!</div>
                      <div style="font-size:13px;color:#6b7280;line-height:1.5;">Dođi na aerodrom i doživi nezaboravno putovanje.</div>
                    </td>
                  </tr>

                </table>
              </td></tr>
            </table>
            """.formatted(today, weatherStr, revealStr, depStr);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Shared content blocks
    // ═══════════════════════════════════════════════════════════════════════════

    private String customerTripCard(Booking booking, String depDate, String retDate, int n) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
              <tr><td style="background:#f9fafb;padding:12px 20px;border-bottom:1px solid #e5e7eb;">
                <span style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;">Detalji putovanja</span>
              </td></tr>
              <tr><td style="padding:4px 0;">
                <table width="100%%" cellpadding="0" cellspacing="0">
                  %s%s%s%s%s%s
                </table>
              </td></tr>
            </table>
            """.formatted(
            cRow("Aerodrom polaska", booking.getDepartureAirport(), true),
            cRow("Datum putovanja", depDate + " &rarr; " + retDate, false),
            cRow("Trajanje", booking.getSelectedDate().getNumberOfNights() + " noći", true),
            cRow("Putnici", n + (n == 1 ? " putnik" : " putnika"), false),
            cRow("Smeštaj", resolveAccomLabel(booking.getAccommodationType()), true),
            booking.getExclusionCount() > 0 ? cRow("Isključene dest.", buildExclusionsText(booking), false) : ""
        );
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

    private String totalBox(int total, int n) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;background:#08112a;border-radius:8px;overflow:hidden;">
              <tr>
                <td style="padding:24px 28px;">
                  <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:rgba(255,255,255,0.45);margin-bottom:6px;">Ukupna cena putovanja</div>
                  <div style="font-size:38px;font-weight:900;color:#f97316;line-height:1;margin-bottom:4px;">%s €</div>
                  <div style="font-size:13px;color:rgba(255,255,255,0.5);">za %d %s</div>
                </td>
              </tr>
            </table>
            """.formatted(total, n, n == 1 ? "putnika" : "putnika");
    }

    private String nextStepsBlock() {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
              <tr><td style="background:#f9fafb;padding:12px 20px;border-bottom:1px solid #e5e7eb;">
                <span style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#9ca3af;">Šta sledi?</span>
              </td></tr>
              <tr><td style="padding:8px 0;">
                %s
                %s
                %s
              </td></tr>
            </table>
            """.formatted(
            step("1", "#f97316", "Tim Escapii vam se javlja u roku od <strong style='color:#111827;'>24 sata</strong>"),
            step("2", "#f97316", "Destinacija se otkriva <strong style='color:#111827;'>72 sata pre polaska</strong>"),
            step("3", "#f97316", "Prognoza vremena stiže <strong style='color:#111827;'>7 dana pre polaska</strong>")
        );
    }

    private String step(String num, String color, String text) {
        return """
            <table cellpadding="0" cellspacing="0" style="width:100%%;border-bottom:1px solid #f3f4f6;">
              <tr>
                <td style="padding:12px 20px;width:36px;vertical-align:top;">
                  <div style="width:24px;height:24px;background:%s;border-radius:6px;text-align:center;line-height:24px;font-size:12px;font-weight:800;color:#fff;">%s</div>
                </td>
                <td style="padding:12px 20px 12px 0;font-size:14px;color:#6b7280;line-height:1.6;">%s</td>
              </tr>
            </table>
            """.formatted(color, num, text);
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
     * Generiše kompletan HTML dokument oko prosledjenog body sadržaja.
     */
    private String wrapBase(
        String logoLabel,
        String headerBg,
        String headingText,
        String refCode,
        String badgeColor,
        String badgeLabel,
        String bodyContent,
        String footerText
    ) {
        return """
            <!DOCTYPE html>
            <html lang="sr" xmlns:v="urn:schemas-microsoft-com:vml">
            <head>
              <meta charset="UTF-8">
              <meta name="x-apple-disable-message-reformatting">
              <meta http-equiv="x-ua-compatible" content="ie=edge">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="format-detection" content="telephone=no, date=no, address=no, email=no">
              <meta name="color-scheme" content="light dark">
              <meta name="supported-color-schemes" content="light dark">
              <!--[if mso]>
              <noscript><xml><o:OfficeDocumentSettings xmlns:o="urn:schemas-microsoft-com:office:office">
                <o:PixelsPerInch>96</o:PixelsPerInch>
              </o:OfficeDocumentSettings></xml></noscript>
              <style>td,th,div,p,a,h1,h2,h3{font-family:"Segoe UI",sans-serif;mso-line-height-rule:exactly;}</style>
              <![endif]-->
              <style>
                :root { color-scheme: light dark; supported-color-schemes: light dark; }
                @media (max-width:620px) {
                  .mob-full { width:100%% !important; }
                  .mob-pad  { padding:24px !important; }
                }
                @media (prefers-color-scheme:dark) {
                  .dm-bg  { background-color:#1a1a1a !important; }
                  .dm-card { background-color:#242424 !important; border-color:#333 !important; }
                  .dm-text { color:#e5e7eb !important; }
                }
              </style>
            </head>
            <body style="margin:0;padding:0;word-break:break-word;-webkit-font-smoothing:antialiased;background:#f3f4f6;" class="dm-bg">

              <!-- Preheader spacer -->
              <div style="display:none;max-height:0;overflow:hidden;mso-hide:all;">
                %s &#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&zwnj;&nbsp;
              </div>

              <div role="article" aria-roledescription="email" lang="sr">
              <table align="center" width="100%%" cellpadding="0" cellspacing="0" style="font-family:ui-sans-serif,system-ui,-apple-system,'Segoe UI',Arial,sans-serif;">
                <tr><td align="center" style="padding:32px 16px;" class="dm-bg">

                  <table class="mob-full" style="width:600px;max-width:600px;" cellpadding="0" cellspacing="0">

                    <!-- LOGO ROW -->
                    <tr><td style="padding-bottom:20px;text-align:center;">
                      <span style="font-size:13px;font-weight:800;letter-spacing:2px;text-transform:uppercase;color:#6b7280;">%s</span>
                    </td></tr>

                    <!-- CARD -->
                    <tr><td class="dm-card" style="background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;">

                      <!-- Top accent bar -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td style="background:%s;height:4px;line-height:4px;font-size:0;">&nbsp;</td></tr>
                      </table>

                      <!-- Header -->
                      <table width="100%%" cellpadding="0" cellspacing="0" style="background:%s;">
                        <tr><td style="padding:28px 36px;" class="mob-pad">
                          <p style="margin:0 0 12px;font-family:Georgia,'Times New Roman',serif;font-size:24px;font-weight:600;color:#ffffff;line-height:1.3;">
                            %s
                          </p>
                          <div>
                            <span style="display:inline-block;background:rgba(255,255,255,0.12);color:rgba(255,255,255,0.85);font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;padding:5px 12px;border-radius:4px;">
                              %s
                            </span>
                            &nbsp;
                            <span style="display:inline-block;background:%s;color:#fff;font-size:11px;font-weight:800;letter-spacing:1.5px;text-transform:uppercase;padding:5px 12px;border-radius:4px;">
                              %s
                            </span>
                          </div>
                        </td></tr>
                      </table>

                      <!-- Body -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td style="padding:32px 36px;" class="mob-pad dm-text">
                          %s
                        </td></tr>
                      </table>

                    </td></tr>

                    <!-- Footer -->
                    <tr><td style="padding:20px 0;text-align:center;font-size:12px;color:#9ca3af;line-height:1.8;">
                      %s
                    </td></tr>

                  </table>
                </td></tr>
              </table>
              </div>
            </body>
            </html>
            """.formatted(
            headingText,      // preheader
            logoLabel,        // logo label
            badgeColor,       // top accent bar color
            headerBg,         // header background
            headingText,      // h1
            refCode,          // ref badge (gray)
            badgeColor,       // status badge color
            badgeLabel,       // status badge text
            bodyContent,      // main content
            footerText        // footer
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
            long daysLeft  = today.until(dep).getDays();
            String daysStr = daysLeft == 0 ? "danas" : "za " + daysLeft + (daysLeft == 1 ? " dan" : " dana");

            if (dep.equals(weatherDay)) {
                weatherRows.append(row(name, email, ref, airport, depStr, daysStr));
            } else if (dep.equals(destinationDay)) {
                destinationRows.append(row(name, email, ref, airport, depStr, daysStr));
            } else {
                // Preview — šta ga čeka
                String nextTask = dep.isAfter(weatherDay)
                    ? "🌤 prognoza: " + dep.minusDays(7).format(DATE_FMT) + " · ✉️ koverta: " + dep.minusDays(3).format(DATE_FMT)
                    : "✉️ koverta: " + dep.minusDays(3).format(DATE_FMT);
                previewRows.append(previewRow(name, ref, airport, depStr, daysStr, nextTask));
            }
        }

        String todayStr = today.format(DATE_FMT);

        String weatherSection = !weatherRows.isEmpty() ? section(
            "🌤 Danas pošalji prognozu vremena", "#fff7ed", "#fed7aa", "#7c2d12", weatherRows.toString()) : "";

        String destinationSection = !destinationRows.isEmpty() ? section(
            "✉️ Danas pošalji kovertu s destinacijom", "#fff1f2", "#fecdd3", "#881337", destinationRows.toString()) : "";

        String nothingToday = weatherRows.isEmpty() && destinationRows.isEmpty() ? """
            <div style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:10px;padding:16px 20px;margin-bottom:24px;">
              <p style="margin:0;font-size:14px;color:#14532d;font-weight:600;">✓ Nema zadataka za danas</p>
            </div>""" : "";

        String previewSection = !previewRows.isEmpty() ? """
            <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;
                        color:#6b7280;margin:24px 0 10px;">Narednih 14 dana</div>
            <table width="100%%" cellpadding="0" cellspacing="0"
                   style="background:#fafafa;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;">
              %s
            </table>""".formatted(previewRows.toString()) : "";

        String body = """
            <p style="margin:0 0 4px;font-family:Georgia,'Times New Roman',serif;font-size:22px;
                      font-weight:600;color:#08112a;">Jutarnji podsetnik — %s</p>
            <p style="margin:0 0 24px;font-size:14px;color:#6b7280;">%d rezervacija u narednih 14 dana</p>
            %s%s%s%s
            """.formatted(todayStr, bookings.size(),
                nothingToday, weatherSection, destinationSection, previewSection);

        boolean hasUrgent = !weatherRows.isEmpty() || !destinationRows.isEmpty();
        String html = wrapBase(
            "Escapii Ops", "#08112a", "Jutarnji pregled", "",
            hasUrgent ? "#f97316" : "#16a34a",
            hasUrgent ? "AKCIJA POTREBNA" : "SVE U REDU",
            body,
            "Escapii interni sistem · Automatska poruka · Ne odgovarati"
        );

        sendEmail(opsEmail, "📋 Escapii — " + todayStr, html);
    }

    private String row(String name, String email, String ref, String airport, String dep, String daysStr) {
        return """
            <tr style="border-bottom:1px solid rgba(0,0,0,.06);">
              <td style="padding:12px 16px;">
                <div style="font-weight:700;font-size:14px;color:#08112a;">%s
                  <a href="mailto:%s" style="font-weight:400;font-size:13px;color:#2563eb;margin-left:8px;">%s</a>
                </div>
                <div style="font-size:12px;color:#6b7280;margin-top:2px;">%s · %s · polazak %s (%s)</div>
              </td>
            </tr>""".formatted(name, email, email, ref, airport, dep, daysStr);
    }

    private String previewRow(String name, String ref, String airport, String dep, String daysStr, String nextTask) {
        return """
            <tr style="border-bottom:1px solid #f0f0f0;">
              <td style="padding:11px 16px;">
                <div style="font-weight:600;font-size:13px;color:#08112a;">%s
                  <span style="font-weight:400;color:#9ca3af;font-size:12px;"> · %s · polazak %s (%s)</span>
                </div>
                <div style="font-size:12px;color:#6b7280;margin-top:3px;">%s</div>
              </td>
            </tr>""".formatted(name, ref, airport, dep, daysStr, nextTask);
    }

    private String section(String title, String bg, String border, String titleColor, String rows) {
        return """
            <div style="font-size:13px;font-weight:700;color:%s;margin-bottom:8px;">%s</div>
            <table width="100%%" cellpadding="0" cellspacing="0"
                   style="background:%s;border:1px solid %s;border-radius:10px;overflow:hidden;margin-bottom:20px;">
              %s
            </table>""".formatted(titleColor, title, bg, border, rows);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Waitlist
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Async
    public void sendWaitlistConfirmation(String email, String airport) {
        String airportName = resolveAirportName(airport);
        String body = """
            <p style="margin:0 0 6px;font-family:Georgia,'Times New Roman',serif;font-size:22px;font-weight:600;color:#08112a;line-height:1.3;">
              Dodali smo te na listu čekanja!
            </p>
            <p style="margin:0 0 28px;font-size:15px;color:#6b7280;line-height:1.7;">
              Čim se otvore novi termini za aerodrom <strong>%s</strong>, bićeš prvi koji to sazna.
            </p>
            <div style="background:#f0fdf4;border-left:3px solid #16a34a;border-radius:0 8px 8px 0;padding:18px 20px;margin-bottom:28px;">
              <p style="margin:0;font-size:14px;color:#14532d;line-height:1.7;">
                Poslaćemo ti email čim se pojave dostupni termini.
              </p>
            </div>
            <p style="margin:0;font-size:13px;color:#9ca3af;line-height:1.6;">
              Ako si se prijavio greškom, jednostavno ignoriši ovaj email.
            </p>
            """.formatted(esc(airportName));

        String html = wrapBase(
            "Escapii", "#08112a", "Lista čekanja", "",
            "#16a34a", "NOVI TERMINI USKORO", body,
            "Escapii · escapii.com"
        );
        sendEmail(email, "Na listi si čekanja — Escapii", html);
    }

    @Override
    @Async
    public void sendWaitlistNotification(String email, String airport) {
        String airportName = resolveAirportName(airport);
        String body = """
            <p style="margin:0 0 6px;font-family:Georgia,'Times New Roman',serif;font-size:22px;font-weight:600;color:#08112a;line-height:1.3;">
              Otvorili su se novi termini!
            </p>
            <p style="margin:0 0 28px;font-size:15px;color:#6b7280;line-height:1.7;">
              Čekao/la si — i isplatilo se. Dostupni su novi termini za polazak iz <strong>%s</strong>.
            </p>
            <div style="background:#fff7ed;border-left:3px solid #f97316;border-radius:0 8px 8px 0;padding:18px 20px;margin-bottom:28px;">
              <p style="margin:0;font-size:14px;color:#7c2d12;line-height:1.7;">
                Termini se brzo popunjavaju — rezerviši na vreme!
              </p>
            </div>
            <div style="text-align:center;margin-bottom:28px;">
              <a href="https://escapii.com" style="display:inline-block;background:#f97316;color:#fff;font-weight:700;font-size:15px;padding:14px 36px;border-radius:100px;text-decoration:none;letter-spacing:.3px;">
                Rezerviši sada &rarr;
              </a>
            </div>
            <p style="margin:0;font-size:13px;color:#9ca3af;line-height:1.6;">
              Primio/la si ovaj email jer si se prijavio/la na listu čekanja za aerodrom %s.
            </p>
            """.formatted(esc(airportName), esc(airportName));

        String html = wrapBase(
            "Escapii", "#08112a", "Novi termini dostupni", "",
            "#f97316", "NOVI TERMINI", body,
            "Escapii · escapii.com"
        );
        sendEmail(email, "Otvorili su se novi termini — Escapii", html);
    }
}
