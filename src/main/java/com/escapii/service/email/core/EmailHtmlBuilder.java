package com.escapii.service.email.core;

import com.escapii.model.AccommodationType;

import java.time.format.DateTimeFormatter;

public final class EmailHtmlBuilder {

    public static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /** Postavljaju se iz EmailHtmlBuilderConfig @PostConstruct — ne menjati direktno. */
    public static volatile String LOGO_WHITE_URL = "";
    public static volatile String LOGO_BLACK_URL = "";

    private EmailHtmlBuilder() {}

    // ── Status badge ─────────────────────────────────────────────────────────────
    /**
     * Vraća inline-styled status badge HTML.
     * type = "orange" | "green" | "red" | "blue"
     */
    public static String statusBadge(String label, String type) {
        String style = switch (type) {
            case "green" -> "background:rgba(22,163,74,0.2);color:#4ade80;border:1px solid rgba(22,163,74,0.4);";
            case "red"   -> "background:rgba(220,38,38,0.2);color:#f87171;border:1px solid rgba(220,38,38,0.4);";
            case "blue"  -> "background:rgba(99,102,241,0.2);color:#a5b4fc;border:1px solid rgba(99,102,241,0.4);";
            default      -> "background:rgba(249,115,22,0.2);color:#fb923c;border:1px solid rgba(249,115,22,0.4);";
        };
        return "<span style=\"display:inline-block;padding:4px 12px;border-radius:100px;font-size:11px;"
             + "font-weight:700;letter-spacing:1.5px;text-transform:uppercase;" + style + "\">"
             + label + "</span>";
    }

