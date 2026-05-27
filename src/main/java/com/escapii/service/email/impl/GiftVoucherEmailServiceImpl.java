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

        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:0 auto;background:#0f2d35;color:#e8e0d5;border-radius:12px;padding:32px;">
                  <div style="text-align:center;margin-bottom:28px;">
                    <div style="font-size:48px;margin-bottom:12px;">🎁</div>
                    <h1 style="margin:0;font-size:24px;color:#CA8A71;">Neko ti je poklonio putovanje!</h1>
                    <p style="margin:8px 0 0;font-size:15px;color:rgba(232,224,213,.7);">
                      %s ti šalje gift vaučer za Escapii
                    </p>
                  </div>

                  %s

                  <div style="background:rgba(255,255,255,.05);border-radius:12px;padding:20px;text-align:center;margin:20px 0;">
                    <p style="margin:0 0 8px;font-size:13px;color:#888;text-transform:uppercase;letter-spacing:.8px;">Tvoj vaučer kod</p>
                    <p style="margin:0;font-size:28px;font-weight:700;letter-spacing:3px;color:#CA8A71;font-family:monospace;">%s</p>
                    <p style="margin:8px 0 0;font-size:13px;color:#888;">Vrednost: <strong style="color:#e8e0d5;">%s EUR</strong></p>
                  </div>

                  <div style="margin:20px 0;padding:16px;background:rgba(255,255,255,.04);border-radius:8px;font-size:14px;color:rgba(232,224,213,.8);">
                    <p style="margin:0 0 8px;font-weight:700;">Kako iskoristiti vaučer?</p>
                    <ol style="margin:0;padding-left:20px;">
                      <li style="margin-bottom:6px;">Poseti <a href="%s" style="color:#CA8A71;">escapii.rs</a> i odaberi termin koji ti odgovara</li>
                      <li style="margin-bottom:6px;">Na kraju forme unesi vaučer kod</li>
                      <li>Cena putovanja biće umanjena za %s EUR</li>
                    </ol>
                  </div>

                  <p style="margin:16px 0 0;font-size:12px;color:#666;text-align:center;">
                    Vaučer važi 1 godinu od danas. Za pitanja: <a href="mailto:escapii.team@gmail.com" style="color:#CA8A71;">escapii.team@gmail.com</a>
                  </p>
                </div>
                """.formatted(
                        v.getBuyerName() != null && !v.getBuyerName().isBlank() ? v.getBuyerName() : "Neko poseban",
                        personalMessage,
                        v.getCode(),
                        v.getAmount().toPlainString(),
                        frontendUrl,
                        v.getAmount().toPlainString());

        boolean ok = emailSender.send(
                v.getRecipientEmail(),
                "🎁 " + (v.getBuyerName() != null && !v.getBuyerName().isBlank() ? v.getBuyerName() : "Neko") + " ti je poklonio putovanje!",
                html);
        if (!ok) log.warn("[GiftVoucher] Recipient email nije poslat za vaučer id={}", v.getId());
    }
}
