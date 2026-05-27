package com.escapii.service.email.impl;

import com.escapii.model.CustomDateInquiry;
import com.escapii.service.email.InquiryEmailService;
import com.escapii.service.email.core.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryEmailServiceImpl implements InquiryEmailService {

    private final EmailSender emailSender;

    @Value("${app.team-email}")
    private String teamEmail;

    @Override
    @Async
    public void sendTeamAlert(CustomDateInquiry i) {
        String notes = (i.getNotes() != null && !i.getNotes().isBlank())
                ? "<tr><td style='padding:6px 0;color:#888;'>Napomena</td><td style='padding:6px 0;'>" + i.getNotes() + "</td></tr>"
                : "";
        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:0 auto;background:#0f2d35;color:#e8e0d5;border-radius:12px;padding:28px 32px;">
                  <h2 style="margin:0 0 20px;color:#CA8A71;font-size:20px;">📅 Nov prilagođeni upit</h2>
                  <table style="width:100%%;border-collapse:collapse;font-size:15px;">
                    <tr><td style="padding:6px 0;color:#888;width:130px;">ID</td><td style="padding:6px 0;">#%d</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Email</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Aerodrom</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Putnici</td><td style="padding:6px 0;">%d</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Željeni datum</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Noći</td><td style="padding:6px 0;">%d</td></tr>
                    %s
                  </table>
                </div>
                """.formatted(
                        i.getId(), i.getEmail(), i.getAirport(),
                        i.getTravelers(), i.getDesiredDepartureDate(),
                        i.getNights(), notes);

        boolean ok = emailSender.send(teamEmail, "📅 Nov prilagođeni upit #" + i.getId(), html);
        if (!ok) log.warn("[Inquiry] Tim notifikacija nije poslata za upit id={}", i.getId());
    }
}
