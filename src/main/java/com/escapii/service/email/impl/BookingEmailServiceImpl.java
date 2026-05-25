package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.model.PassengerInfo;
import com.escapii.util.LogUtils;
import com.escapii.service.AppErrorService;
import com.escapii.service.DestinationService;
import com.escapii.service.email.BookingEmailService;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.escapii.service.impl.PriceCalculatorImpl;

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

    /** @Lazy sprečava circular dependency: AppErrorService → emailAlert → BookingEmailServiceImpl */
    @Autowired @Lazy
    private AppErrorService appErrorService;

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
        boolean ok = sender.send(
            teamEmail,
            "Novi upit %s — %s %s".formatted(booking.getBookingRef(), booking.getFirstName(), booking.getLastName()),
            buildTeamEmailHtml(booking)
        );
        if (!ok) {
            log.warn("[Email] Tim-notifikacija NIJE poslata za booking {}", booking.getBookingRef());
            recordEmailError("EMAIL team-notification ref=" + booking.getBookingRef());
        }
    }

    @Override
    @Async
    public void sendCustomerConfirmation(Booking booking) {
        boolean ok = sender.send(
            booking.getEmail(),
            "Upit primljen — %s".formatted(booking.getBookingRef()),
            buildCustomerReceivedHtml(booking)
        );
        if (!ok) {
            log.warn("[Email] Potvrda korisniku NIJE poslata za booking {} ({})",
                    booking.getBookingRef(), LogUtils.maskEmail(booking.getEmail()));
            recordEmailError("EMAIL customer-confirmation ref=" + booking.getBookingRef());
        }
    }

    @Override
    @Async
    public void sendBookingConfirmed(Booking booking) {
        boolean ok = sender.send(
            booking.getEmail(),
            "Rezervacija potvrđena — %s".formatted(booking.getBookingRef()),
            buildCustomerStatusHtml(booking, true)
        );
        if (ok) {
            log.info("[Email] Poslat CONFIRMED email na adresu {} za booking {}", LogUtils.maskEmail(booking.getEmail()), booking.getBookingRef());
        } else {
            log.warn("[Email] CONFIRMED email NIJE poslat za booking {} ({})",
                    booking.getBookingRef(), LogUtils.maskEmail(booking.getEmail()));
            recordEmailError("EMAIL booking-confirmed ref=" + booking.getBookingRef());
        }
    }

    @Override
    @Async
    public void sendBookingCancelled(Booking booking) {
        boolean ok = sender.send(
            booking.getEmail(),
            "Rezervacija otkazana — %s".formatted(booking.getBookingRef()),
            buildCustomerStatusHtml(booking, false)
        );
        if (ok) {
            log.info("[Email] Poslat CANCELLED email na adresu {} za booking {}", LogUtils.maskEmail(booking.getEmail()), booking.getBookingRef());
        } else {
            log.warn("[Email] CANCELLED email NIJE poslat za booking {} ({})",
                    booking.getBookingRef(), LogUtils.maskEmail(booking.getEmail()));
            recordEmailError("EMAIL booking-cancelled ref=" + booking.getBookingRef());
        }
    }

    /**
     * Snima grešku slanja emaila u AppError dashboard (vidljivo adminu u 🚨 Greške tabu).
     * Koristi RuntimeException kao nosač poruke — stack trace nije relevantan za email greške.
     */
    private void recordEmailError(String context) {
        try {
            appErrorService.record(context, 0,
                new RuntimeException("Email nije poslat — proveriti SMTP konfiguraciju i log"));
        } catch (Exception ex) {
            log.error("[Email] Nije moguće snimiti email grešku u AppErrorService: {}", ex.getMessage());
        }
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
                tRow("Email", "<a href='mailto:" + booking.getEmail() + "' style='color:#a85e44;text-decoration:none;'>" + booking.getEmail() + "</a>") +
                tRow("Telefon", "<a href='tel:" + booking.getPhone() + "' style='color:#a85e44;text-decoration:none;'>" + booking.getPhone() + "</a>")
            ),
            teamSection("Putovanje",
                tRow("Aerodrom", booking.getDepartureAirport()) +
                tRow("Datum", "<strong>" + depDate + " &rarr; " + retDate + "</strong>") +
                tRow("Noći", booking.getSelectedDate().getNumberOfNights() + " noći") +
                tRow("Putnici", passengerNamesList(booking)) +
                tRow("Smeštaj", EmailHtmlBuilder.resolveAccomLabel(booking.getAccommodationType())) +
                tRow("Isključene dest.", buildExclusionsText(booking)) +
                tRow("Presedanje OK", Boolean.TRUE.equals(booking.getHasConnectingFlights()) ? "✔ Da" : "✘ Ne — samo direktni letovi")
            ),
            buildPassengersSection(booking),
            buildPriceTable(booking, n)
        );

        String notes = buildNotesBox(booking.getNotes());

        return EmailHtmlBuilder.wrapBase(
            "#2D5F6B",
            "#1e1b4b",
            EmailHtmlBuilder.statusBadge("Novi upit", "blue"),
            "Novi upit stigao",
            depDate + " → " + retDate + " · " + n + (n == 1 ? " putnik" : " putnika"),
            booking.getBookingRef(),
            body + notes,
            "Interni email — escapii ops tim · Nije za prosleđivanje",
            false
        );
    }

    private String metaSection(Booking booking, String dep, String ret, int n) {
        String deadline = booking.getCreatedAt().plusHours(24).format(EmailHtmlBuilder.DATETIME_FMT);
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px;background:#faf6ee;border:1px solid #ebe1cf;border-radius:6px;">
              <tr>
                <td width="25%%" style="padding:12px 16px;border-right:1px solid #ebe1cf;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:3px;">Ref. broj</div>
                  <div style="font-size:14px;font-weight:700;color:#2D5F6B;">%s</div>
                </td>
                <td width="25%%" style="padding:12px 16px;border-right:1px solid #ebe1cf;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:3px;">Primljeno</div>
                  <div style="font-size:13px;font-weight:600;color:#1a1410;">%s</div>
                </td>
                <td width="25%%" style="padding:12px 16px;border-right:1px solid #ebe1cf;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:3px;">Ukupno</div>
                  <div style="font-size:14px;font-weight:700;color:#a85e44;">%s €</div>
                </td>
                <td width="25%%" style="padding:12px 16px;width:25%%;">
                  <div style="font-size:10px;text-transform:uppercase;letter-spacing:0.8px;color:#a89888;margin-bottom:3px;">Rok odgovora</div>
                  <div style="font-size:13px;font-weight:700;color:#9b3a2a;">%s</div>
                </td>
              </tr>
            </table>
            """.formatted(booking.getBookingRef(), booking.getCreatedAt().format(EmailHtmlBuilder.DATETIME_FMT),
                booking.getTotalPriceAll(), deadline);
    }

    private String teamSection(String title, String rows) {
        return """
            <div style="margin-bottom:28px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;margin-bottom:12px;padding-bottom:8px;border-bottom:2px solid #ebe1cf;">%s</div>
              <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;">%s</table>
            </div>
            """.formatted(title, rows);
    }

    private String tRow(String label, String value) {
        return """
            <tr>
              <td width="38%%" style="width:38%%;padding:9px 0;font-size:13px;color:#a89888;font-weight:600;border-bottom:1px solid #ebe1cf;">%s</td>
              <td width="62%%" style="width:62%%;padding:9px 0;font-size:14px;color:#1a1410;border-bottom:1px solid #ebe1cf;">%s</td>
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
            <p style="margin:0 0 24px;font-size:15px;color:#1a1410;line-height:1.65;">
              Draga/i <strong style="color:#1a1410;">%s</strong>,<br><br>
              uspešno smo primili vaš upit za putovanje. Naš tim pregledava vaše preference
              i kontaktiraće vas u roku od <strong style="color:#1a1410;">24 sata</strong> sa svim detaljima i potvrdom rezervacije.
            </p>
            %s
            %s
            %s
            %s
            %s
            """.formatted(
            EmailHtmlBuilder.esc(booking.getFirstName()),
            customerTripCard(booking, depDate, retDate, n),
            buildPassengersSection(booking),
            EmailHtmlBuilder.totalBox(booking.getTotalPriceAll(), n),
            buildPriceTable(booking, n),
            nextStepsBlock()
        );

        return EmailHtmlBuilder.wrapBase(
            "#a85e44",
            "#1a1410",
            EmailHtmlBuilder.statusBadge("Na čekanju", "orange"),
            "Vaš upit je primljen",
            "Hvala što ste nam se obratili — naš tim će vas kontaktirati u roku od 24 sata.",
            booking.getBookingRef(),
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

        String heading     = confirmed ? "Rezervacija potvrđena!" : "Rezervacija otkazana";
        String subtitle    = confirmed
            ? "vaša rezervacija je zvanično potvrđena! Sve je spremno — vi samo spakujte stvari i prepustite se misteriji. ✦"
            : "sa žaljenjem vam obaveštavamo da je vaša rezervacija otkazana. Razumemo da su planovi nekad nepredvidivi — i nadamo se da ćete nam ponovo ukazati poverenje.<br><br>Vaša avantura nas čeka — kada budete spremni, mi ćemo biti tu. ✦";

        String content;
        if (confirmed) {
            content = """
                <p style="margin:0 0 20px;font-size:15px;color:#1a1410;line-height:1.65;">
                  Draga/i <strong style="color:#1a1410;">%s</strong>,<br><br>%s
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
                <p style="margin:0 0 20px;font-size:15px;color:#1a1410;line-height:1.65;">
                  Draga/i <strong style="color:#1a1410;">%s</strong>,<br><br>%s
                </p>
                %s
                <div style="background:#fbeeec;border:1px solid #e9c5bd;border-left:3px solid #9b3a2a;border-radius:6px;padding:16px 20px;margin-bottom:24px;">
                  <div style="font-size:13px;font-weight:700;color:#9b3a2a;margin-bottom:6px;">Pitanja ili žalba?</div>
                  <p style="margin:0;font-size:13px;color:#1a1410;line-height:1.7;">
                    Kontaktirajte nas na
                    <a href="mailto:%s" style="color:#9b3a2a;font-weight:600;text-decoration:none;">%s</a>.
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
            confirmed ? "#1d6042" : "#9b3a2a",
            confirmed ? "#064e3b" : "#450a0a",
            confirmed
                ? EmailHtmlBuilder.statusBadge("Potvrđena", "green")
                : EmailHtmlBuilder.statusBadge("Otkazana", "red"),
            heading,
            confirmed
                ? "Vaše putovanje je zvanično u kalendaru. Jedino što ne znate — kuda idete! ✦"
                : "Nadamo se da ćemo vas videti na sledećem putovanju.",
            booking.getBookingRef(),
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
        var revealDate  = dep.minusDays(2);

        String today      = java.time.LocalDate.now().format(EmailHtmlBuilder.DATE_FMT);
        String weatherStr = weatherDate.format(EmailHtmlBuilder.DATE_FMT);
        String revealStr  = revealDate.format(EmailHtmlBuilder.DATE_FMT);
        String depStr     = dep.format(EmailHtmlBuilder.DATE_FMT);

        return """
            <div style="margin-bottom:24px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;margin-bottom:16px;">Šta vas čeka</div>
              %s
              %s
              %s
              %s
            </div>
            """.formatted(
            EmailHtmlBuilder.timelineItem("✓", "#eef6f0", "#1d6042",
                "Rezervacija potvrđena",
                "Danas · " + today,
                "Sve je rezervisano — letovi, smeštaj, transfer. Možete se opustiti — doslovno."),
            EmailHtmlBuilder.timelineItem("🌤", "#fff5eb", "#a85e44",
                "Vremenska prognoza",
                weatherStr + " · 7 dana pre polaska",
                "Dobijate prognozu da znate šta da spakujete. Destinacija? I dalje tajna!"),
            EmailHtmlBuilder.timelineItem("✉", "#eaf0f3", "#2D5F6B",
                "Koverta s destinacijom",
                revealStr + " · 48h pre polaska",
                "Konačno — otkrivate gde idete!"),
            EmailHtmlBuilder.timelineItem("✈", "#f5efe2", "#ebe1cf",
                "Avantura počinje!",
                depStr + " · Dan polaska",
                "Dođite na aerodrom i dozvolite sebi da budete iznenađeni.")
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Boarding pass block — replaces customerTripCard in customer-received email
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generiše boarding-pass vizuelni blok za email korisniku.
     * Koristi table-based inline CSS za maksimalnu kompatibilnost s email klijentima.
     * Prikazuje logo, rutu, datume, broj putnika i stvarna imena putnika.
     */
    private String buildBoardingPassBlock(Booking booking, String depDate, String retDate, int n) {
        String airportCode = EmailHtmlBuilder.esc(booking.getDepartureAirport());
        String airportCity = EmailHtmlBuilder.resolveAirportName(booking.getDepartureAirport());

        // Passenger names — show actual passengers, fall back to booking holder
        List<PassengerInfo> pax = booking.getPassengers();
        String passengerNamesHtml;
        String avatarInitials;

        if (pax != null && !pax.isEmpty()) {
            StringBuilder names = new StringBuilder();
            names.append("<div style=\"font-size:15px;font-weight:700;color:#1a1410;line-height:1.3;\">")
                 .append(EmailHtmlBuilder.esc(pax.get(0).getName()))
                 .append("</div>");
            for (int i = 1; i < pax.size(); i++) {
                names.append("<div style=\"font-size:13px;font-weight:600;color:#6b5d4f;line-height:1.4;margin-top:1px;\">")
                     .append(EmailHtmlBuilder.esc(pax.get(i).getName()))
                     .append("</div>");
            }
            passengerNamesHtml = names.toString();
            String[] parts = pax.get(0).getName().split(" ", 2);
            avatarInitials = (parts.length >= 2)
                ? String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[1].charAt(0))
                : parts[0].length() > 0 ? String.valueOf(parts[0].charAt(0)) : "?";
        } else {
            passengerNamesHtml = "<div style=\"font-size:15px;font-weight:700;color:#1a1410;\">"
                + EmailHtmlBuilder.esc(booking.getFirstName() + " " + booking.getLastName())
                + "</div>";
            avatarInitials = String.valueOf(booking.getFirstName().charAt(0))
                + String.valueOf(booking.getLastName().charAt(0));
        }

        return """
            <!-- ═══ BOARDING PASS ══════════════════════════════════════════════ -->
            <table width="100%%" cellpadding="0" cellspacing="0"
                   style="margin-bottom:20px;border-radius:18px;overflow:hidden;
                          border:1px solid rgba(200,119,90,0.18);
                          box-shadow:0 8px 32px rgba(168,94,68,0.14);">

              <!-- Gradient header -->
              <tr>
                <td style="background:linear-gradient(135deg,#a85e44 0%%,#c8775a 50%%,#e29070 100%%);
                           padding:22px 30px 60px;border-radius:18px 18px 0 0;">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr>
                      <td style="vertical-align:middle;">
                        <img src="%s" alt="escapii" height="34"
                             style="display:block;border:0;height:34px;max-width:140px;"
                             onerror="this.style.display='none'">
                      </td>
                      <td style="text-align:right;vertical-align:middle;">
                        <span style="display:inline-block;font-size:9px;letter-spacing:3px;
                                     font-weight:700;color:#fff;
                                     background:rgba(255,255,255,0.14);
                                     border:1px solid rgba(255,255,255,0.3);
                                     padding:6px 14px;border-radius:100px;">
                          ✦ BOARDING PASS
                        </span>
                      </td>
                    </tr>
                  </table>
                </td>
              </tr>

              <!-- White body — pulled up with negative margin simulation via inner table -->
              <tr>
                <td style="background:#f5efe2;padding:0 20px 0;">
                  <table width="100%%" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:18px;margin-top:-32px;
                                box-shadow:0 -4px 24px rgba(168,94,68,0.1);overflow:hidden;">
                    <tr>
                      <td style="padding:30px 30px 24px;">

                        <!-- Route -->
                        <table width="100%%" cellpadding="0" cellspacing="0"
                               style="margin-bottom:22px;">
                          <tr>
                            <td style="width:40%%;vertical-align:top;">
                              <div style="font-family:Georgia,'Times New Roman',serif;
                                          font-size:54px;font-weight:700;color:#1a1410;
                                          line-height:0.9;letter-spacing:-1px;">%s</div>
                              <div style="font-size:13px;font-weight:600;color:#1a1410;
                                          margin-top:8px;">%s</div>
                              <div style="font-size:11px;color:#a89888;margin-top:2px;">
                                Polazni aerodrom</div>
                            </td>
                            <td style="width:20%%;text-align:center;vertical-align:middle;
                                       padding:0 10px;">
                              <div style="font-size:20px;color:#c8775a;line-height:1;">✈</div>
                              <div style="border-top:2px dashed #e29070;margin:8px 0;"></div>
                            </td>
                            <td style="width:40%%;vertical-align:top;text-align:right;">
                              <div style="font-family:Georgia,'Times New Roman',serif;
                                          font-size:54px;font-weight:700;color:#a85e44;
                                          font-style:italic;line-height:0.9;">???</div>
                              <div style="font-size:13px;font-weight:600;color:#1a1410;
                                          margin-top:8px;">Iznenađenje</div>
                              <div style="font-size:11px;color:#a89888;margin-top:2px;">
                                otkrij 48h pre polaska</div>
                            </td>
                          </tr>
                        </table>

                        <!-- Meta strip: Polazak · Povratak · Putnika -->
                        <table width="100%%" cellpadding="0" cellspacing="0"
                               style="background:linear-gradient(135deg,
                                        rgba(200,119,90,0.07),rgba(200,119,90,0.02));
                                      border:1px solid rgba(200,119,90,0.16);
                                      border-radius:12px;margin-bottom:22px;">
                          <tr>
                            <td style="padding:14px 18px;
                                       border-right:1px solid rgba(200,119,90,0.16);">
                              <div style="font-size:8px;letter-spacing:3px;
                                          text-transform:uppercase;color:#a89888;font-weight:700;">
                                Polazak</div>
                              <div style="font-size:14px;font-weight:700;color:#1a1410;
                                          margin-top:5px;font-family:'Courier New',monospace;">
                                %s</div>
                            </td>
                            <td style="padding:14px 18px;
                                       border-right:1px solid rgba(200,119,90,0.16);">
                              <div style="font-size:8px;letter-spacing:3px;
                                          text-transform:uppercase;color:#a89888;font-weight:700;">
                                Povratak</div>
                              <div style="font-size:14px;font-weight:700;color:#1a1410;
                                          margin-top:5px;font-family:'Courier New',monospace;">
                                %s</div>
                            </td>
                            <td style="padding:14px 18px;">
                              <div style="font-size:8px;letter-spacing:3px;
                                          text-transform:uppercase;color:#a89888;font-weight:700;">
                                Putnika</div>
                              <div style="font-size:14px;font-weight:700;color:#1a1410;
                                          margin-top:5px;font-family:'Courier New',monospace;">
                                %d</div>
                            </td>
                          </tr>
                        </table>

                        <!-- Bottom: passengers + ref -->
                        <table width="100%%" cellpadding="0" cellspacing="0"
                               style="border-top:1.5px dashed rgba(26,20,16,0.12);padding-top:20px;">
                          <tr>
                            <td style="padding-top:18px;vertical-align:top;">
                              <table cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="vertical-align:top;">
                                    <!-- Avatar -->
                                    <div style="width:40px;height:40px;border-radius:50%%;
                                                background:linear-gradient(135deg,#e29070,#c8775a);
                                                color:#fff;font-weight:700;font-size:13px;
                                                text-align:center;line-height:40px;
                                                letter-spacing:0.04em;margin-right:12px;
                                                box-shadow:0 4px 12px rgba(200,119,90,0.45);
                                                display:inline-block;">%s</div>
                                  </td>
                                  <td style="padding-left:12px;vertical-align:top;">
                                    <div style="font-size:8px;letter-spacing:3px;
                                                text-transform:uppercase;color:#a89888;
                                                font-weight:700;margin-bottom:5px;">Putnici</div>
                                    %s
                                  </td>
                                </tr>
                              </table>
                            </td>
                            <td style="padding-top:18px;vertical-align:top;text-align:right;">
                              <div style="font-size:8px;letter-spacing:3px;
                                          text-transform:uppercase;color:#a89888;
                                          font-weight:700;margin-bottom:5px;">Rezervacija</div>
                              <div style="font-family:'Courier New',monospace;font-size:13px;
                                          font-weight:700;color:#a85e44;letter-spacing:0.04em;">
                                %s</div>
                            </td>
                          </tr>
                        </table>

                      </td>
                    </tr>
                  </table>
                </td>
              </tr>

              <!-- Tear line -->
              <tr>
                <td style="background:#f5efe2;padding:0;">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr>
                      <td style="width:14px;">
                        <div style="width:28px;height:28px;background:#ebe1cf;
                                    border-radius:50%%;margin-left:-14px;"></div>
                      </td>
                      <td style="border-top:1px dashed rgba(26,20,16,0.2);"></td>
                      <td style="width:14px;">
                        <div style="width:28px;height:28px;background:#ebe1cf;
                                    border-radius:50%%;margin-right:-14px;"></div>
                      </td>
                    </tr>
                  </table>
                </td>
              </tr>

              <!-- Stub -->
              <tr>
                <td style="background:#f5efe2;padding:18px 30px 26px;
                           border-radius:0 0 18px 18px;">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr>
                      <td style="width:22%%;">
                        <div style="font-size:8px;letter-spacing:3px;text-transform:uppercase;
                                    color:#a89888;font-weight:700;">Gate</div>
                        <div style="font-size:16px;font-weight:700;color:#1a1410;margin-top:4px;
                                    font-family:'Courier New',monospace;">—</div>
                      </td>
                      <td style="width:28%%;">
                        <div style="font-size:8px;letter-spacing:3px;text-transform:uppercase;
                                    color:#a89888;font-weight:700;">Boarding</div>
                        <div style="font-size:14px;font-weight:700;color:#1a1410;margin-top:4px;
                                    font-family:'Courier New',monospace;">USKORO</div>
                      </td>
                      <td style="width:22%%;">
                        <div style="font-size:8px;letter-spacing:3px;text-transform:uppercase;
                                    color:#a89888;font-weight:700;">Sedište</div>
                        <div style="font-size:16px;font-weight:700;color:#1a1410;margin-top:4px;
                                    font-family:'Courier New',monospace;">—</div>
                      </td>
                      <td style="text-align:right;vertical-align:bottom;">
                        <!-- Decorative barcode -->
                        <div style="display:inline-flex;gap:2px;height:38px;
                                    align-items:stretch;vertical-align:bottom;">
                          <div style="width:2px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:4px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:1px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:3px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:2px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:5px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:1px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:2px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:4px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:2px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:1px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:3px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:2px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:5px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:1px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:4px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:2px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:3px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:1px;background:#1a1410;border-radius:1px;"></div>
                          <div style="width:2px;background:#1a1410;border-radius:1px;"></div>
                        </div>
                      </td>
                    </tr>
                  </table>
                </td>
              </tr>

            </table>
            <!-- ═══ END BOARDING PASS ════════════════════════════════════════════ -->
            """.formatted(
            EmailHtmlBuilder.LOGO_WHITE_URL,   // logo src
            airportCode,                        // IATA (BEG)
            airportCity,                        // city name
            depDate,                            // Polazak
            retDate,                            // Povratak
            n,                                  // Putnika
            avatarInitials,                     // avatar
            passengerNamesHtml,                 // names list
            EmailHtmlBuilder.esc(booking.getBookingRef())  // ref code
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Shared content blocks
    // ═══════════════════════════════════════════════════════════════════════════

    private String customerTripCard(Booking booking, String depDate, String retDate, int n) {
        return customerTripCardStyled(booking, depDate, retDate, n, false);
    }

    private String customerTripCardStyled(Booking booking, String depDate, String retDate, int n, boolean cancelled) {
        String borderColor = cancelled ? "#9b3a2a" : "#a85e44"; // purple for active, red for cancelled
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
        rows.append(EmailHtmlBuilder.dRow("Putnici",   passengerNamesList(booking)));
        rows.append(EmailHtmlBuilder.dRow("Smeštaj",   EmailHtmlBuilder.resolveAccomLabel(booking.getAccommodationType())));
        if (Boolean.TRUE.equals(booking.getHasConnectingFlights())) rows.append(EmailHtmlBuilder.dRow("Presedanje", "✔ Prihvaćeno"));
        if (booking.getExclusionCount() > 0) rows.append(EmailHtmlBuilder.dRow("Isključene dest.", buildExclusionsText(booking)));
        if (!cancelled) rows.append(EmailHtmlBuilder.dRowMystery("Destinacija", "✦ Iznenađenje!"));

        return """
            <div style="background:#faf6ee;border:1px solid #ebe1cf;border-left:3px solid %s;border-radius:6px;padding:18px 20px;margin:0 0 20px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;margin-bottom:14px;">%s</div>
              <table width="100%%" cellpadding="0" cellspacing="0">%s</table>
            </div>
            """.formatted(borderColor, cardTitle, rows);
    }

    private String cRow(String label, String value, boolean shaded) {
        String bg = shaded ? "background:#faf6ee;" : "";
        return """
            <tr style="%s">
              <td width="40%%" style="width:40%%;padding:11px 20px;font-size:13px;color:#a89888;font-weight:600;border-bottom:1px solid #ebe1cf;">%s</td>
              <td width="60%%" style="width:60%%;padding:11px 20px;font-size:14px;color:#1a1410;font-weight:500;border-bottom:1px solid #ebe1cf;">%s</td>
            </tr>
            """.formatted(bg, label, value);
    }

    private String nextStepsBlock() {
        return """
            <div style="margin-bottom:24px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;margin-bottom:14px;">Šta vas čeka</div>
              %s
              %s
              %s
              %s
            </div>
            """.formatted(
            EmailHtmlBuilder.step("1", "Tim Escapii vam se javlja u roku od <strong style='color:#2D5F6B;'>24 sata</strong>",
                      "Proveravamo dostupnost i potvrđujemo vašu rezervaciju."),
            EmailHtmlBuilder.step("2", "Detalji rezervacije i uplata",
                      "Javićemo vam se sa svim detaljima — koracima za uplatu, pravilima putovanja i svim informacijama koje su vam potrebne pre polaska."),
            EmailHtmlBuilder.step("3", "Vremenska prognoza — <strong style='color:#2D5F6B;'>7 dana pre polaska</strong>",
                      "Dobijate prognozu da znate šta da spakujete. Destinacija? I dalje tajna. 🌤"),
            EmailHtmlBuilder.step("4", "Koverta s destinacijom — <strong style='color:#2D5F6B;'>48h pre polaska</strong>",
                      "Koverta otkriva gde putujete. ✉")
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Putnici
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildPassengersSection(Booking booking) {
        List<PassengerInfo> passengers = booking.getPassengers();
        if (passengers == null || passengers.isEmpty()) return "";

        StringBuilder cards = new StringBuilder();
        for (int i = 0; i < passengers.size(); i++) {
            PassengerInfo p = passengers.get(i);
            String name     = EmailHtmlBuilder.esc(p.getName());
            String dob      = p.getDateOfBirth() != null ? p.getDateOfBirth().format(EmailHtmlBuilder.DATE_FMT) : "—";
            String gender   = "M".equals(p.getGender()) ? "Muški" : "Ženski";
            String passport = (p.getPassportNumber() != null && !p.getPassportNumber().isBlank())
                    ? EmailHtmlBuilder.esc(p.getPassportNumber()) : "—";
            String visaInfo = (p.getVisaInfo() != null && !p.getVisaInfo().isBlank())
                    ? EmailHtmlBuilder.esc(p.getVisaInfo()) : "—";

            cards.append("""
                <table width="100%%" cellpadding="0" cellspacing="0"
                       style="margin-bottom:8px;border:1px solid #ebe1cf;border-radius:6px;overflow:hidden;">
                  <tr>
                    <td colspan="2" width="100%%"
                        style="padding:8px 14px;background:#f5efe2;font-size:10px;font-weight:700;
                               letter-spacing:1.5px;text-transform:uppercase;color:#a89888;
                               border-bottom:1px solid #ebe1cf;">
                      Putnik %d
                    </td>
                  </tr>
                  <tr>
                    <td width="38%%" style="width:38%%;padding:9px 14px;font-size:12px;color:#a89888;font-weight:600;border-bottom:1px solid #ebe1cf;">Ime</td>
                    <td width="62%%" style="width:62%%;padding:9px 14px;font-size:13px;font-weight:600;color:#1a1410;border-bottom:1px solid #ebe1cf;">%s</td>
                  </tr>
                  <tr>
                    <td width="38%%" style="width:38%%;padding:9px 14px;font-size:12px;color:#a89888;font-weight:600;border-bottom:1px solid #ebe1cf;">Pol</td>
                    <td width="62%%" style="width:62%%;padding:9px 14px;font-size:13px;color:#1a1410;border-bottom:1px solid #ebe1cf;">%s</td>
                  </tr>
                  <tr>
                    <td width="38%%" style="width:38%%;padding:9px 14px;font-size:12px;color:#a89888;font-weight:600;border-bottom:1px solid #ebe1cf;">Datum ro&#273;enja</td>
                    <td width="62%%" style="width:62%%;padding:9px 14px;font-size:13px;color:#1a1410;border-bottom:1px solid #ebe1cf;">%s</td>
                  </tr>
                  <tr>
                    <td width="38%%" style="width:38%%;padding:9px 14px;font-size:12px;color:#a89888;font-weight:600;border-bottom:1px solid #ebe1cf;">Pa&#353;o&#353;</td>
                    <td width="62%%" style="width:62%%;padding:9px 14px;font-size:13px;color:#1a1410;border-bottom:1px solid #ebe1cf;">%s</td>
                  </tr>
                  <tr>
                    <td width="38%%" style="width:38%%;padding:9px 14px;font-size:12px;color:#a89888;font-weight:600;">Vize</td>
                    <td width="62%%" style="width:62%%;padding:9px 14px;font-size:13px;color:#1a1410;">%s</td>
                  </tr>
                </table>
                """.formatted(i + 1, name, gender, dob, passport, visaInfo));
        }

        return """
            <div style="margin-bottom:28px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;
                          color:#a89888;margin-bottom:12px;padding-bottom:8px;border-bottom:2px solid #ebe1cf;">
                Putnici
              </div>
              %s
            </div>
            """.formatted(cards);
    }

    /**
     * Vraća HTML listu putnika (ime i prezime, svaki u novom redu) za "Putnici" red u
     * tabeli detalja putovanja. Ako putnici nisu uneti, padback je broj putnika.
     */
    private String passengerNamesList(Booking booking) {
        List<PassengerInfo> passengers = booking.getPassengers();
        if (passengers == null || passengers.isEmpty()) {
            int n = booking.getNumberOfTravelers();
            return n + (n == 1 ? " putnik" : " putnika");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passengers.size(); i++) {
            if (i > 0) sb.append("<br>");
            sb.append(EmailHtmlBuilder.esc(passengers.get(i).getName()));
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cenovnik
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildPriceTable(Booking booking, int n) {
        StringBuilder rows = new StringBuilder();

        rows.append(priceRow("Osnovna cena", EmailHtmlBuilder.eur(booking.getBasePricePerPerson()) + " / os", n, booking.getBasePricePerPerson() * n, false));
        if (booking.getAccommodationExtra() > 0)
            rows.append(priceRow(EmailHtmlBuilder.resolveAccomLabel(booking.getAccommodationType()) + " upgrade", EmailHtmlBuilder.eur(booking.getAccommodationExtra()) + " / os", n, booking.getAccommodationExtra() * n, false));
        if (Boolean.TRUE.equals(booking.getHasBreakfast())) {
            int nights       = booking.getSelectedDate() != null ? booking.getSelectedDate().getNumberOfNights() : 1;
            int bfstPP       = PriceCalculatorImpl.BREAKFAST_PP * nights;
            rows.append(priceRow("Doručak (" + nights + " noći)", bfstPP + " € / os", n, bfstPP * n, false));
        }
        if (Boolean.TRUE.equals(booking.getHasSeatsTogether()))
            rows.append(priceRow("Sedišta zajedno (12 € × 2 smera)", PriceCalculatorImpl.SEATS_PP + " € / os", n, PriceCalculatorImpl.SEATS_PP * n, false));
        if (Boolean.TRUE.equals(booking.getHasInsurance()))
            rows.append(priceRow("Putno osiguranje", PriceCalculatorImpl.INSURANCE_PP + " € / os", n, PriceCalculatorImpl.INSURANCE_PP * n, false));
        if (booking.getCabinSuitcaseCount() > 0)
            rows.append(priceRow("Kabinski kofer (50 € × 2 smera)", "100 € / os", booking.getCabinSuitcaseCount(), booking.getCabinSuitcaseCount() * 100, false));
        if (booking.getExclusionCostEur() > 0) {
            int paid = booking.getExclusionCount() - 1;
            rows.append(priceRow(exclusionLabel(paid), "—", null, booking.getExclusionCostEur(), true));
        }
        if (n == 1)
            rows.append(priceRow("Doplata za solo putnika", "—", null, PriceCalculatorImpl.SOLO_SURCHARGE, true));

        return """
            <div style="margin-bottom:28px;">
              <div style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;margin-bottom:12px;padding-bottom:8px;border-bottom:2px solid #ebe1cf;">Pregled cene</div>
              <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;border:1px solid #ebe1cf;border-radius:8px;overflow:hidden;">
                <thead>
                  <tr style="background:#f5efe2;">
                    <th width="48%%" style="width:48%%;padding:10px 16px;text-align:left;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#a89888;border-bottom:1px solid #ebe1cf;">Stavka</th>
                    <th width="19%%" style="width:19%%;padding:10px 16px;text-align:center;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#a89888;border-bottom:1px solid #ebe1cf;white-space:nowrap;">Po osobi</th>
                    <th width="14%%" style="width:14%%;padding:10px 16px;text-align:center;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#a89888;border-bottom:1px solid #ebe1cf;white-space:nowrap;">Kom</th>
                    <th width="19%%" style="width:19%%;padding:10px 16px;text-align:right;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#a89888;border-bottom:1px solid #ebe1cf;white-space:nowrap;">Ukupno</th>
                  </tr>
                </thead>
                <tbody>%s</tbody>
                <tfoot>
                  <tr style="background:#1a1410;">
                    <td colspan="3" style="padding:14px 16px;font-size:13px;font-weight:700;color:#fff;letter-spacing:0.5px;">SVE UKUPNO</td>
                    <td style="padding:14px 16px;text-align:right;font-size:18px;font-weight:900;color:#e29070;">%s</td>
                  </tr>
                </tfoot>
              </table>
            </div>
            """.formatted(rows, EmailHtmlBuilder.eur(booking.getTotalPriceAll()));
    }

    private String priceRow(String label, String perPerson, Integer count, int total, boolean flat) {
        String ppCell    = flat ? "<td width='19%%' style='width:19%%;padding:11px 16px;text-align:center;color:#d1d5db;border-bottom:1px solid #ebe1cf;white-space:nowrap;'>—</td>" :
                                  "<td width='19%%' style='width:19%%;padding:11px 16px;text-align:center;font-size:13px;color:#6b5d4f;border-bottom:1px solid #ebe1cf;white-space:nowrap;'>" + perPerson + "</td>";
        String countCell = flat ? "<td width='14%%' style='width:14%%;padding:11px 16px;text-align:center;color:#d1d5db;border-bottom:1px solid #ebe1cf;white-space:nowrap;'>—</td>" :
                                  "<td width='14%%' style='width:14%%;padding:11px 16px;text-align:center;font-size:13px;color:#6b5d4f;border-bottom:1px solid #ebe1cf;white-space:nowrap;'>" + count + "</td>";
        return """
            <tr>
              <td width="48%%" style="width:48%%;padding:11px 16px;font-size:14px;color:#1a1410;border-bottom:1px solid #ebe1cf;">%s</td>
              %s
              %s
              <td width="19%%" style="width:19%%;padding:11px 16px;text-align:right;font-size:14px;font-weight:700;color:#1a1410;border-bottom:1px solid #ebe1cf;white-space:nowrap;">%s</td>
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
            booking.getExcludedDestination4()
        );
        for (com.escapii.model.Destination d : excl) {
            if (d == null) continue;
            String name = d.getName();
            if (name == null || name.isBlank()) continue; // odbrani LAZY proxy bez sesije
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(name);
        }
        return sb.isEmpty() ? "Nema" : sb.toString();
    }

    /**
     * Gradi label za red isključivanja u cenovniku.
     * Pricing: 1. isključivanje besplatno, svako naredno +15€/po osobi (max 3 plaćena).
     */
    private String exclusionLabel(int paid) {
        return switch (paid) {
            case 1 -> "Isključivanje (1× 15€/os)";
            case 2 -> "Isključivanja (2× 15€/os)";
            default -> "Isključivanja (%d× 15€/os)".formatted(paid);
        };
    }
}
