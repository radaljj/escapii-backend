package com.escapii.service.voucher;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Base64;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Generiše PDF poklon vaučera (boarding-pass dizajn) iz Thymeleaf template-a
 * pomoću openhtmltopdf-a.
 *
 * Resursi (src/main/resources):
 *   templates/gift-voucher.html
 *   fonts/PlayfairDisplay-Regular.ttf
 *   fonts/PlayfairDisplay-Bold.ttf
 *   fonts/PlayfairDisplay-Italic.ttf
 *   fonts/Inter-Regular.ttf
 *   fonts/Inter-Bold.ttf
 *
 * Napomena: openhtmltopdf renderuje SAMO fontove koje ovde registrujemo.
 * Familije 'GiftSerif' (Playfair Display) i 'GiftSans' (Inter) moraju
 * da se poklope sa CSS-om u template-u.
 */
@Lazy   // inicijalizuje se tek pri prvom PDF pozivu, ne blokira startup ako openhtmltopdf ima problem
@Service
public class VoucherPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final TemplateEngine templateEngine;
    private final QrCodeGenerator qrCodeGenerator;

    /** Bazni URL na koji vodi QR kod; vaučer kod se dodaje kao ?code=... */
    @Value("${escapii.voucher.redeem-url:https://escapii.rs/poklon}")
    private String redeemBaseUrl;

    public VoucherPdfService(QrCodeGenerator qrCodeGenerator) {
        this.qrCodeGenerator = qrCodeGenerator;

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

    /**
     * Glavni ulaz — generiše PDF kao byte[] (pogodno za prilog mejlu).
     */
    public byte[] generate(VoucherData data) {
        try {
            // 1) QR kod -> PNG data URI; link vodi na redeem stranicu sa kodom
            String redeemUrl = redeemBaseUrl + "?code=" + urlEncode(data.voucherCode());
            String qrDataUri = qrCodeGenerator.pngDataUri(redeemUrl, 480); // hi-res za štampu

            // 2) Logo -> PNG data URI (logo-black na svetloj pozadini)
            String logoDataUri = loadImageDataUri("static/images/logo-black.png", "image/png");

            // 3) Thymeleaf kontekst
            Context ctx = new Context(new Locale("sr"));
            ctx.setVariable("amount",          data.amount());
            ctx.setVariable("amountFormatted", data.amount() + " €");
            ctx.setVariable("amountWords",     amountInWords(data.amount()));
            ctx.setVariable("voucherCode",     safe(data.voucherCode()));
            ctx.setVariable("issuedAt",        data.issuedAt().format(DATE_FMT));
            ctx.setVariable("expiresAt",       data.expiresAt().format(DATE_FMT));
            ctx.setVariable("buyerName",       safe(data.buyerName()));
            ctx.setVariable("personalMessage", wrapLongWords(data.personalMessage())); // sigurno prelamanje
            ctx.setVariable("qrDataUri",       qrDataUri);
            ctx.setVariable("logoDataUri",     logoDataUri);

            // 4) Render HTML
            String html = templateEngine.process("gift-voucher", ctx);

            // 5) HTML -> PDF
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
            throw new RuntimeException("Neuspelo generisanje PDF vaučera: " + e.getMessage(), e);
        }
    }

    /** Registruje fontove — familije i weight/style moraju da prate CSS template-a. */
    private void registerFonts(PdfRendererBuilder builder) {
        // GiftSerif = Playfair Display
        builder.useFont(() -> classpath("fonts/PlayfairDisplay-Regular.ttf"), "GiftSerif", 400, FontStyle.NORMAL, true);
        builder.useFont(() -> classpath("fonts/PlayfairDisplay-Bold.ttf"),    "GiftSerif", 700, FontStyle.NORMAL, true);
        builder.useFont(() -> classpath("fonts/PlayfairDisplay-Italic.ttf"),  "GiftSerif", 400, FontStyle.ITALIC, true);
        // GiftSans = Inter
        builder.useFont(() -> classpath("fonts/Inter-Regular.ttf"), "GiftSans", 400, FontStyle.NORMAL, true);
        builder.useFont(() -> classpath("fonts/Inter-Bold.ttf"),    "GiftSans", 700, FontStyle.NORMAL, true);
    }

    /** Učitava sliku sa classpath-a i vraća je kao base64 data URI za inline ugrađivanje u HTML/PDF. */
    private static String loadImageDataUri(String path, String mimeType) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            byte[] bytes = is.readAllBytes();
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Nedostaje resurs na classpath-u: " + path, e);
        }
    }

    private static InputStream classpath(String path) {
        try {
            return new ClassPathResource(path).getInputStream();
        } catch (Exception e) {
            throw new RuntimeException("Nedostaje resurs na classpath-u: " + path, e);
        }
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /**
     * Ubacuje razmak svakih MAX_WORD_LEN karaktera unutar "reči" bez razmaka,
     * jer openhtmltopdf ne podržava word-break:break-word za sasvim duge tokene.
     */
    private static final int MAX_WORD_LEN = 30;
    static String wrapLongWords(String msg) {
        if (msg == null || msg.isEmpty()) return msg;
        String[] words = msg.split("(?<=\\s)|(?=\\s)");
        StringBuilder sb = new StringBuilder(msg.length() + 8);
        for (String w : words) {
            if (w.length() > MAX_WORD_LEN) {
                for (int i = 0; i < w.length(); i++) {
                    sb.append(w.charAt(i));
                    if ((i + 1) % MAX_WORD_LEN == 0 && (i + 1) < w.length()) sb.append(' ');
                }
            } else {
                sb.append(w);
            }
        }
        return sb.toString();
    }

    /**
     * Iznos u rečima na srpskom — za podnaslov na vaučeru.
     * Pokriva tipične iznose; za ostale vraća "{n} evra".
     */
    static String amountInWords(int amount) {
        return switch (amount) {
            case 50   -> "pedeset evra";
            case 100  -> "sto evra";
            case 150  -> "sto pedeset evra";
            case 200  -> "dvesta evra";
            case 250  -> "dvesta pedeset evra";
            case 300  -> "trista evra";
            case 400  -> "četiristo evra";
            case 500  -> "petsto evra";
            case 600  -> "šeststo evra";
            case 750  -> "sedamsto pedeset evra";
            case 1000 -> "hiljadu evra";
            default   -> amount + " evra";
        };
    }
}
