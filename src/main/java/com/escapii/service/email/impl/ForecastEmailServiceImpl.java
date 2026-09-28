package com.escapii.service.email.impl;

import com.escapii.model.Booking;
import com.escapii.service.email.ForecastEmailService;
import com.escapii.util.LogUtils;
import com.escapii.util.Padez;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.weather.DailyForecast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    /** Javna kontakt adresa koju kupac vidi (nije adresa na koju tim prima). */
    @Value("${app.contact-email}")
    private String contactEmail;

    private final EmailSender sender;

    @Override
    public void sendForecastEmail(Booking booking, List<DailyForecast> forecast) {
        LocalDate depDate = booking.getSelectedDate().getDepartureDate();
        LocalDate retDate = booking.getSelectedDate().getReturnDate();
        String depDateStr = depDate.format(EmailHtmlBuilder.DATE_FMT);

        // Krupna kartica pokazuje dan polaska - za taj dan se pakuje. "Trenutno vreme" sedam
        // dana ranije zbunjuje čim se razlikuje od dana puta. Kad prognoza ne doseže polazak
        // (na T-7 ne bi trebalo), pada na prvi dan koji ima.
        DailyForecast hero = forecast.stream()
                .filter(d -> d.date().equals(depDate)).findFirst().orElse(forecast.get(0));
        // Bez emodžija u naslovu: marketinški obrazac koji Outlook/Hotmail kažnjava.
        String subject = "Tvoja prognoza za putovanje - " + depDateStr + " | Escapii";
        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), depDate);
        String html = buildHtml(depDate, retDate, depDateStr, daysUntil, forecast, hero);

        // travellerEmail(), ne getEmail(): kod poklona prognoza ide obdarenom.
        // Poklanjaocu se ne salje - prognoza namerno ne imenuje grad, pa mu ne
        // govori nista sto mu kopija reveala vec ne kaze.
        boolean sent = sender.send(booking.travellerEmail(), subject, html);
        if (!sent) {
            throw new RuntimeException("[Forecast] Email slanje nije uspelo za " + booking.getBookingRef());
        }
        log.info("[Forecast] ✅ Email poslan za {} ({})", booking.getBookingRef(), LogUtils.maskEmail(booking.travellerEmail()));
    }

    // ── HTML template ─────────────────────────────────────────────────────────

    private String buildHtml(LocalDate depDate, LocalDate retDate,
                             String depDateStr, long daysUntil,
                             List<DailyForecast> forecast, DailyForecast hero) {

        String dayCards       = buildDayCards(forecast, depDate, retDate);
        String packingCard    = buildPackingCard(forecast, depDate, retDate);
        String travelDaysCard = buildTravelDaysCard(forecast, depDate, retDate);
        String[] kad          = kadJePolazak(daysUntil);

        String body = """
            <!-- Hero weather card - light -->
            <table width="100%%" cellpadding="0" cellspacing="0" style="border-radius:16px;overflow:hidden;margin-bottom:16px;background:#faf6ee;border:1px solid #ebe1cf;">
              <tr><td style="padding:32px 28px 28px;">

                <!-- Pozdrav -->
                <p style="margin:0 0 24px;font-size:13px;color:#6b5d4f;letter-spacing:0.5px;">
                  Tvoje putovanje je %s<strong style="color:#2D5F6B;">%s</strong> - evo šta te čeka.
                </p>

                <!-- Glavna temperatura -->
                <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:24px;">
                  <tr>
                    <td width="65%%" style="width:65%%;vertical-align:middle;">
                      <div style="font-size:10px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#a89888;margin-bottom:8px;">%s</div>
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

                <!-- Forecast strip (travel days only) - inline-block kartice se
                     prelamaju na uzak ekran; fiksne <td> kolone bi se prelile
                     kod dužih putovanja (8 dana × 60px > 320px). font-size:0
                     na kontejneru uklanja razmake između inline-block elemenata. -->
                <div style="text-align:center;font-size:0;">%s</div>

              </td></tr>
            </table>

            <!-- Šta da spakuješ - iz dana puta, ne iz današnjeg vremena -->
            %s

            <!-- Travel days breakdown -->
            %s

            <!-- Reveal note -->
            <table width="100%%" cellpadding="0" cellspacing="0"
              style="background:#faf6ee;border:1px solid #ebe1cf;border-left:3px solid #a85e44;border-radius:8px;margin-bottom:12px;">
              <tr><td style="padding:14px 18px;">
                <div style="font-size:12px;font-weight:700;color:#a85e44;margin-bottom:4px;">📬 Preporuka</div>
                <div style="font-size:12px;color:#1a1410;line-height:1.6;">
                  Kada dobiješ mejl sa otkrićem destinacije,
                  <strong>preporučujemo da ponovo proveriš prognozu</strong> direktno za tu destinaciju -
                  %s
                </div>
              </td></tr>
            </table>

            <!-- Footer note -->
            <p style="font-size:11px;color:#a89888;text-align:center;margin:8px 0 0;line-height:1.6;">
              Srećan put! 🌍
            </p>
            """.formatted(
                kad[0], kad[1],
                hero.date().equals(depDate) ? "Na dan polaska" : "Trenutno vreme",
                hero.emoji(), hero.maxTemp(), hero.description(),
                depDateStr,
                hero.maxTemp(), hero.minTemp(),
                dayCards,
                packingCard,
                travelDaysCard,
                // dan-dva pre polaska „za toliko dana unapred" ne stoji
                daysUntil > 2 ? "prognoza za toliko dana unapred može biti okvirna."
                              : "prognoza se do polaska još može promeniti."
        );

        return EmailHtmlBuilder.wrapBase(
            "#a85e44",
            "#0a1628",
            EmailHtmlBuilder.statusBadge("Prognoza", "orange"),
            "Tvoja vremenska <span style=\"border-bottom:4px solid #F1AB86;\">prognoza</span>",
            "Putovanje · " + depDateStr,
            "",
            body,
            EmailHtmlBuilder.customerFooter(contactEmail),
            false,
            // pregled u inbox listi: ono što kupca zanima, ne ponovljen naslov
            "Na dan polaska " + hero.description().toLowerCase(Locale.ROOT) + ", " + hero.maxTemp() + "° - i šta da spakuješ"
        );
    }

    // ── Travel days forecast strip ───────────────────────────────────────────

    private String buildDayCards(List<DailyForecast> forecast, LocalDate depDate, LocalDate retDate) {
        StringBuilder sb = new StringBuilder();
        Locale sr = Locale.forLanguageTag("sr-Latn-RS");   // latinica - "sr" bez pisma daje ćirilicu (субота)
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

            String dayDate = d.date().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM."));

            String labelStyle = (isDep || isRet)
                ? "font-size:8px;font-weight:700;letter-spacing:0.3px;text-transform:uppercase;margin-bottom:2px;"
                  + (isDep ? "color:#a85e44;" : "color:#1d6042;")
                : "font-size:9px;color:#a89888;margin-bottom:2px;text-transform:uppercase;letter-spacing:0.5px;";

            String tempStyle = (isDep || isRet)
                ? "font-size:13px;font-weight:800;"
                : "font-size:12px;font-weight:700;";

            sb.append("""
                <div style="display:inline-block;width:64px;vertical-align:top;padding:2px;font-size:12px;">
                  <div style="%sborder-radius:12px;padding:10px 4px;">
                    <div style="%s">%s</div>
                    <div style="font-size:8px;color:#a89888;margin-bottom:5px;">%s</div>
                    <div style="font-size:20px;margin-bottom:5px;">%s</div>
                    <div style="%scolor:#9b3a2a;">%d°</div>
                    <div style="font-size:10px;color:#1f4a57;margin-top:2px;">%d°</div>
                    %s
                  </div>
                </div>""".formatted(
                    bg,
                    labelStyle, label,
                    dayDate,
                    d.emoji(),
                    tempStyle,
                    d.maxTemp(),
                    d.minTemp(),
                    d.imaPadavina()
                        ? "<div style=\"font-size:9px;color:#2D5F6B;margin-top:3px;\">" + padavine(d) + "</div>"
                        : ""
            ));
        }
        return sb.toString();
    }

    // ── Šta da spakuješ ───────────────────────────────────────────────────────

    private static String buildPackingCard(List<DailyForecast> forecast, LocalDate depDate, LocalDate retDate) {
        String hint = packingHint(forecast, depDate, retDate);
        if (hint.isEmpty()) return "";
        return """
            <table width="100%%" cellpadding="0" cellspacing="0"
              style="background:#fff;border:1px solid #ebe1cf;border-radius:12px;margin-bottom:16px;">
              <tr><td style="padding:20px 24px;">
                <div style="font-size:12px;font-weight:700;color:#a89888;letter-spacing:1px;text-transform:uppercase;margin-bottom:10px;">
                  🧳 Šta da spakuješ
                </div>
                <div style="font-size:14px;color:#1a1410;line-height:1.65;">%s</div>
              </td></tr>
            </table>""".formatted(hint);
    }

    /** Pojas po dnevnom maksimumu - od njega zavisi šta se pakuje. Redosled: od najtoplijeg ka najhladnijem. */
    enum Pojas {
        VRELO, TOPLO, PRIJATNO, SVEZE, HLADNO, ZIMA;

        static Pojas za(int max) {
            if (max >= 30) return VRELO;
            if (max >= 25) return TOPLO;
            if (max >= 19) return PRIJATNO;
            if (max >= 13) return SVEZE;
            if (max >= 6)  return HLADNO;
            return ZIMA;
        }
    }

    /**
     * Razlika između najtoplijeg i najhladnijeg dana puta od koje savet više ne ide po proseku.
     * Prosek ume da slaže: 30, 30 i 5 daju „prijatno, oko 22", a nijedan dan nije takav - tada
     * se navode oba kraja i šta se pakuje za svaki.
     */
    static final int RASPON_ZA_DVA_SAVETA = 8;

    /**
     * Savet o garderobi iz temperatura i padavina za DANE PUTA, ne za danas. Kad su dani slični,
     * pojas bira prosek dnevnih maksimuma; kad se mnogo razlikuju (8 i više stepeni, ili dva
     * pojasa razlike), navode se najtopliji i najhladniji dan i šta se pakuje za koji. Hladna
     * noć, sneg i kiša dodaju po rečenicu.
     *
     * <p>Pravila koja čuva ForecastPackingLogicTest na stotinama hiljada kombinacija: savet važi
     * za SVAKI dan puta (dan do 24 stepena donosi bar duks, do 12 jaknu, do 5 toplu jaknu, a dan
     * od 25 laganu garderobu), sneg se pominje samo uz hladan dan, padavine na mrazu nisu kiša,
     * i dve spojene rečenice ne kažu ni isto ni suprotno. Prazno kad prognoza ne pokriva nijedan
     * dan puta.
     */
    static String packingHint(List<DailyForecast> forecast, LocalDate depDate, LocalDate retDate) {
        List<DailyForecast> dani = forecast.stream()
                .filter(d -> !d.date().isBefore(depDate) && !d.date().isAfter(retDate))
                .toList();
        if (dani.isEmpty()) return "";

        int prosek      = (int) Math.round(dani.stream().mapToInt(DailyForecast::maxTemp).average().orElse(0));
        int najtopliji  = dani.stream().mapToInt(DailyForecast::maxTemp).max().orElse(prosek);
        int najhladniji = dani.stream().mapToInt(DailyForecast::maxTemp).min().orElse(prosek);
        int noc         = dani.stream().mapToInt(DailyForecast::minTemp).min().orElse(prosek);
        Pojas topli  = Pojas.za(najtopliji);
        Pojas hladni = Pojas.za(najhladniji);
        boolean velikRaspon = najtopliji - najhladniji >= RASPON_ZA_DVA_SAVETA;
        boolean dvaPojasaRazlike = hladni.ordinal() - topli.ordinal() >= 2;

        StringBuilder s = new StringBuilder();
        if (topli != hladni && (velikRaspon || dvaPojasaRazlike)) {
            s.append(savetZaRaspon(najtopliji, najhladniji, topli, hladni)).append(nocUzRaspon(hladni, noc));
        } else {
            s.append(savetZaPojas(Pojas.za(prosek), prosek, najtopliji, najhladniji, noc));
        }

        if (dani.stream().anyMatch(DailyForecast::snegZaPakovanje)) {
            s.append(" Ima i snega u najavi - obuj nešto što ne klizi i spakuj tople čarape.");
        }
        // Dan sa snegom za pakovanje se ne broji i ovde. Kod snega uz dan koji nije dovoljno hladan
        // ulazi samo ako ima bar 1 mm, i tada rečenica kaže „padavine", ne „kiša".
        List<DailyForecast> mokri = dani.stream()
                .filter(d -> !d.snegZaPakovanje() && (d.snowy() ? d.imaPadavina() : d.rainy()))
                .toList();
        boolean ceoPutPokriven = dani.size() == ChronoUnit.DAYS.between(depDate, retDate) + 1;
        s.append(recenicaOPadavinama(mokri.size(), dani.size(), ceoPutPokriven,
                mokri.stream().noneMatch(DailyForecast::snowy)));
        return s.toString();
    }

    /** Dani su slični: jedna rečenica po pojasu, a hladna noć je menja ili joj dodaje nastavak. */
    private static String savetZaPojas(Pojas pojas, int prosek, int najtopliji, int najhladniji, int noc) {
        // Isti pojas, a dani daleko jedan od drugog (40 i 32, 5 i -11): prosek ne liči ni na jedan.
        String oko = najtopliji - najhladniji >= RASPON_ZA_DVA_SAVETA
                ? "od " + najhladniji + " do " + stepeni(najtopliji)
                : "oko " + stepeni(prosek);
        String nocu = "Noću pada na oko " + stepeni(noc);
        return switch (pojas) {
            case VRELO -> "Preko dana je vrelo, " + oko
                    + " - lagana garderoba, naočare za sunce i krema su obavezne, a flašica vode uvek pri ruci."
                    + (noc <= 10 ? " " + nocu + ", pa ponesi i nešto toplije za veče." : "");
            // Uz hladnu noć rečenica pojasa je kraća: ne sme da kaže „sasvim dovoljna" pa odmah
            // „ponesi nešto toplije", niti dvaput „za veče". Dan ispod 25 traži bar duks.
            case TOPLO -> "Toplo je, " + oko + " preko dana - " + (
                    noc <= 10         ? "za dan je dovoljna lagana letnja garderoba. " + nocu + ", pa za veče ponesi duks ili tanku jaknu."
                  : najhladniji < 25  ? "lagana letnja garderoba, a za svežiji dan i veče dobro dođe duks ili tanka jakna."
                  :                     "lagana letnja garderoba je sasvim dovoljna, uz jednu majicu dugih rukava za veče.");
            case PRIJATNO -> "Prijatno je, " + oko + " preko dana - " + (noc <= 10
                    ? "majice i lagane pantalone. " + nocu + ", pa ti za jutro i veče treba jakna."
                    : "majice i lagane pantalone, a za jutro i veče dobro dođe tanka jakna ili duks.");
            case SVEZE -> "Sveže je, " + oko
                    + " preko dana - jakna ti treba, a najsigurnije je da se oblačiš u slojevima."
                    + (noc <= 3 ? " " + nocu + ", pa za veče ponesi i nešto toplije." : "");
            case HLADNO -> "Hladno je, " + oko + " preko dana - topla jakna, šal i zatvorena obuća."
                    + (noc < 0 ? " Noću je ispod nule, pa ponesi i kapu i rukavice." : "");
            case ZIMA -> "Zimski uslovi, " + oko + " preko dana"
                    + (noc < 0 && najhladniji > 0 ? ", a noću ispod nule" : "")   // kad je i neki dan u minusu, suvišno je
                    + " - topla jakna, kapa, rukavice i obuća koja ne propušta.";
        };
    }

    /** Dani se mnogo razlikuju: oba kraja brojem, pa šta se pakuje za tople, a šta za hladne dane. */
    private static String savetZaRaspon(int najtopliji, int najhladniji, Pojas topli, Pojas hladni) {
        String uvod = "Dani se dosta razlikuju: najtopliji ima oko " + najtopliji
                + ", a najhladniji oko " + stepeni(najhladniji) + " preko dana. ";
        if (topli == Pojas.VRELO && hladni == Pojas.TOPLO) {
            return uvod + "Ponesi laganu letnju garderobu za sve dane, uz naočare za sunce i kremu.";
        }
        if (topli == Pojas.HLADNO && hladni == Pojas.ZIMA) {
            return uvod + "Ponesi toplu jaknu, šal i zatvorenu obuću, a za najhladnije dane i kapu i rukavice.";
        }
        String zaTople = switch (topli) {
            case VRELO    -> "laganu garderobu, naočare za sunce i kremu za vrele dane";
            case TOPLO    -> "laganu letnju garderobu za tople dane";
            case PRIJATNO -> "majice i lagane pantalone za toplije dane";
            default       -> "duks ili tanju jaknu za toplije dane";        // SVEZE; hladniji od toga ne može biti „topli" kraj
        };
        String zaHladne = switch (hladni) {
            case PRIJATNO -> "majice i lagane pantalone za prijatnije";     // jakna za jutro i veče stiže u nocUzRaspon
            case SVEZE    -> "jaknu ili duks za svežije";
            case HLADNO   -> "toplu jaknu i zatvorenu obuću za hladne";
            default       -> "toplu jaknu, kapu i rukavice za najhladnije";  // ZIMA
        };
        return uvod + "Ponesi " + zaTople + ", a " + zaHladne + ".";
    }

    /**
     * Nastavak saveta za raspon, po najhladnijem danu. Kad je on „prijatan" (19-24), jakna za jutro
     * i veče ide uvek - bez nje bi jedan topao dan više izbacio jaknu iz saveta za ostale dane.
     */
    private static String nocUzRaspon(Pojas hladni, int noc) {
        String nocu = " Noću pada na oko " + stepeni(noc);
        return switch (hladni) {
            // bez „ponesi": savet za raspon već počinje tom rečju
            case TOPLO    -> noc <= 10 ? nocu + ", pa za veče dobro dođe i nešto toplije." : "";
            case PRIJATNO -> noc <= 10 ? nocu + ", pa ti za jutro i veče treba jakna."
                                       : " Za jutro i veče dobro dođe tanka jakna ili duks.";
            case SVEZE    -> noc <= 3  ? nocu + ", pa za veče dobro dođe i nešto toplije." : "";
            case HLADNO   -> noc < 0   ? " Noću je ispod nule, pa ne zaboravi kapu i rukavice." : "";
            default       -> "";
        };
    }

    /**
     * Rečenica o kiši. „Padavine" umesto „kiša" kad je među danima i dan sa kodom snega koji nije
     * dovoljno hladan za savet o snegu. Kad prognoza pokriva samo deo puta, bez „svakog dana" i
     * bez brojanja - za ostale dane se još ne zna.
     */
    private static String recenicaOPadavinama(int dana, int pokrivenoDana, boolean ceoPutPokriven, boolean samoKisa) {
        if (dana == 0) return "";
        if (!ceoPutPokriven) {
            return samoKisa ? " U prognozi je i kiša, pa ubaci i kišobran."
                            : " U prognozi su i padavine, pa ubaci i kišobran.";
        }
        if (dana == 1) {
            return samoKisa ? " Jedan dan je najavljena kiša, pa ubaci i kišobran."
                            : " Jedan dan su najavljene padavine, pa ubaci i kišobran.";
        }
        String koliko = dana < pokrivenoDana ? dana + " od " + pokrivenoDana + " dana"
                      : pokrivenoDana == 2   ? "oba dana"
                      :                        "svakog dana";
        return (samoKisa ? " Kiša se očekuje " : " Padavine se očekuju ") + koliko + ", pa ponesi kišobran ili kabanicu.";
    }

    /**
     * „za 7 dana", a za polazak sutra ili danas „sutra" i „danas" - „za 0 dana" zvuči kao automat.
     * Vraća {predlog, istaknuti deo}.
     */
    static String[] kadJePolazak(long daysUntil) {
        if (daysUntil <= 0) return new String[]{"", "danas"};
        if (daysUntil == 1) return new String[]{"", "sutra"};
        return new String[]{"za ", daysUntil + " " + Padez.dan(daysUntil)};
    }

    /** „poslednji dan", „poslednja 3 dana", „poslednjih 5 dana", „poslednji 21 dan". */
    static String poslednjiDani(long n) {
        if (n == 1) return "poslednji dan";
        long a = Math.abs(n) % 100, c = a % 10;
        if (c == 1 && a != 11) return "poslednji " + n + " dan";
        if (c >= 2 && c <= 4 && (a < 12 || a > 14)) return "poslednja " + n + " dana";
        return "poslednjih " + n + " dana";
    }

    /** „💧 4 mm" - razmak između broja i jedinice; nbsp da se u uskoj kartici ne prelomi. */
    private static String padavine(DailyForecast d) {
        return "💧&nbsp;" + String.format("%.0f", d.precipitation()) + "&nbsp;mm";
    }

    /** "21 stepen", "22 stepena", "25 stepeni" - po poslednjoj cifri, kao i za minus. */
    static String stepeni(int n) {
        int a = Math.abs(n) % 100, c = a % 10;
        String rec = (a >= 11 && a <= 14) ? "stepeni" : c == 1 ? "stepen" : (c >= 2 && c <= 4) ? "stepena" : "stepeni";
        return n + " " + rec;
    }

    // ── Travel days breakdown ─────────────────────────────────────────────────

    private String buildTravelDaysCard(List<DailyForecast> forecast, LocalDate depDate, LocalDate retDate) {
        Locale sr = Locale.forLanguageTag("sr-Latn-RS");   // latinica - "sr" bez pisma daje ćirilicu (субота)
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
                String precipitation = d.imaPadavina()
                        ? "<span style=\"font-size:11px;color:#6b5d4f;\"> · " + padavine(d) + "</span>"
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
                        <div style="font-size:11px;color:#ebe1cf;margin-top:2px;">Prognoza još nije dostupna</div>
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
            // Mejl ide jednom, pa se ne obećava da će prognoza „biti dostupna": kupac je proverava
            // sam kad sazna destinaciju - isto što kaže i Preporuka ispod.
            caveat = """
                <div style="margin-top:12px;font-size:11px;color:#a89888;line-height:1.5;">
                  ℹ️ Za %s putovanja prognoza još nije dostupna - proveri je kad saznaš destinaciju.
                </div>""".formatted(poslednjiDani(missing));
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
