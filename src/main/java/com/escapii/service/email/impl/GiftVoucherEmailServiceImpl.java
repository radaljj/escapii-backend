package com.escapii.service.email.impl;

import com.escapii.model.GiftVoucher;
import com.escapii.service.email.GiftVoucherEmailService;
import com.escapii.service.email.core.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GiftVoucherEmailServiceImpl implements GiftVoucherEmailService {

    private final EmailSender emailSender;

    @Value("${app.team-email}")
    private String teamEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ── Tim notifikacija (novi upit) ─────────────────────────────────────────

    @Override
    @Async
    public void sendTeamAlert(GiftVoucher v) {
        String buyerNameRow = (v.getBuyerName() != null && !v.getBuyerName().isBlank())
                ? "<tr><td style='padding:6px 0;color:#888;'>Ime kupca</td><td style='padding:6px 0;'>"
                  + v.getBuyerName() + "</td></tr>"
                : "";
        String messageRow = (v.getGiftMessage() != null && !v.getGiftMessage().isBlank())
                ? "<tr><td style='padding:6px 0;color:#888;'>Poruka</td><td style='padding:6px 0;'>"
                  + v.getGiftMessage() + "</td></tr>"
                : "";

        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:0 auto;background:#0f2d35;color:#e8e0d5;border-radius:12px;padding:28px 32px;">
                  <h2 style="margin:0 0 20px;color:#CA8A71;font-size:20px;">🎁 Nov gift vaučer upit</h2>
                  <table style="width:100%%;border-collapse:collapse;font-size:15px;">
                    <tr><td style="padding:6px 0;color:#888;width:140px;">ID</td><td style="padding:6px 0;">#%d</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Iznos</td><td style="padding:6px 0;font-weight:700;color:#CA8A71;">%s EUR</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Email kupca</td><td style="padding:6px 0;">%s</td></tr>
                    %s
                    %s
                  </table>
                  <p style="margin:20px 0 0;font-size:13px;color:#888;">
                    Nakon uplate, aktiviraj vaučer u admin panelu — sistem će automatski generisati PDF i poslati ga kupcu.
                  </p>
                </div>
                """.formatted(
                        v.getId(), v.getAmount().toPlainString(),
                        v.getBuyerEmail(),
                        buyerNameRow,
                        messageRow);

        boolean ok = emailSender.send(teamEmail,
                "🎁 Nov gift vaučer #" + v.getId() + " — " + v.getAmount().toPlainString() + " EUR",
                html);
        if (!ok) log.warn("[GiftVoucher] Tim notifikacija nije poslata za vaučer id={}", v.getId());
    }

    // ── PDF vaučer kupcu (nakon aktivacije) ─────────────────────────────────

    @Override
    @Async
    public void sendVoucherPdfToBuyer(GiftVoucher v, byte[] pdfBytes) {
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:0 auto;background:#0f2d35;
                            color:#e8e0d5;border-radius:12px;padding:36px 40px;">

                  <!-- Header -->
                  <div style="text-align:center;margin-bottom:32px;">
                    <div style="font-size:48px;margin-bottom:12px;">🎁</div>
                    <h1 style="margin:0;font-size:26px;color:#CA8A71;line-height:1.3;">
                      Tvoj Escapii vaučer je spreman!
                    </h1>
                  </div>

                  <!-- Info box -->
                  <div style="background:rgba(202,138,113,.1);border:1px solid rgba(202,138,113,.25);
                              border-radius:12px;padding:20px 24px;margin-bottom:28px;">
                    <table style="width:100%%;border-collapse:collapse;font-size:14px;">
                      <tr>
                        <td style="padding:6px 0;color:rgba(232,224,213,.55);width:120px;">Iznos vaučera</td>
                        <td style="padding:6px 0;font-weight:700;font-size:18px;color:#CA8A71;">%s EUR</td>
                      </tr>
                      <tr>
                        <td style="padding:6px 0;color:rgba(232,224,213,.55);">Vaučer kod</td>
                        <td style="padding:6px 0;font-weight:700;font-family:monospace;
                                   letter-spacing:2px;color:#e8e0d5;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:6px 0;color:rgba(232,224,213,.55);">Važi do</td>
                        <td style="padding:6px 0;color:#e8e0d5;">1 godinu od aktivacije</td>
                      </tr>
                    </table>
                  </div>

                  <!-- Instructions -->
                  <div style="margin-bottom:28px;">
                    <p style="font-size:14px;font-weight:700;color:#e8e0d5;margin:0 0 12px;">
                      📎 Vaučer je u prilogu kao PDF — možeš ga odštampati ili prikazati sa telefona.
                    </p>
                    <p style="font-size:14px;color:rgba(232,224,213,.75);line-height:1.7;margin:0 0 10px;">
                      Kod vaučera unosi se pri rezervaciji putovanja na
                      <a href="%s" style="color:#CA8A71;">escapii.rs</a> —
                      iznos se automatski odbija od cene putovanja.
                    </p>
                    <p style="font-size:14px;color:rgba(232,224,213,.75);line-height:1.7;margin:0;">
                      Vaučer važi za bilo koje Escapii putovanje iznenađenja i može se iskoristiti
                      u celosti. Ne može se zameniti za gotovinu.
                    </p>
                  </div>

                  <!-- CTA -->
                  <div style="text-align:center;margin-bottom:28px;">
                    <a href="%s"
                       style="display:inline-block;padding:16px 40px;background:#CA8A71;color:#fff;
                              text-decoration:none;border-radius:12px;font-size:15px;font-weight:700;
                              letter-spacing:.3px;box-shadow:0 6px 24px rgba(202,138,113,.35);">
                      Rezerviši putovanje →
                    </a>
                  </div>

                  <!-- Footer -->
                  <p style="margin:0;font-size:12px;color:#555;text-align:center;line-height:1.7;">
                    Za pitanja: <a href="mailto:escapii.team@gmail.com" style="color:#CA8A71;">escapii.team@gmail.com</a>
                  </p>
                </div>
                """.formatted(
                        v.getAmount().toPlainString(),
                        v.getCode(),
                        frontendUrl,
                        frontendUrl);

        String attachmentName = "escapii-poklon-" + v.getCode() + ".pdf";
        boolean ok = emailSender.sendWithAttachment(
                v.getBuyerEmail(),
                "🎁 Tvoj Escapii poklon vaučer — " + v.getAmount().toPlainString() + " EUR",
                html,
                attachmentName,
                pdfBytes,
                "application/pdf");
        if (!ok) log.warn("[GiftVoucher] PDF email nije poslat kupcu za vaučer id={}", v.getId());
    }
}
