package com.escapii.service.email.core;

import com.escapii.model.AccommodationType;

import java.time.format.DateTimeFormatter;

/**
 * v2 — Light-first email shell (cream header, dark text).
 */
public final class EmailHtmlBuilder {

    public static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static volatile String LOGO_WHITE_URL = "";
    public static volatile String LOGO_BLACK_URL = "";

    private EmailHtmlBuilder() {}

    public static String statusBadge(String label, String type) {
        String style = switch (type) {
            case "green" -> "background:#eef6f0;color:#1d6042;border:1px solid #c3d8c9;";
            case "red"   -> "background:#fbeeec;color:#9b3a2a;border:1px solid #e9c5bd;";
            case "blue"  -> "background:#eaf0f3;color:#1f4a57;border:1px solid #bcd0d6;";
            default      -> "background:#fff5eb;color:#a85e44;border:1px solid #e8c7b1;";
        };
        return "<span style=\"display:inline-block;padding:5px 12px;border-radius:100px;font-size:10px;"
             + "font-weight:700;letter-spacing:1.8px;text-transform:uppercase;" + style + "\">"
             + label + "</span>";
    }

    public static String wrapBase(
        String accentBarColor,
        String headerBg,      // ignorisano u v2 — uvek cream
        String badgeHtml,
        String headingText,
        String subheading,
        String refCode,
        String bodyContent,
        String footerText,
        boolean mysteryStrip
    ) {
        String accent = (accentBarColor == null || accentBarColor.isBlank()) ? "#a85e44" : accentBarColor;

        String badgeBlock = badgeHtml.isBlank() ? "" :
            "<div style=\"margin-bottom:14px;\">" + badgeHtml + "</div>";

        String subheadingHtml = subheading.isBlank() ? "" :
            "<p style=\"margin:10px 0 0;font-size:14px;color:#6b5d4f;line-height:1.6;max-width:90%%;\">%s</p>"
                .formatted(subheading);

        String refHtml = refCode.isBlank() ? "" :
            "<div style=\"display:inline-block;background:rgba(168,94,68,0.08);"
            + "border:1px solid rgba(168,94,68,0.25);color:#a85e44;font-family:'Courier New',monospace;"
            + "font-size:11px;font-weight:700;padding:4px 10px;border-radius:4px;letter-spacing:1px;"
            + "margin-top:14px;\">&#10022; %s</div>".formatted(refCode);

        String mysteryHtml = mysteryStrip ? """
            <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f5efe2;">
              <tr><td style="padding:12px 40px;font-size:11px;color:#6b5d4f;text-align:center;letter-spacing:1.2px;border-top:1px dashed #d8cab2;border-bottom:1px dashed #d8cab2;">
                <span style="color:#a85e44;letter-spacing:3px;">&#9679; &#9679; &#9679;</span>
                &nbsp;&nbsp;Vaša destinacija ostaje tajna sve do 48h pre polaska&nbsp;&nbsp;
                <span style="color:#a85e44;letter-spacing:3px;">&#9679; &#9679; &#9679;</span>
              </td></tr>
            </table>
            """ : "";

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
                body { background:#ebe4d4 !important; }
              </style>
              <![endif]-->
              <style>
                @media (max-width:620px) {
                  .mob-full { width:100%% !important; }
                  .mob-pad  { padding:22px !important; }
                }
                @media (prefers-color-scheme: dark) {
                  .force-cream  { background:#ebe4d4 !important; }
                  .force-white  { background:#ffffff !important; }
                  .force-sand   { background:#f5efe2 !important; }
                  .ink   { color:#1a1410 !important; }
                  .mute  { color:#6b5d4f !important; }
                }
              </style>
            </head>
            <body style="margin:0;padding:0;word-break:break-word;-webkit-font-smoothing:antialiased;background:#ebe4d4;" bgcolor="#ebe4d4">

              <div style="display:none;max-height:0;overflow:hidden;mso-hide:all;">
                %s &#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&#847;&zwnj;&nbsp;
              </div>

              <table width="100%%" cellpadding="0" cellspacing="0" bgcolor="#ebe4d4" style="background:#ebe4d4;" class="force-cream">
                <tr><td align="center" style="padding:40px 16px;">
                  <!--[if mso]><table width="640" cellpadding="0" cellspacing="0"><tr><td><![endif]-->
                  <table class="mob-full" style="width:640px;max-width:640px;margin:0 auto;" cellpadding="0" cellspacing="0">

                    <tr><td bgcolor="#ffffff" style="background:#ffffff;border:1px solid #ebe1cf;border-radius:10px;overflow:hidden;" class="force-white">

                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td bgcolor="%s" style="background:%s;height:4px;font-size:0;line-height:0;">&nbsp;</td></tr>
                      </table>

                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td bgcolor="#f5efe2" style="background:#f5efe2;padding:32px 40px 28px;border-bottom:1px solid #ebe1cf;" class="mob-pad force-sand">

                          <table cellpadding="0" cellspacing="0" style="margin-bottom:18px;">
                            <tr><td align="center">
                              <!--[if !mso]><!-->
                              <a href="https://escapii.rs" style="text-decoration:none;display:block;">
                                <img src="https://escapii.rs/wp-content/themes/escapii-theme/images/logo-black.png"
                                     alt="escapii?"
                                     width="120" height="40"
                                     style="display:block;border:0;outline:0;width:120px;max-width:120px;height:auto;"
                                     class="logo">
                              </a>
                              <!--<![endif]-->
                              <!--[if mso]>
                              <a href="https://escapii.rs" style="text-decoration:none;color:#1a1410;">
                                <span style="font-family:Georgia,serif;font-size:26px;font-weight:700;color:#1a1410;">escapii?</span>
                              </a>
                              <![endif]-->
                            </td></tr>
                          </table>

                          %s

                          <h1 class="ink" style="font-family:Georgia,'Times New Roman',serif;font-size:32px;color:#1a1410;line-height:1.2;margin:0;font-weight:normal;letter-spacing:-0.3px;mso-line-height-rule:exactly;">%s</h1>
                          %s
                          %s
                        </td></tr>
                      </table>

                      %s

                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td bgcolor="#ffffff" style="padding:32px 40px;background:#ffffff;color:#1a1410;" class="mob-pad force-white ink">
                          %s
                        </td></tr>
                      </table>

                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr><td bgcolor="#f5efe2" style="background:#f5efe2;border-top:1px solid #ebe1cf;padding:22px 40px;text-align:center;font-size:11px;color:#6b5d4f;line-height:1.8;" class="force-sand mute">
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
            headingText,
            accent,
            accent,
            badgeBlock,
            headingText,
            subheadingHtml,
            refHtml,
            mysteryHtml,
            bodyContent,
            footerText
        );
    }

    public static String customerFooter(String email) {
        return """
            <strong style="color:#1a1410;">escapii</strong> — putovanja iznenađenja<br>
            Beograd, Srbija · <a href="mailto:%s" style="color:#a85e44;text-decoration:none;font-weight:600;">%s</a><br><br>
            <a href="https://escapii.rs" style="color:#a85e44;text-decoration:none;font-weight:600;">escapii.rs</a>
            """.formatted(email, email);
    }

    public static String dRow(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #ebe1cf;">
              <td style="padding:8px 0;font-size:13px;color:#a89888;width:50%%;">%s</td>
              <td style="padding:8px 0;font-size:13px;color:#1a1410;font-weight:600;text-align:right;white-space:nowrap;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String dRowStrike(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #ebe1cf;">
              <td style="padding:8px 0;font-size:13px;color:#a89888;width:50%%;">%s</td>
              <td style="padding:8px 0;font-size:13px;color:#a89888;font-weight:500;text-align:right;white-space:nowrap;text-decoration:line-through;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String dRowMystery(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #ebe1cf;">
              <td style="padding:8px 0;font-size:13px;color:#a89888;width:45%%;">%s</td>
              <td style="padding:8px 0;font-size:13px;color:#a85e44;font-weight:600;font-style:italic;text-align:right;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String detailsCard(String title, String rowsHtml, String borderColor) {
        String bc = (borderColor == null || borderColor.isBlank()) ? "#a85e44" : borderColor;
        return """
            <div style="background:#faf6ee;border:1px solid #ebe1cf;border-left:3px solid %s;border-radius:8px;padding:20px 22px;margin:0 0 22px;">
              <div style="font-size:10px;font-weight:800;letter-spacing:2px;text-transform:uppercase;color:#a89888;margin-bottom:14px;">%s</div>
              <table width="100%%" cellpadding="0" cellspacing="0">%s</table>
            </div>
            """.formatted(bc, title, rowsHtml);
    }

    public static String totalBox(int total, int n) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:22px;background:#faf6ee;border:1px solid #ebe1cf;border-radius:10px;">
              <tr>
                <td style="padding:20px 24px;">
                  <div style="font-size:10px;font-weight:800;letter-spacing:2px;text-transform:uppercase;color:#a89888;margin-bottom:6px;">Ukupna cena putovanja</div>
                  <div style="font-family:Georgia,'Times New Roman',serif;font-size:34px;font-weight:700;color:#a85e44;line-height:1;margin-bottom:4px;">%s €</div>
                  <div style="font-size:12px;color:#6b5d4f;">za %d putnika</div>
                </td>
              </tr>
            </table>
            """.formatted(total, n);
    }

    public static String step(String num, String title, String description) {
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:14px;">
              <tr>
                <td style="width:38px;vertical-align:top;padding-top:2px;">
                  <div style="width:30px;height:30px;background:#a85e44;border-radius:50%%;text-align:center;line-height:30px;font-size:13px;font-weight:800;color:#fff;">%s</div>
                </td>
                <td style="padding-left:12px;vertical-align:top;">
                  <div style="font-size:14px;color:#1a1410;font-weight:600;margin-bottom:2px;">%s</div>
                  <div style="font-size:13px;color:#6b5d4f;line-height:1.55;">%s</div>
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
                <td style="width:42px;vertical-align:top;">
                  <div style="width:38px;height:38px;background:%s;border:2px solid %s;border-radius:50%%;text-align:center;line-height:34px;font-size:16px;color:%s;">%s</div>
                </td>
                <td style="padding-left:14px;vertical-align:top;">
                  <div style="font-size:14px;font-weight:700;color:#1a1410;margin-bottom:2px;">%s</div>
                  <div style="font-size:11px;color:#a89888;margin-bottom:4px;">%s</div>
                  <div style="font-size:13px;color:#6b5d4f;line-height:1.55;">%s</div>
                </td>
              </tr>
            </table>
            """.formatted(iconBg, accentColor, accentColor, icon, title, when, description);
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
        if (type == null) return "Standard (3★)";
        return switch (type) {
            case SUPERIOR -> "Superior (4★)";
            case PREMIUM  -> "Premium (5★)";
            default       -> "Standard (3★)";
        };
    }
}
