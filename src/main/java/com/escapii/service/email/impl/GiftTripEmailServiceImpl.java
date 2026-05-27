package com.escapii.service.email.impl;

import com.escapii.model.GiftTripInquiry;
import com.escapii.service.email.GiftTripEmailService;
import com.escapii.service.email.core.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GiftTripEmailServiceImpl implements GiftTripEmailService {

    private final EmailSender emailSender;

    @Value("${app.team-email}")
    private String teamEmail;

    @Override
    @Async
    public void sendTeamAlert(GiftTripInquiry i) {
        String notes = (i.getNotes() != null && !i.getNotes().isBlank())
                ? "<tr><td style='padding:6px 0;color:#888;'>Napomena</td><td style='padding:6px 0;'>"
                  + i.getNotes() + "</td></tr>"
                : "";
        String giftMsg = (i.getGiftMessage() != null && !i.getGiftMessage().isBlank())
                ? "<tr><td style='padding:6px 0;color:#888;'>Poruka primaocu</td><td style='padding:6px 0;font-style:italic;'>\""
                  + i.getGiftMessage() + "\"</td></tr>"
                : "";

        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:0 auto;background:#0f2d35;color:#e8e0d5;border-radius:12px;padding:28px 32px;">
                  <h2 style="margin:0 0 6px;color:#CA8A71;font-size:20px;">🎁 Nov gift putovanje upit</h2>
                  <p style="margin:0 0 20px;font-size:13px;color:#888;">Kupac traži termin kao poklon — pošalji mu privatni link nakon formiranja cene.</p>
                  <table style="width:100%%;border-collapse:collapse;font-size:15px;">
                    <tr><td style="padding:6px 0;color:#888;width:150px;">ID</td><td style="padding:6px 0;">#%d</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Email kupca</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Aerodrom</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Putnici</td><td style="padding:6px 0;">%d</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Željeni datum</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Noći</td><td style="padding:6px 0;">%d</td></tr>
                    %s
                    <tr><td colspan="2" style="padding:12px 0 6px;border-top:1px solid rgba(255,255,255,.08);"></td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Primalac (ime)</td><td style="padding:6px 0;font-weight:700;color:#CA8A71;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Primalac (email)</td><td style="padding:6px 0;">%s</td></tr>
                    %s
                  </table>
                </div>
                """.formatted(
                        i.getId(), i.getBuyerEmail(), i.getAirport(),
                        i.getTravelers(), i.getDesiredDepartureDate(), i.getNights(),
                        notes,
                        i.getRecipientName(), i.getRecipientEmail(),
                        giftMsg);

        boolean ok = emailSender.send(teamEmail,
                "🎁 Gift putovanje upit #" + i.getId() + " za " + i.getRecipientName(), html);
        if (!ok) log.warn("[GiftTrip] Tim notifikacija nije poslata za upit id={}", i.getId());
    }
}
