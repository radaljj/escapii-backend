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

    @Override
    @Async
    public void sendTeamAlert(GiftVoucher v) {
        String message = (v.getGiftMessage() != null && !v.getGiftMessage().isBlank())
                ? "<tr><td style='padding:6px 0;color:#888;'>Poruka</td><td style='padding:6px 0;'>"
                  + v.getGiftMessage() + "</td></tr>"
                : "";
        String buyerNameRow = (v.getBuyerName() != null && !v.getBuyerName().isBlank())
                ? "<tr><td style='padding:6px 0;color:#888;'>Kupac (ime)</td><td style='padding:6px 0;'>"
                  + v.getBuyerName() + "</td></tr>"
                : "";

        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:0 auto;background:#0f2d35;color:#e8e0d5;border-radius:12px;padding:28px 32px;">
                  <h2 style="margin:0 0 20px;color:#CA8A71;font-size:20px;">🎁 Nov gift vaučer upit</h2>
                  <table style="width:100%%;border-collapse:collapse;font-size:15px;">
                    <tr><td style="padding:6px 0;color:#888;width:140px;">ID</td><td style="padding:6px 0;">#%d</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Iznos</td><td style="padding:6px 0;font-weight:700;color:#CA8A71;">%s EUR</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Kupac (email)</td><td style="padding:6px 0;">%s</td></tr>
                    %s
                    <tr><td style="padding:6px 0;color:#888;">Primalac (ime)</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Primalac (email)</td><td style="padding:6px 0;">%s</td></tr>
                    %s
                  </table>
                  <p style="margin:20px 0 0;font-size:13px;color:#888;">
                    Nakon uplate, aktiviraj vaučer u admin panelu — sistem će automatski poslati kod primaocu.
                  </p>
                </div>
                """.formatted(
                        v.getId(), v.getAmount().toPlainString(),
                        v.getBuyerEmail(), buyerNameRow,
                        v.getRecipientName(), v.getRecipientEmail(),
                        message);

        boolean ok = emailSender.send(teamEmail, "🎁 Nov gift vaučer #" + v.getId() + " — " + v.getAmount().toPlainString() + " EUR", html);
        if (!ok) log.warn("[GiftVoucher] Tim notifikacija nije poslata za vaučer id={}", v.getId());
    }

    @Override
    @Async
    public void sendVoucherToRecipient(GiftVoucher v) {
        String personalMessage = (v.getGiftMessage() != null && !v.getGiftMessage().isBlank())
                ? """
                  <div style="margin:20px 0;padding:16px 20px;background:rgba(202,138,113,.08);
                               border-left:3px solid #CA8A71;border-radius:0 8px 8px 0;">
                    <p style="margin:0;font-size:14px;color:#e8e0d5;font-style:italic;">"%s"</p>
                  </div>
                  """.formatted(v.getGiftMessage())
                : "";

        String revealUrl = frontendUrl + "/poklon?k=" + v.getCode();

        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:0 auto;background:#0f2d35;color:#e8e0d5;border-radius:12px;padding:32px;">
                  <div style="text-align:center;margin-bottom:28px;">
                    <div style="font-size:56px;margin-bottom:14px;">🎁</div>
                    <h1 style="margin:0;font-size:24px;color:#CA8A71;line-height:1.3;">Neko ti je poklonio putovanje!</h1>
                    <p style="margin:10px 0 0;font-size:15px;color:rgba(232,224,213,.65);">
                      <strong style="color:rgba(232,224,213,.9);">%s</strong> ti šalje iznenađenje putem Escapii
                    </p>
                  </div>

                  %s

                  <div style="text-align:center;margin:28px 0;">
                    <a href="%s"
                       style="display:inline-block;padding:16px 36px;background:#CA8A71;color:#fff;
                              text-decoration:none;border-radius:12px;font-size:16px;font-weight:700;
                              letter-spacing:.3px;box-shadow:0 6px 24px rgba(202,138,113,.35);">
                      🎁 Otkrij svoj poklon →
                    </a>
                    <p style="margin:12px 0 0;font-size:12px;color:#666;">
                      Klikni da vidiš šta si dobio/la
                    </p>
                  </div>

                  <div style="margin:24px 0;padding:16px;background:rgba(255,255,255,.04);border-radius:8px;font-size:14px;color:rgba(232,224,213,.7);">
                    <p style="margin:0 0 6px;font-weight:700;color:rgba(232,224,213,.9);">Kako iskoristiti poklon?</p>
                    <ol style="margin:0;padding-left:20px;line-height:1.8;">
                      <li>Klikni dugme iznad da otkriješ detalje svog poklona</li>
                      <li>Poseti <a href="%s" style="color:#CA8A71;">escapii.rs</a> i odaberi termin koji ti odgovara</li>
                      <li>U koraku 7 unesi vaučer kod — iznos se automatski oduzima od cene</li>
                    </ol>
                  </div>

                  <p style="margin:16px 0 0;font-size:12px;color:#555;text-align:center;line-height:1.6;">
                    Vaučer važi 1 godinu od aktivacije.<br>
                    Za pitanja: <a href="mailto:escapii.team@gmail.com" style="color:#CA8A71;">escapii.team@gmail.com</a>
                  </p>
                </div>
                """.formatted(
                        v.getBuyerName() != null && !v.getBuyerName().isBlank() ? v.getBuyerName() : "Neko poseban",
                        personalMessage,
                        revealUrl,
                        frontendUrl);

        boolean ok = emailSender.send(
                v.getRecipientEmail(),
                "🎁 " + (v.getBuyerName() != null && !v.getBuyerName().isBlank() ? v.getBuyerName() : "Neko") + " ti je poklonio putovanje!",
                html);
        if (!ok) log.warn("[GiftVoucher] Recipient email nije poslat za vaučer id={}", v.getId());
    }
}
