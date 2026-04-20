package com.escapii.service.email.core;

import java.time.format.DateTimeFormatter;

public final class EmailHtmlBuilder {

    public static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private EmailHtmlBuilder() {}

    public static String wrapBase(
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
        String badgeBg = switch (badgeColor) {
            case "#16a34a" -> "rgba(22,163,74,0.2)";
            case "#dc2626" -> "rgba(220,38,38,0.2)";
            default        -> "rgba(249,115,22,0.2)";
        };
        String badgeText = switch (badgeColor) {
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
                      <img src="https://escapii.com/wp-content/themes/escapii-theme/images/logo-white.svg"
                           alt="Escapii" width="110" height="37"
                           style="display:inline-block;border:0;height:37px;width:110px;"
                           onerror="this.style.display='none'">
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
                                <img src="https://escapii.com/wp-content/themes/escapii-theme/images/logo-white.svg"
                                     alt="Escapii" width="90" height="30"
                                     style="display:inline-block;border:0;height:30px;width:90px;"
                                     onerror="this.style.display='none'">
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

    public static String customerFooter(String email) {
        return """
            <strong style="color:#08112a;">escapii</strong> — mystery travel d.o.o.<br>
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
              <td style="padding:7px 0;font-size:13px;color:#f97316;font-weight:600;font-style:italic;text-align:right;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String totalBox(int total, int n) {
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

    public static String resolveAccomLabel(String type) {
        if (type == null) return "Standard (3*)";
        return switch (type.toUpperCase()) {
            case "SUPERIOR" -> "Superior (4*)";
            case "PREMIUM"  -> "Premium (5*)";
            default         -> "Standard (3*)";
        };
    }
}
