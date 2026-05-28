package com.escapii.service.email.core;

import com.escapii.util.LogUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
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

    /**
     * Šalje email. Vraća {@code true} ako je email uspešno poslat, {@code false} ako je
     * došlo do greške (greška je već logovana — caller odlučuje šta dalje).
     */
    public boolean send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("[EmailSender] Email poslan na {}", LogUtils.maskEmail(to));
            return true;
        } catch (MessagingException e) {
            log.error("[EmailSender] MessagingException za {}: {}", LogUtils.maskEmail(to), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            // MailException (Spring, RuntimeException) — auth failure, connection refused, itd.
            log.error("[EmailSender] Greška pri slanju na {} — proveriti SMTP env vars (MAIL_USERNAME, MAIL_APP_PASSWORD): {}",
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
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
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
