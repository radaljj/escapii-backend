package com.escapii.service.email.core;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * From ostaje noreply@ (verifikovan kod provajdera), ali „Odgovori" u klijentu
 * mora da vodi na sanduče koje tim čita - Reply-To: info@escapii.rs, na svakom
 * mejlu, sa prilogom i bez njega.
 */
class EmailSenderReplyToTest {

    private static JavaMailSender mailSender() {
        JavaMailSender m = mock(JavaMailSender.class);
        when(m.createMimeMessage()).thenAnswer(inv -> new JavaMailSenderImpl().createMimeMessage());
        return m;
    }

    private static EmailSender sender(JavaMailSender mailSender, String from, String replyTo) {
        EmailSender s = new EmailSender(mailSender);
        ReflectionTestUtils.setField(s, "fromEmail", from);
        ReflectionTestUtils.setField(s, "fromName", "Escapii");
        ReflectionTestUtils.setField(s, "replyToEmail", replyTo);
        return s;
    }

    private static MimeMessage poslat(EmailSender sender, JavaMailSender mailSender, boolean prilog) {
        boolean ok = prilog
            ? sender.sendWithAttachment("kupac@example.com", "Naslov", "<p>Zdravo</p>",
                                        "vaucer.pdf", new byte[] {1, 2, 3}, "application/pdf")
            : sender.send("kupac@example.com", "Naslov", "<p>Zdravo</p>");
        assertTrue(ok, "slanje mora da uspe");
        ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(cap.capture());
        return cap.getValue();
    }

    private static String adresa(jakarta.mail.Address a) { return ((InternetAddress) a).getAddress(); }
    private static String ime(jakarta.mail.Address a)    { return ((InternetAddress) a).getPersonal(); }

    @Test
    void replyToIdeNaKontaktAdresu_fromOstajeNoreply() throws Exception {
        JavaMailSender m = mailSender();
        MimeMessage msg = poslat(sender(m, "noreply@escapii.rs", "info@escapii.rs"), m, false);

        assertEquals("noreply@escapii.rs", adresa(msg.getFrom()[0]));
        assertEquals("Escapii", ime(msg.getFrom()[0]));
        assertNotNull(msg.getHeader("Reply-To"), "Reply-To zaglavlje mora postojati");
        assertEquals(1, msg.getReplyTo().length);
        assertEquals("info@escapii.rs", adresa(msg.getReplyTo()[0]));
        assertEquals("Escapii", ime(msg.getReplyTo()[0]));
    }

    @Test
    void replyToIUzPrilog() throws Exception {
        JavaMailSender m = mailSender();
        MimeMessage msg = poslat(sender(m, "noreply@escapii.rs", "info@escapii.rs"), m, true);

        assertNotNull(msg.getHeader("Reply-To"));
        assertEquals("info@escapii.rs", adresa(msg.getReplyTo()[0]));
    }

    @Test
    void bezReplyToKadJePraznoIliIstoKaoFrom() throws Exception {
        JavaMailSender m1 = mailSender();
        MimeMessage prazno = poslat(sender(m1, "noreply@escapii.rs", ""), m1, false);
        assertNull(prazno.getHeader("Reply-To"), "prazno podešavanje = bez zaglavlja");

        JavaMailSender m2 = mailSender();
        MimeMessage isto = poslat(sender(m2, "info@escapii.rs", "INFO@escapii.rs"), m2, false);
        assertNull(isto.getHeader("Reply-To"), "isto kao From = suvišno zaglavlje se ne dodaje");

        JavaMailSender m3 = mailSender();
        MimeMessage nista = poslat(sender(m3, "noreply@escapii.rs", null), m3, false);
        assertNull(nista.getHeader("Reply-To"), "null (test bez konteksta) = bez zaglavlja");
    }
}
