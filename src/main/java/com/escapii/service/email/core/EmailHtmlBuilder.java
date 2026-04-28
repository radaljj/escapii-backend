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

    public static String wrapBase(
        String headerBg,
        String headingText,
        String subheading,
        String refCode,
        String bodyContent,
        String footerText,
        boolean mysteryStrip
    ) {
        String subheadingHtml = subheading.isBlank() ? "" :
            "<p style=\"margin:8px 0 0;font-size:13px;color:rgba(255,255,255,0.55);line-height:1.5;\">%s</p>".formatted(subheading);

        String refHtml = refCode.isBlank() ? "" :
            "<div style=\"display:inline-block;background:rgba(202,138,113,0.18);border:1px solid rgba(202,138,113,0.35);color:#F5C9A8;font-size:11px;font-weight:700;padding:3px 10px;border-radius:4px;letter-spacing:0.5px;margin-top:10px;\">&#10022; %s</div>".formatted(refCode);

        String mysteryHtml = mysteryStrip ? """
            <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0D2E38;">
              <tr><td style="padding:10px 36px;font-size:11px;color:rgba(255,255,255,0.5);text-align:center;letter-spacing:1px;">
                <span style="color:#F5C9A8;">&#9679; &#9679; &#9679;</span>
                &nbsp;&nbsp;Vaša destinacija ostaje tajna sve do 72h pre polaska&nbsp;&nbsp;
                <span style="color:#F5C9A8;">&#9679; &#9679; &#9679;</span>
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
                <tr><td align="center" style="padding:40px 16px;">
                  <table class="mob-full" style="width:640px;max-width:640px;margin:0 auto;" cellpadding="0" cellspacing="0">

                    <!-- CARD -->
                    <tr><td style="background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;">

                      <!-- Accent bar -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td style="background:%s;height:4px;font-size:0;line-height:0;">&nbsp;</td></tr>
                      </table>

                      <!-- Header -->
                      <table width="100%%" cellpadding="0" cellspacing="0" style="background:%s;">
                        <tr><td style="padding:28px 40px 26px;" class="mob-pad">
                          <!-- Logo centered -->
                          <div style="text-align:center;margin-bottom:20px;">
                            <img src="%s"
                                 alt="Escapii" width="140" height="47"
                                 style="display:inline-block;border:0;max-width:140px;height:auto;"
                                 onerror="this.style.display='none'">
                          </div>
                          <!-- Heading -->
                          <h1 style="font-family:Georgia,'Times New Roman',serif;font-size:30px;color:#fff;line-height:1.3;margin:0 0 0;font-weight:normal;">%s</h1>
                          %s
                          %s
                        </td></tr>
                      </table>

                      <!-- Mystery strip -->
                      %s

                      <!-- Body -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td style="padding:28px 40px;background:#ffffff;" class="mob-pad">
                          %s
                        </td></tr>
                      </table>

                      <!-- Footer -->
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td style="background:%s;border-top:1px solid #e5e7eb;padding:16px 40px;text-align:center;font-size:11px;color:#9ca3af;line-height:1.8;">
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
            "#CA8A71",      // accent bar (Escapii brand color)
            headerBg,       // header bg
            LOGO_WHITE_URL, // logo centered
            headingText,    // h1
            subheadingHtml, // subtitle
            refHtml,        // ref chip
            mysteryHtml,    // mystery strip
            bodyContent,    // body
            "#1e1b4b".equals(headerBg) ? "#f0f0f5" : "#f8f9fa", // footer bg
            footerText      // footer
        );
    }

    public static String customerFooter(String email) {
        return """
            <strong style="color:#2D5F6B;">escapii</strong> — mystery travel d.o.o.<br>
            Beograd, Srbija · <a href="mailto:%s" style="color:#6b7280;text-decoration:underline;">%s</a><br><br>
            <a href="#" style="color:#6b7280;text-decoration:underline;">Politika privatnosti</a>
            """.formatted(email, email);
    }

    public static String dRow(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #f3f4f6;">
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;width:45%%;">%s</td>
              <td style="padding:7px 0;font-size:13px;color:#1f2937;font-weight:500;text-align:right;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String dRowStrike(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #f3f4f6;">
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;width:45%%;">%s</td>
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;font-weight:500;text-align:right;text-decoration:line-through;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String dRowMystery(String label, String value) {
        return """
            <tr>
              <td style="padding:7px 0;font-size:13px;color:#9ca3af;width:45%%;">%s</td>
              <td style="padding:7px 0;font-size:13px;color:#CA8A71;font-weight:600;font-style:italic;text-align:right;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String totalBox(int total, int n) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:24px;background:#0D2E38;border-radius:8px;">
              <tr>
                <td style="padding:20px 24px;">
                  <div style="font-size:10px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:rgba(255,255,255,0.45);margin-bottom:6px;">Ukupna cena putovanja</div>
                  <div style="font-family:Georgia,'Times New Roman',serif;font-size:34px;font-weight:700;color:#F5C9A8;line-height:1;margin-bottom:4px;">%s €</div>
                  <div style="font-size:12px;color:rgba(255,255,255,0.4);">za %d %s · sve uključeno</div>
                </td>
              </tr>
            </table>
            """.formatted(total, n, n == 1 ? "putnika" : "putnika");
    }

    public static String step(String num, String title, String description) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:12px;">
              <tr>
                <td style="width:36px;vertical-align:top;padding-top:2px;">
                  <div style="width:28px;height:28px;background:#CA8A71;border-radius:50%%;text-align:center;line-height:28px;font-size:13px;font-weight:800;color:#fff;">%s</div>
                </td>
                <td style="padding-left:12px;vertical-align:top;">
                  <div style="font-size:14px;color:#2D5F6B;font-weight:600;margin-bottom:2px;">%s</div>
                  <div style="font-size:13px;color:#6b7280;line-height:1.5;">%s</div>
                </td>
              </tr>
            </table>
            """.formatted(num, title, description);
    }

    public static String timelineItem(String icon, String iconBg, String accentColor,
                                      String title, String when, String description) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:16px;">
              <tr>
                <td style="width:38px;vertical-align:top;">
                  <div style="width:36px;height:36px;background:%s;border:2px solid %s;border-radius:50%%;text-align:center;line-height:32px;font-size:15px;">%s</div>
                </td>
                <td style="padding-left:14px;vertical-align:top;">
                  <div style="font-size:14px;font-weight:700;color:#2D5F6B;margin-bottom:2px;">%s</div>
                  <div style="font-size:11px;color:#9ca3af;margin-bottom:4px;">%s</div>
                  <div style="font-size:13px;color:#6b7280;line-height:1.5;">%s</div>
                </td>
              </tr>
            </table>
            """.formatted(iconBg, accentColor, icon, title, when, description);
    }

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
        if (type == null) return "Standard (3*)";
        return switch (type) {
            case SUPERIOR -> "Superior (4*)";
            case PREMIUM  -> "Premium (5*)";
            default       -> "Standard (3*)";
        };
    }
}
