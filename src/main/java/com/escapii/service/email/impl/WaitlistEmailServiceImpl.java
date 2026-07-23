package com.escapii.service.email.impl;

import com.escapii.service.email.WaitlistEmailService;
import com.escapii.service.email.core.EmailHtmlBuilder;
import com.escapii.service.email.core.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistEmailServiceImpl implements WaitlistEmailService {

    private final EmailSender sender;

    @Override
    @Async
    public void sendWaitlistConfirmation(String email, String airport) {
        String airportName = EmailHtmlBuilder.resolveAirportName(airport);
        String body = """
            <div style="text-align:center;padding:8px 0 20px;">
              <div style="font-size:44px;margin-bottom:12px;">✉</div>
              <div style="font-family:Georgia,'Times New Roman',serif;font-size:22px;color:#1a1410;margin-bottom:8px;font-weight:normal;">Dobrodošli na listu čekanja!</div>
              <div style="font-size:14px;color:#6b5d4f;line-height:1.6;">Prijavili ste se za sledeće odlaske iz:</div>
              <div style="margin:12px 0;">
                <span style="display:inline-block;background:#f5efe2;border:1px solid #ebe1cf;padding:6px 16px;border-radius:100px;font-size:12px;font-weight:700;color:#1a1410;letter-spacing:0.5px;">✈ %s</span>
              </div>
            </div>
            <div style="height:1px;background:#ebe1cf;margin:0 0 20px;"></div>
            <p style="margin:0 0 20px;font-size:13px;color:#6b5d4f;line-height:1.65;">
              Novi termini se dodaju redovno. Čim se pojave mesta za vaš polazni aerodrom, odmah ćemo vas obavestiti!
            </p>
            <div style="background:#faf6ee;border:1px solid #ebe1cf;border-left:3px solid #a85e44;border-radius:6px;padding:14px 18px;margin-bottom:20px;">
              <div style="font-size:13px;font-weight:700;color:#a85e44;margin-bottom:4px;">Šta sad?</div>
              <div style="font-size:13px;color:#1a1410;line-height:1.6;">Nema ništa što treba da radite - sedite, opustite se, i čekajte naš email. Biće vredno čekanja. ✦</div>
            </div>
            <p style="margin:0;font-size:12px;color:#a89888;line-height:1.6;">
              Ako ste se prijavili greškom, jednostavno ignorišite ovaj email.
            </p>
            """.formatted(EmailHtmlBuilder.esc(airportName));

        String html = EmailHtmlBuilder.wrapBase(
            "#a85e44",
            "#08112a",
            EmailHtmlBuilder.statusBadge("Lista čekanja", "orange"),
            "Na listi ste čekanja",
            "Bićete prvi koji će saznati čim se otvore novi termini.",
            "", body,
            "Escapii · escapii.rs",
            false
        );
        sender.send(email, "Na listi ste čekanja - Escapii", html);
    }

    @Override
    public boolean sendWaitlistNotification(String email, String airport) {
        String airportName = EmailHtmlBuilder.resolveAirportName(airport);
        String body = """
            <div style="text-align:center;padding:8px 0 20px;">
              <div style="font-size:44px;margin-bottom:12px;">✈</div>
              <div style="font-family:Georgia,'Times New Roman',serif;font-size:22px;color:#1a1410;margin-bottom:8px;font-weight:normal;">Termini su otvoreni!</div>
              <div style="font-size:14px;color:#6b5d4f;line-height:1.6;">Čekali ste - i isplatilo se.</div>
            </div>
            <div style="height:1px;background:#ebe1cf;margin:0 0 20px;"></div>
            <p style="margin:0 0 20px;font-size:15px;color:#1a1410;line-height:1.65;">
              Dostupni su novi termini za polazak sa aerodroma <strong style="color:#1a1410;">%s</strong>.
              Termini se brzo popunjavaju - rezervišite na vreme!
            </p>
            <div style="text-align:center;margin:24px 0;">
              <a href="https://escapii.rs" style="display:inline-block;background:#a85e44;color:#fff;font-weight:700;font-size:15px;padding:14px 40px;border-radius:100px;text-decoration:none;letter-spacing:0.3px;">
                Rezerviši sada &rarr;
              </a>
            </div>
            <p style="margin:0;font-size:12px;color:#a89888;line-height:1.6;">
              Primili ste ovaj email jer ste se prijavili na listu čekanja za aerodrom %s.
            </p>
            """.formatted(EmailHtmlBuilder.esc(airportName), EmailHtmlBuilder.esc(airportName));

        String html = EmailHtmlBuilder.wrapBase(
            "#a85e44",
            "#08112a",
            EmailHtmlBuilder.statusBadge("Novi termini", "orange"),
            "Otvorili su se novi termini!",
            "Termini se brzo popunjavaju - rezervišite na vreme!",
            "", body,
            "Escapii · escapii.rs",
            false
        );
        return sender.send(email, "Otvorili su se novi termini - Escapii", html);
    }
}
