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
            "Novi upit %s - %s %s".formatted(booking.getBookingRef(), booking.getFirstName(), booking.getLastName()),
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
            "Upit primljen - %s".formatted(booking.getBookingRef()),
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
            "Rezervacija potvrđena - %s".formatted(booking.getBookingRef()),
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
            "Rezervacija otkazana - %s".formatted(booking.getBookingRef()),
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
     * Koristi RuntimeException kao nosač poruke - stack trace nije relevantan za email greške.
     */
    private void recordEmailError(String context) {
        try {
            appErrorService.record(context, 0,
                new RuntimeException("Email nije poslat - proveriti SMTP konfiguraciju i log"));
        } catch (Exception ex) {
            log.error("[Email] Nije moguće snimiti email grešku u AppErrorService: {}", ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tim - interni email
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
                tRow("Presedanje OK", Boolean.TRUE.equals(booking.getHasConnectingFlights()) ? "✔ Da" : "✘ Ne - samo direktni letovi")
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
            "Interni email - escapii ops tim · Nije za prosleđivanje",
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
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
              <tr>
                <td width="100%%" style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;padding-bottom:8px;border-bottom:2px solid #ebe1cf;">%s</td>
              </tr>
              <tr>
                <td width="100%%" style="padding-top:12px;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;">%s</table>
                </td>
              </tr>
            </table>
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
    // Korisnički - upit primljen
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildCustomerReceivedHtml(Booking booking) {
        String depDate = booking.getSelectedDate().getDepartureDate().format(EmailHtmlBuilder.DATE_FMT);
        String retDate = booking.getSelectedDate().getReturnDate().format(EmailHtmlBuilder.DATE_FMT);
        int n = booking.getNumberOfTravelers();

        boolean revealBox = Boolean.TRUE.equals(booking.getHasRevealBox());
        String revealTitle = revealBox ? "Iznenađenje na tvojoj adresi" : "Koverta s destinacijom";
        String revealDesc  = revealBox
            ? "Reveal Box stiže na tvoju adresu i otkriva gde putuješ. 📦"
            : "Koverta otkriva gde putujete. ✉";

        return loadEmailTemplate("upit-primljen.html")
            .replace("{{FIRST_NAME}}",        EmailHtmlBuilder.esc(booking.getFirstName()))
            .replace("{{REF_CODE}}",           EmailHtmlBuilder.esc(booking.getBookingRef()))
            .replace("{{BOARDING_PASS_HTML}}", buildBoardingPassBlock(booking, depDate, retDate, n))
            .replace("{{REVEAL_STEP_TITLE}}",  revealTitle)
            .replace("{{REVEAL_STEP_DESC}}",   revealDesc)
            .replace("{{PASSENGERS_HTML}}",    buildPassengersSection(booking))
            .replace("{{TOTAL_BOX_HTML}}",     EmailHtmlBuilder.totalBox(booking.getTotalPriceAll(), n))
            .replace("{{PRICE_TABLE_HTML}}",   buildPriceTable(booking, n))
            .replace("{{SENDER_EMAIL}}",       EmailHtmlBuilder.esc(sender.getFrom()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Korisnički - CONFIRMED / CANCELLED
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildCustomerStatusHtml(Booking booking, boolean confirmed) {
        String depDate = booking.getSelectedDate().getDepartureDate().format(EmailHtmlBuilder.DATE_FMT);
        String retDate = booking.getSelectedDate().getReturnDate().format(EmailHtmlBuilder.DATE_FMT);
        int n = booking.getNumberOfTravelers();

        if (confirmed) {
            return loadEmailTemplate("potvrda-rezervacije.html")
                .replace("{{FIRST_NAME}}",      EmailHtmlBuilder.esc(booking.getFirstName()))
                .replace("{{REF_CODE}}",         EmailHtmlBuilder.esc(booking.getBookingRef()))
                .replace("{{TRIP_CARD_HTML}}",   customerTripCardStyled(booking, depDate, retDate, n, false))
                .replace("{{TOTAL_BOX_HTML}}",   EmailHtmlBuilder.totalBox(booking.getTotalPriceAll(), n))
                .replace("{{TIMELINE_HTML}}",    buildConfirmedTimeline(booking))
                .replace("{{PASSENGERS_HTML}}", buildPassengersSection(booking))
                .replace("{{PRICE_TABLE_HTML}}", buildPriceTable(booking, n))
                .replace("{{SENDER_EMAIL}}",     EmailHtmlBuilder.esc(sender.getFrom()));
        } else {
            return loadEmailTemplate("otkaz-rezervacije.html")
                .replace("{{FIRST_NAME}}",     EmailHtmlBuilder.esc(booking.getFirstName()))
                .replace("{{REF_CODE}}",        EmailHtmlBuilder.esc(booking.getBookingRef()))
                .replace("{{TRIP_CARD_HTML}}",  customerTripCardStyled(booking, depDate, retDate, n, true))
                .replace("{{CONTACT_EMAIL}}",   EmailHtmlBuilder.esc(sender.getFrom()))
                .replace("{{SENDER_EMAIL}}",    EmailHtmlBuilder.esc(sender.getFrom()));
        }
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
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:24px;">
              <tr>
                <td width="100%%" style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;padding-bottom:16px;">Šta vas čeka</td>
              </tr>
              <tr>
                <td width="100%%">
                  %s
                  %s
                  %s
                  %s
                </td>
              </tr>
            </table>
            """.formatted(
            EmailHtmlBuilder.timelineItem("✓", "#eef6f0", "#1d6042",
                "Rezervacija potvrđena",
                "Danas · " + today,
                "Sve je rezervisano - letovi, smeštaj, transfer. Možete se opustiti - doslovno."),
            EmailHtmlBuilder.timelineItem("🌤", "#fff5eb", "#a85e44",
                "Vremenska prognoza",
                weatherStr + " · 7 dana pre polaska",
                "Dobijate prognozu da znate šta da spakujete. Destinacija? I dalje tajna!"),
            EmailHtmlBuilder.timelineItem("✉", "#eaf0f3", "#2D5F6B",
                Boolean.TRUE.equals(booking.getHasRevealBox())
                    ? "Iznenađenje na tvojoj adresi" : "Koverta s destinacijom",
                revealStr + " · 48h pre polaska",
                Boolean.TRUE.equals(booking.getHasRevealBox())
                    ? "Reveal Box stiže na tvoju adresu i otkriva gde putujete! 📦"
                    : "Konačno - otkrivate gde idete!"),
            EmailHtmlBuilder.timelineItem("✈", "#f5efe2", "#ebe1cf",
                "Avantura počinje!",
                depStr + " · Dan polaska",
                "Dođite na aerodrom i dozvolite sebi da budete iznenađeni.")
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Boarding pass block - replaces customerTripCard in customer-received email
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generiše boarding-pass vizuelni blok za email korisniku.
     * Koristi table-based inline CSS za maksimalnu kompatibilnost s email klijentima.
     * Prikazuje logo, rutu, datume, broj putnika i stvarna imena putnika.
     */
    private String buildBoardingPassBlock(Booking booking, String depDate, String retDate, int n) {
        String airportCode = EmailHtmlBuilder.esc(booking.getDepartureAirport());
        String airportCity = EmailHtmlBuilder.resolveAirportName(booking.getDepartureAirport());

        // Passenger names - show actual passengers, fall back to booking holder
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
                </td>
              </tr>

              <!-- White body - pulled up with negative margin simulation via inner table -->
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
                                    font-family:'Courier New',monospace;">-</div>
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
                                    font-family:'Courier New',monospace;">-</div>
                      </td>
                      <td style="text-align:right;vertical-align:bottom;">
                        <!-- Decorative barcode - table-based (flexbox se ne renderuje u Gmail desktop) -->
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="right" style="border-collapse:collapse;">
                          <tr>
                            <td width="2" height="38" bgcolor="#1a1410" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="4" height="38" bgcolor="#1a1410" style="width:4px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="1" height="38" bgcolor="#1a1410" style="width:1px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="3" height="38" bgcolor="#1a1410" style="width:3px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="5" height="38" bgcolor="#1a1410" style="width:5px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="1" height="38" bgcolor="#1a1410" style="width:1px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="3" height="38" bgcolor="#1a1410" style="width:3px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="4" height="38" bgcolor="#1a1410" style="width:4px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" bgcolor="#1a1410" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="5" height="38" bgcolor="#1a1410" style="width:5px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="1" height="38" bgcolor="#1a1410" style="width:1px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="2" height="38" style="width:2px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                            <td width="3" height="38" bgcolor="#1a1410" style="width:3px;height:38px;font-size:0;line-height:0;">&nbsp;</td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </td>
              </tr>

            </table>
            <!-- ═══ END BOARDING PASS ════════════════════════════════════════════ -->
            """.formatted(
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
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 20px;">
              <tr>
                <td width="100%%" style="background:#faf6ee;border-top:1px solid #ebe1cf;border-right:1px solid #ebe1cf;border-bottom:1px solid #ebe1cf;border-left:3px solid %s;padding:16px 18px;">
                  <p style="margin:0 0 12px;font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;">%s</p>
                  <table width="100%%" cellpadding="0" cellspacing="0">%s</table>
                </td>
              </tr>
            </table>
            """.formatted(borderColor, cardTitle, rows);
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
            String dob      = p.getDateOfBirth() != null ? p.getDateOfBirth().format(EmailHtmlBuilder.DATE_FMT) : "-";
            String gender   = "M".equals(p.getGender()) ? "Muški" : "Ženski";
            String passport = (p.getPassportNumber() != null && !p.getPassportNumber().isBlank())
                    ? EmailHtmlBuilder.esc(p.getPassportNumber()) : "-";
            String visaInfo = (p.getVisaInfo() != null && !p.getVisaInfo().isBlank())
                    ? EmailHtmlBuilder.esc(p.getVisaInfo()) : "-";

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
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
              <tr>
                <td width="100%%" style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;padding-bottom:8px;border-bottom:2px solid #ebe1cf;">Putnici</td>
              </tr>
              <tr>
                <td width="100%%" style="padding-top:12px;">%s</td>
              </tr>
            </table>
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
            rows.append(priceRow(exclusionLabel(paid), "-", null, booking.getExclusionCostEur(), true));
        }
        if (n == 1)
            rows.append(priceRow("Doplata za solo putnika", "-", null, PriceCalculatorImpl.SOLO_SURCHARGE, true));
        if (Boolean.TRUE.equals(booking.getHasRevealBox()))
            rows.append(priceRow("📦 Reveal Box (iznenađenje na tvojoj adresi)", "-", null, PriceCalculatorImpl.REVEAL_BOX_FLAT, true));

        // ── Vaučer popust ──────────────────────────────────────────────
        boolean hasVoucher = booking.getAppliedVoucherCode() != null
                && booking.getVoucherDiscount() != null
                && booking.getVoucherDiscount() > 0;

        // Vaučer red - samo popust, bez "Cena pre popusta"
        String subtotalHtml = "";
        if (hasVoucher) {
            subtotalHtml = """
                <tr>
                  <td style="padding:10px 16px;font-size:13px;font-weight:700;color:#1d6042;">
                    🎟 Poklon vaučer <span style="font-family:'Courier New',monospace;font-size:12px;background:#eef6f0;padding:2px 8px;border-radius:4px;color:#1d6042;letter-spacing:0.05em;">%s</span>
                  </td>
                  <td style="padding:10px 16px;text-align:right;font-size:14px;font-weight:700;color:#1d6042;">− %s</td>
                </tr>
                <tr><td colspan="2" style="padding:0;height:1px;background:#ebe1cf;font-size:0;line-height:0;mso-line-height-rule:exactly;"></td></tr>
                """.formatted(
                    EmailHtmlBuilder.esc(booking.getAppliedVoucherCode()),
                    EmailHtmlBuilder.eur(booking.getVoucherDiscount()));
        }

        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
              <tr>
                <td width="100%%" style="font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;padding-bottom:8px;border-bottom:2px solid #ebe1cf;">Pregled cene</td>
              </tr>
              <tr>
                <td width="100%%" style="padding-top:12px;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;border:1px solid #ebe1cf;">
                    <tr bgcolor="#f5efe2" style="background:#f5efe2;">
                      <td width="78%%" style="width:78%%;padding:10px 16px;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#a89888;">Stavka</td>
                      <td width="22%%" style="width:22%%;padding:10px 16px;text-align:right;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#a89888;">Ukupno</td>
                    </tr>
                    <tr><td colspan="2" style="padding:0;height:1px;background:#ebe1cf;font-size:0;line-height:0;mso-line-height-rule:exactly;"></td></tr>
                    %s
                    %s
                    <tr bgcolor="#1a1410" style="background:#1a1410;">
                      <td style="padding:14px 16px;font-size:13px;font-weight:700;color:#fff;letter-spacing:0.5px;">SVE UKUPNO</td>
                      <td style="padding:14px 16px;text-align:right;font-size:18px;font-weight:900;color:#e29070;">%s</td>
                    </tr>
                  </table>
                </td>
              </tr>
            </table>
            """.formatted(rows, subtotalHtml, EmailHtmlBuilder.eur(booking.getTotalPriceAll()));
    }

    private String priceRow(String label, String perPerson, Integer count, int total, boolean flat) {
        return """
            <tr>
              <td width="78%%" style="width:78%%;padding:11px 16px;font-size:14px;color:#1a1410;">%s</td>
              <td width="22%%" style="width:22%%;padding:11px 16px;text-align:right;font-size:14px;font-weight:700;color:#1a1410;">%s</td>
            </tr>
            <tr><td colspan="2" style="padding:0;height:1px;background:#ebe1cf;font-size:0;line-height:0;mso-line-height-rule:exactly;"></td></tr>
            """.formatted(label, EmailHtmlBuilder.eur(total));
    }

    private String buildNotesBox(String notes) {
        if (notes == null || notes.isBlank()) return "";
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
              <tr>
                <td width="100%%" style="background:#fffbeb;border-top:1px solid #fde68a;border-right:1px solid #fde68a;border-bottom:1px solid #fde68a;border-left:3px solid #f59e0b;padding:14px 18px;">
                  <p style="margin:0 0 6px;font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#b45309;">Napomena korisnika</p>
                  <p style="margin:0;font-size:14px;color:#78350f;line-height:1.6;">%s</p>
                </td>
              </tr>
            </table>
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Template loader - učitava MJML-kompajlirani HTML iz classpath /email/
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Učitava pre-kompajlirani HTML email template iz src/main/resources/email/.
     * Placeholders oblika {{TOKEN}} se potom zamenjuju dinamičkim vrednostima.
     */
    private static String loadEmailTemplate(String filename) {
        try (var is = BookingEmailServiceImpl.class.getResourceAsStream("/email/" + filename)) {
            if (is == null) {
                throw new IllegalStateException("Email template nije pronađen: /email/" + filename);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Nije moguće učitati email template: " + filename, e);
        }
    }
}
