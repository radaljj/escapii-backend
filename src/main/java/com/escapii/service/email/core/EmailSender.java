package com.escapii.service.email.core;

import com.escapii.util.LogUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;

    @Value("${app.mail-from}")
    private String fromEmail;

    /** Ime koje primalac vidi umesto gole adrese (npr. "Escapii" umesto "noreply"). */
    @Value("${app.mail-from-name:Escapii}")
    private String fromName;

    /**
     * Postavlja pošiljaoca sa prikaznim imenom. Ako kodiranje imena pukne,
     * pada nazad na golu adresu - bolje poslati mejl bez lepog imena nego ne poslati ga.
     */
    private void setFrom(MimeMessageHelper helper) throws MessagingException {
        try {
            helper.setFrom(fromEmail, fromName);
        } catch (UnsupportedEncodingException e) {
            log.warn("[EmailSender] Ime pošiljaoca '{}' nije moguće kodirati, šaljem bez njega", fromName);
            helper.setFrom(fromEmail);
        }
    }

    /**
     * Postavlja telo kao multipart/alternative - plain text + HTML.
     * Mejlovi bez tekstualne verzije dobijaju lošiju ocenu kod spam filtera
     * (Microsoft je posebno osetljiv), a tekst je i jedino što vide čitači
     * ekrana i klijenti sa isključenim HTML-om.
     */
    private void setBody(MimeMessageHelper helper, String html) throws MessagingException {
        helper.setText(toPlainText(html), html);
    }

    /**
     * Gruba ali dovoljna HTML → tekst konverzija. Ne pokušava da reprodukuje
     * raspored: cilj je čitljiv tekst sa istom porukom, ne verna kopija.
     */
    static String toPlainText(String html) {
        String s = html;
        // <head>, <style>, <script> nose CSS i skriptu - ne smeju u tekst
        s = s.replaceAll("(?is)<head[^>]*>.*?</head>", " ");
        s = s.replaceAll("(?is)<(style|script)[^>]*>.*?</\\1>", " ");
        // komentari (uključujući Outlook mso uslovne blokove)
        s = s.replaceAll("(?s)<!--.*?-->", " ");
        // elementi koji vizuelno prelamaju red
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(p|div|tr|h[1-6]|li|table)>", "\n");
        // preostali tagovi
        s = s.replaceAll("(?s)<[^>]+>", " ");
        // entiteti - prvo imenovani, pa numerički, &amp; na kraju da se ne udvostruči
        s = s.replace("&nbsp;", " ").replace("&middot;", "·")
             .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
        s = s.replaceAll("&#(\\d+);", "");
        s = s.replace("&amp;", "&");
        // sredi razmake: najviše jedan prazan red, bez vodećih razmaka u redu
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        s = s.replaceAll(" ?\n ?", "\n");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    /**
     * Šalje email. Vraća {@code true} ako je email uspešno poslat, {@code false} ako je
     * došlo do greške (greška je već logovana - caller odlučuje šta dalje).
     */
    public boolean send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            setFrom(helper);
            helper.setTo(to);
            helper.setSubject(subject);
            setBody(helper, html);
            mailSender.send(message);
            log.info("[EmailSender] Email poslan na {}", LogUtils.maskEmail(to));
            return true;
        } catch (MessagingException e) {
            log.error("[EmailSender] MessagingException za {}: {}", LogUtils.maskEmail(to), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            // MailException (Spring, RuntimeException) - auth failure, connection refused, itd.
            log.error("[EmailSender] Greška pri slanju na {} - proveriti SMTP env vars (MAIL_USERNAME, MAIL_APP_PASSWORD): {}",
                    LogUtils.maskEmail(to), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Šalje email sa jednim prilogom.
     * Vraća {@code true} ako je email uspešno poslat, {@code false} ako je
     * došlo do greške (greška je već logovana).
     *
     * @param to             primalac
     * @param subject        naslov
     * @param html           HTML telo
     * @param attachmentName ime fajla koji se šalje u prilogu (npr. "vaucer.pdf")
     * @param attachmentData sadržaj fajla
     * @param contentType    MIME tip (npr. "application/pdf")
     */
    public boolean sendWithAttachment(String to, String subject, String html,
                                      String attachmentName, byte[] attachmentData,
                                      String contentType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            setFrom(helper);
            helper.setTo(to);
            helper.setSubject(subject);
            setBody(helper, html);
            helper.addAttachment(attachmentName, new ByteArrayResource(attachmentData), contentType);
            mailSender.send(message);
            log.info("[EmailSender] Email sa prilogom '{}' poslan na {}", attachmentName, LogUtils.maskEmail(to));
            return true;
        } catch (MessagingException e) {
            log.error("[EmailSender] MessagingException (prilog) za {}: {}", LogUtils.maskEmail(to), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("[EmailSender] Greška pri slanju (prilog) na {}: {}", LogUtils.maskEmail(to), e.getMessage(), e);
            return false;
        }
    }

    public String getFrom() {
        return fromEmail;
    }
}
