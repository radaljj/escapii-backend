package com.escapii.service.email.core;

import com.escapii.model.AccommodationType;
import com.escapii.model.Booking;

import java.time.format.DateTimeFormatter;

/**
 * v2 - Light-first email shell (cream header, dark text).
 */
public final class EmailHtmlBuilder {

    public static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static volatile String LOGO_WHITE_URL = "";
    public static volatile String LOGO_BLACK_URL = "";

    private EmailHtmlBuilder() {}

    /** "Dragi" ili "Draga" na osnovu pola nosioca rezervacije. Fallback: "Dragi/a" za stare rezervacije. */
    public static String salutation(Booking booking) {
        String g = booking.getLeadPassengerGender();
        if ("M".equals(g)) return "Dragi";
        if ("F".equals(g)) return "Draga";
        return "Dragi/a";
    }

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
        String headerBg,      // ignorisano - zadržano zbog kompatibilnosti poziva
        String badgeHtml,
        String headingText,
        String subheading,
        String refCode,
        String bodyContent,
        String footerText,
        boolean mysteryStrip
    ) {
        // Bez eksplicitnog preheadera - naslov se koristi kao pregled u inbox-u
        return wrapBase(accentBarColor, headerBg, badgeHtml, headingText, subheading,
                        refCode, bodyContent, footerText, mysteryStrip, headingText);
    }

    /** Varijanta sa zasebnim preheader tekstom (ono što se vidi u inbox listi). */
    public static String wrapBase(
        String accentBarColor,
        String headerBg,
        String badgeHtml,
        String headingText,
        String subheading,
        String refCode,
        String bodyContent,
        String footerText,
        boolean mysteryStrip,
        String preheader
    ) {
        String accent = (accentBarColor == null || accentBarColor.isBlank()) ? "#a85e44" : accentBarColor;

        // Fragmenti nose sopstvene margine - shell ih ubacuje u mj-text sa padding=0,
        // pa prazna vrednost ne ostavlja prazan prostor.
        String badgeBlock = badgeHtml.isBlank() ? "" :
            "<div style=\"margin-bottom:14px;text-align:center;\">" + badgeHtml + "</div>";

        String subheadingHtml = subheading.isBlank() ? "" :
            "<p style=\"margin:10px 0 0;font-size:14px;color:#6b5d4f;line-height:1.6;\">%s</p>"
                .formatted(subheading);

        String refHtml = refCode.isBlank() ? "" :
            ("<div style=\"display:inline-block;background:rgba(168,94,68,0.08);"
            + "border:1px solid rgba(168,94,68,0.25);color:#a85e44;font-family:'Courier New',monospace;"
            + "font-size:11px;font-weight:700;padding:4px 10px;border-radius:4px;letter-spacing:1px;"
            + "margin-top:14px;\">&#10022; %s</div>").formatted(refCode);

        // replace() je literalan (nije regex) - siguran za sadržaj sa $ ili backslash
        return loadTemplate(mysteryStrip ? "shell-mystery.html" : "shell.html")
                .replace("{{PREHEADER}}",  preheader)
                .replace("{{ACCENT}}",     accent)
                .replace("{{BADGE}}",      badgeBlock)
                .replace("{{HEADING}}",    headingText)
                .replace("{{SUBHEADING}}", subheadingHtml)
                .replace("{{REF}}",        refHtml)
                .replace("{{BODY}}",       bodyContent)
                .replace("{{FOOTER}}",     footerText);
    }

    /** Učitava MJML-kompajlirani shell iz src/main/resources/email/. */
    private static String loadTemplate(String filename) {
        try (var is = EmailHtmlBuilder.class.getResourceAsStream("/email/" + filename)) {
            if (is == null) {
                throw new IllegalStateException("Email shell nije pronađen: /email/" + filename);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Nije moguće učitati email shell: " + filename, e);
        }
    }

    public static String customerFooter(String email) {
        return """
            <strong style="color:#1a1410;">escapii</strong> - putovanja iznenađenja<br>
            Beograd, Srbija · <a href="mailto:%s" style="color:#a85e44;text-decoration:none;font-weight:600;">%s</a><br><br>
            <a href="https://escapii.rs" style="color:#a85e44;text-decoration:none;font-weight:600;">escapii.rs</a>
            """.formatted(email, email);
    }

    public static String dRow(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #ebe1cf;">
              <td width="50%%" style="width:50%%;padding:8px 0;font-size:13px;color:#a89888;">%s</td>
              <td width="50%%" style="width:50%%;padding:8px 0;font-size:13px;color:#1a1410;font-weight:600;text-align:right;white-space:nowrap;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String dRowStrike(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #ebe1cf;">
              <td width="50%%" style="width:50%%;padding:8px 0;font-size:13px;color:#a89888;">%s</td>
              <td width="50%%" style="width:50%%;padding:8px 0;font-size:13px;color:#a89888;font-weight:500;text-align:right;white-space:nowrap;text-decoration:line-through;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String dRowMystery(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #ebe1cf;">
              <td width="50%%" style="width:50%%;padding:8px 0;font-size:13px;color:#a89888;">%s</td>
              <td width="50%%" style="width:50%%;padding:8px 0;font-size:13px;color:#a85e44;font-weight:600;font-style:italic;text-align:right;">%s</td>
            </tr>
            """.formatted(label, value);
    }

    public static String detailsCard(String title, String rowsHtml, String borderColor) {
        String bc = (borderColor == null || borderColor.isBlank()) ? "#a85e44" : borderColor;
        return """
            <table width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 22px;">
              <tr>
                <td width="100%%" style="background:#faf6ee;border-top:1px solid #ebe1cf;border-right:1px solid #ebe1cf;border-bottom:1px solid #ebe1cf;border-left:3px solid %s;padding:18px 20px;">
                  <p style="margin:0 0 12px;font-size:10px;font-weight:800;letter-spacing:2px;text-transform:uppercase;color:#a89888;">%s</p>
                  <table width="100%%" cellpadding="0" cellspacing="0">%s</table>
                </td>
              </tr>
            </table>
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