    // ── Email shell ───────────────────────────────────────────────────────────────
    /**
     * Obmotava sadržaj u standardni Escapii email shell.
     *
     * @param accentBarColor tanka traka pri vrhu (#f97316, #16a34a, #dc2626 …)
     * @param headerBg       pozadina headera (#08112a, #064e3b, #450a0a, #1e1b4b)
     * @param badgeHtml      badge HTML iz statusBadge(); "" ako ne treba
     * @param headingText    glavni naslov (koristi se i kao preheader)
     * @param subheading     podnaslov ispod naslova; "" da se izostavi
     * @param refCode        booking ref; "" da se izostavi
     * @param bodyContent    HTML tela emaila (bela pozadina)
     * @param footerText     HTML futera
     * @param mysteryStrip   prikaži "destinacija je tajna" traku između headera i tela
     */
    public static String wrapBase(
        String accentBarColor,
        String headerBg,
        String badgeHtml,
        String headingText,
        String subheading,
        String refCode,
        String bodyContent,
        String footerText,
        boolean mysteryStrip
    ) {
        String subheadingHtml = subheading.isBlank() ? "" :
            "<p style=\"margin:8px 0 0;font-size:13px;color:rgba(255,255,255,0.55);line-height:1.5;\">%s</p>"
                .formatted(subheading);

        String refHtml = refCode.isBlank() ? "" :
            "<div style=\"display:inline-block;background:rgba(249,115,22,0.15);"
            + "border:1px solid rgba(249,115,22,0.3);color:#fb923c;font-size:11px;font-weight:700;"
            + "padding:3px 10px;border-radius:4px;letter-spacing:0.5px;margin-top:10px;\">"
            + "&#10022; %s</div>".formatted(refCode);

        String mysteryHtml = mysteryStrip ? """
            <table width="100%%" cellpadding="0" cellspacing="0" style="background:#08112a;">
              <tr><td style="padding:10px 36px;font-size:11px;color:rgba(255,255,255,0.5);text-align:center;letter-spacing:1px;">
                <span style="color:#f97316;">&#9679; &#9679; &#9679;</span>
                &nbsp;&nbsp;Vaša destinacija ostaje tajna sve do 72h pre polaska&nbsp;&nbsp;
                <span style="color:#f97316;">&#9679; &#9679; &#9679;</span>
              </td></tr>
            </table>
            """ : "";

        String footerBg = "#1e1b4b".equals(headerBg) ? "#f0f0f5" : "#f8f9fa";

        return """
            <!DOCTYPE html>
            <html lang="sr" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
            <head>
              <meta charset="UTF-8">
              <meta name="x-apple-disable-message-reformatting">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="format-detection" content="telephone=no, date=no, address=no, email=no">
              <meta name="color-scheme" content="light only">
              <meta name="supported-color-schemes" content="light">
              <!--[if mso]>
              <xml><o:OfficeDocumentSettings>
                <o:PixelsPerInch>96</o:PixelsPerInch>
              </o:OfficeDocumentSettings></xml>
              <style>
                td,th,div,p,a,h1,h2,h3 { font-family:"Segoe UI",Arial,sans-serif; mso-line-height-rule:exactly; }
                body { background:#f3f4f6 !important; }
              </style>
              <![endif]-->
              <style>
                @media (max-width:620px) {
                  .mob-full { width:100%% !important; }
                  .mob-pad  { padding:20px !important; }
                }
                @media (prefers-color-scheme: dark) {
                  .force-light-bg { background:#f3f4f6 !important; }
                  .force-white-bg { background:#ffffff !important; }
                  .force-footer-bg { background:%s !important; }
                }
              </style>
            </head>
            <body style="margin:0;padding:0;word-break:break-word;-webkit-font-smoothing:antialiased;background:#f3f4f6;" bgcolor="#f3f4f6">

              <!-- Preheader -->
              <div style="display:none;max-height:0;overflow:hidden;mso-hide:all;">
                %s &#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&zwnj;&nbsp;
              </div>

              <table width="100%%" cellpadding="0" cellspacing="0" bgcolor="#f3f4f6" style="background:#f3f4f6;" class="force-light-bg">
                <tr><td align="center" style="padding:40px 16px;">
                  <!--[if mso]><table width="640" cellpadding="0" cellspacing="0"><tr><td><![endif]-->
                  <table class="mob-full" style="width:640px;max-width:640px;margin:0 auto;" cellpadding="0" cellspacing="0">

                    <!-- CARD -->
                    <tr><td bgcolor="#ffffff" style="background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;" class="force-white-bg">

                      <!-- Accent bar -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td bgcolor="%s" style="background:%s;height:4px;font-size:0;line-height:0;">&nbsp;</td></tr>
                      </table>

                      <!-- Header -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td bgcolor="%s" style="background:%s;padding:28px 40px 26px;" class="mob-pad">

                          <!-- Logo centered -->
                          <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:16px;">
                            <tr>
                              <td align="center" style="text-align:center;vertical-align:middle;">
                                <!--[if mso]>
                                <p style="margin:0;font-family:'Segoe UI',Arial,sans-serif;font-size:22px;font-weight:700;color:#ffffff;letter-spacing:2px;text-align:center;">ESCAPII</p>
                                <![endif]-->
                                <!--[if !mso]><!-->
                                <img src="%s" alt="ESCAPII" width="130" height="38"
                                     style="display:block;border:0;height:38px;width:130px;max-width:130px;margin:0 auto;">
                                <!--<![endif]-->
                              </td>
                            </tr>
                          </table>

                          <!-- Heading -->
                          <h1 style="font-family:Georgia,'Times New Roman',serif;font-size:28px;color:#ffffff;line-height:1.3;margin:0;font-weight:normal;mso-line-height-rule:exactly;">%s</h1>
                          %s
                          %s
                        </td></tr>
                      </table>

                      <!-- Mystery strip -->
                      %s

                      <!-- Body -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td bgcolor="#ffffff" style="padding:28px 40px;background:#ffffff;" class="mob-pad force-white-bg">
                          %s
                        </td></tr>
                      </table>

                      <!-- Footer -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td bgcolor="%s" style="background:%s;border-top:1px solid #e5e7eb;padding:16px 40px;text-align:center;font-size:11px;color:#9ca3af;line-height:1.8;" class="force-footer-bg">
                          %s
                        </td></tr>
                      </table>

                    </td></tr>
                  </table>
                  <!--[if mso]></td></tr></table><![endif]-->
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(
            footerBg,           // dark-mode CSS override for footer class
            headingText,        // preheader
            accentBarColor,     // accent bar bgcolor attr
            accentBarColor,     // accent bar style attr
            headerBg,           // header bgcolor attr
            headerBg,           // header style attr
            LOGO_WHITE_URL,     // logo
            headingText,        // h1
            subheadingHtml,     // subtitle
            refHtml,            // ref chip
            mysteryHtml,        // mystery strip
            bodyContent,        // body
            footerBg,           // footer bgcolor attr
            footerBg,           // footer style attr
            footerText          // footer text
        );
    }

    // ── Footer ────────────────────────────────────────────────────────────────────
    public static String customerFooter(String email) {
        return """
            <strong style="color:#08112a;">escapii</strong> — putovanja iznenađenja<br>
            Beograd, Srbija · <a href="mailto:%s" style="color:#6b7280;text-decoration:underline;">%s</a><br><br>
            <a href="https://escapii.rs" style="color:#6b7280;text-decoration:underline;">escapii.rs</a>
            """.formatted(email, email);
    }

    // ── Detail row helpers ────────────────────────────────────────────────────────
    public static String dRow(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #f3f4f6;">
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;width:55%%;">%s</td>
              <td style="padding:7px 0;font-size:13px;color:#1f2937;font-weight:500;text-align:right;white-space:nowrap;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String dRowStrike(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #f3f4f6;">
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;width:55%%;">%s</td>
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;font-weight:500;text-align:right;white-space:nowrap;text-decoration:line-through;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String dRowMystery(String label, String value) {
        return """
            <tr>
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;width:45%%;">%s</td>
              <td style="padding:7px 0;font-size:13px;color:#f97316;font-weight:600;font-style:italic;text-align:right;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    // ── Price summary box ─────────────────────────────────────────────────────────
    public static String totalBox(int total, int n) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:24px;background:#08112a;border-radius:8px;">
              <tr>
                <td style="padding:20px 24px;">
                  <div style="font-size:10px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:rgba(255,255,255,0.45);margin-bottom:6px;">Ukupna cena putovanja</div>
                  <div style="font-family:Georgia,'Times New Roman',serif;font-size:34px;font-weight:700;color:#f97316;line-height:1;margin-bottom:4px;">%s €</div>
                  <div style="font-size:12px;color:rgba(255,255,255,0.4);">za %d putnika</div>
                </td>
              </tr>
            </table>
            """.formatted(total, n);
    }

    // ── Step block (email: upit primljen) ─────────────────────────────────────────
    public static String step(String num, String title, String description) {
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

    // ── Timeline item (email: rezervacija potvrđena) ──────────────────────────────
    public static String timelineItem(String icon, String iconBg, String accentColor,
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

    // ── Utilities ─────────────────────────────────────────────────────────────────
    public static String esc(String input) {
        if (input == null) return "";
        return input.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    public static String eur(int amount) { return amount + " €"; }

    public static String resolveAirportName(String iata) {
        return switch (iata == null ? "" : iata.toUpperCase()) {
            case "BEG" -> "Beograd";
            case "INI" -> "Niš";
            case "ZAG" -> "Zagreb";
            case "BUD" -> "Budimpešta";
            case "TIM" -> "Temišvar";
            default    -> iata;
        };
    }

    public static String resolveAccomLabel(AccommodationType type) {
        if (type == null) return "Standard (3★)";
        return switch (type) {
            case SUPERIOR -> "Superior (4★)";
            case PREMIUM  -> "Premium (5★)";
            default       -> "Standard (3★)";
        };
    }
}
