package com.escapii.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Poslato" u panelu mora značiti da je mejl stvarno otišao.
 *
 * Ranije su dokument i profaktura slati kao @Async void, a vreme slanja se
 * upisivalo odmah - kad bi slanje puklo (npr. probijen dnevni limit kod
 * provajdera), panel je pokazivao "poslato" za mejl koji kupac nikad nije
 * dobio. Jedini trag je bio log.warn koji niko ne čita.
 */
class SentFlagTruthfulnessTest {

    private static String src(String p) throws Exception {
        return new String(Files.readAllBytes(Path.of(p)), StandardCharsets.UTF_8);
    }

    /** Mejlovi čiji ishod određuje upis u bazu ne smeju biti fire-and-forget. */
    @Test
    void slanjeSaPosledicomVracaIshod() throws Exception {
        String doc = src("src/main/java/com/escapii/service/email/ConfirmationDocumentEmailService.java");
        assertTrue(doc.contains("boolean sendConfirmationDocument"),
                "mora vraćati ishod - pozivalac na osnovu njega upisuje confirmationSentAt");

        String inv = src("src/main/java/com/escapii/service/email/InvoiceEmailService.java");
        assertTrue(inv.contains("boolean sendInvoiceToClient"), "profaktura mora vraćati ishod");
        assertTrue(inv.contains("boolean sendVoucherInvoiceToClient"), "profaktura vaučera mora vraćati ishod");
    }

    /** @Async bi vratio kontrolu pre slanja - ishod bi opet bio nepoznat. */
    @Test
    void tiSendoviNisuAsinhroni() throws Exception {
        for (String p : new String[]{
                "src/main/java/com/escapii/service/email/impl/ConfirmationDocumentEmailServiceImpl.java",
                "src/main/java/com/escapii/service/email/impl/InvoiceEmailServiceImpl.java"}) {
            assertFalse(src(p).contains("@Async"),
                    p + " ne sme biti @Async - ishod se ne bi znao u trenutku upisa");
        }
    }

    /** Vreme slanja se upisuje tek posle provere ishoda, nikad pre poziva. */
    @Test
    void vremeSlanjaSeUpisujeTekPosleUspeha() throws Exception {
        String inv = src("src/main/java/com/escapii/service/impl/InvoiceServiceImpl.java");
        int send = inv.indexOf("sendInvoiceToClient(booking");
        int mark = inv.indexOf("setInvoiceSentAt");
        assertTrue(send > 0 && mark > 0, "oba mesta moraju postojati");
        assertTrue(send < mark,
                "slanje mora biti PRE upisa invoiceSentAt - obrnuto bi evidentiralo neposlato");

        String rev = src("src/main/java/com/escapii/service/impl/RevealServiceImpl.java");
        assertTrue(rev.contains("if (confirmationDocumentEmailService.sendConfirmationDocument(booking))"),
                "upis confirmationSentAt mora biti uslovljen ishodom slanja");
    }
}
