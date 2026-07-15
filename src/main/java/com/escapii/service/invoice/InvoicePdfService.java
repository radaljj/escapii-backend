package com.escapii.service.invoice;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Slf4j
@Lazy
@Service
public class InvoicePdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy.", new Locale("sr"));
    private static final Semaphore PDF_SEMAPHORE = new Semaphore(3, true);

    private final TemplateEngine templateEngine;

    public InvoicePdfService() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);

        XRLog.setLevel(XRLog.GENERAL, Level.WARNING);
    }

    public byte[] generate(InvoiceData data) {
        try {
            boolean acquired = PDF_SEMAPHORE.tryAcquire(10, TimeUnit.MINUTES);
            if (!acquired) {
                throw new RuntimeException("PDF generisanje nije moglo da počne - server prezauzet");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("PDF generisanje prekinuto dok je čekalo na semafor", e);
        }

        try {
            String logoDataUri = loadImageDataUri("static/images/logo-black.png", "image/png");

            Context ctx = new Context(new Locale("sr"));
            ctx.setVariable("invoiceNumber",  data.invoiceNumber());
            ctx.setVariable("issuedAt",       data.issuedAt().format(DATE_FMT));
            ctx.setVariable("dueDate",        data.dueDate().format(DATE_FMT));
            ctx.setVariable("clientName",     data.clientFullName());
            ctx.setVariable("clientEmail",    data.clientEmail());
            ctx.setVariable("clientPhone",    data.clientPhone());
            ctx.setVariable("bookingRef",     data.bookingRef());
            ctx.setVariable("departureDate",  data.departureDate().format(DATE_FMT));
            ctx.setVariable("returnDate",     data.returnDate().format(DATE_FMT));
            ctx.setVariable("travelers",      data.numberOfTravelers());
            ctx.setVariable("subtotal",       data.subtotalEur());
            ctx.setVariable("hasVoucher",     data.hasVoucher());
            ctx.setVariable("voucherCode",    data.voucherCode() != null ? maskVoucherCode(data.voucherCode()) : "");
            ctx.setVariable("voucherDiscount", data.voucherDiscountEur());
            ctx.setVariable("total",          data.totalEur());
            ctx.setVariable("companyName",    data.companyName());
            ctx.setVariable("companyAddress", data.companyAddress());
            ctx.setVariable("companyPib",     data.companyPib());
            ctx.setVariable("companyMb",      data.companyMb());
            ctx.setVariable("companyAccount", data.companyAccount());
            ctx.setVariable("companyBank",    data.companyBank());
            ctx.setVariable("companyEmail",   data.companyEmail());
            ctx.setVariable("companyWebsite", data.companyWebsite());
            ctx.setVariable("ipsQrDataUri",   data.ipsQrDataUri());
            ctx.setVariable("logoDataUri",    logoDataUri);

            String html = templateEngine.process("invoice", ctx);

            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                registerFonts(builder);
                builder.withHtmlContent(html, "classpath:/templates/");
                builder.toStream(os);
                builder.run();
                return os.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("Neuspelo generisanje PDF fakture: " + e.getMessage(), e);
        } finally {
            PDF_SEMAPHORE.release();
        }
    }

    private void registerFonts(PdfRendererBuilder builder) {
        builder.useFont(() -> classpath("fonts/Inter-Regular.ttf"), "InvoiceSans", 400, FontStyle.NORMAL, true);
        builder.useFont(() -> classpath("fonts/Inter-Bold.ttf"),    "InvoiceSans", 700, FontStyle.NORMAL, true);
    }

    private static String loadImageDataUri(String path, String mimeType) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            byte[] bytes = is.readAllBytes();
            return "data:" + mimeType + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Nedostaje resurs: " + path, e);
        }
    }

    private static InputStream classpath(String path) {
        try {
            return new ClassPathResource(path).getInputStream();
        } catch (Exception e) {
            throw new RuntimeException("Nedostaje resurs: " + path, e);
        }
    }

    private static String maskVoucherCode(String code) {
        if (code == null || code.length() < 4) return code;
        int lastDash = code.lastIndexOf('-');
        return lastDash > 0 ? "ESC-••••-" + code.substring(lastDash + 1) : code;
    }
}
