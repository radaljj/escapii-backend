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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.escapii.util.LogUtils;
import lombok.extern.slf4j.Slf4j;
import java.util.logging.Level;

/**
 * Generiše PDF vaučere (boarding-pass dizajn) iz Thymeleaf šablona pomoću
 * openhtmltopdf-a. Dva vaučera, jedan list:
 * <ul>
 *   <li>{@link #generate(VoucherData)} - novčani poklon vaučer, {@code templates/gift-voucher.html}</li>
 *   <li>{@link #generateTrip(TripVoucherData)} - vaučer poklonjenog putovanja, {@code templates/gift-trip-voucher.html}</li>
 * </ul>
 *
 * <p>Fontovi (src/main/resources/fonts, vidi DOWNLOAD_FONTS.md): openhtmltopdf
 * renderuje SAMO fontove koje ovde registrujemo, sistemskih nema. Familije u
 * CSS-u šablona moraju da se poklope sa {@link #registerFonts}:
 * {@code VoucherSerif} (Cormorant Garamond), {@code VoucherSans} (Manrope),
 * {@code VoucherMono} (JetBrains Mono) - isti trio kao boarding pass na /hvala.
 */
@Slf4j
@Lazy   // inicijalizuje se tek pri prvom PDF pozivu, ne blokira startup ako openhtmltopdf ima problem
@Service
public class VoucherPdfService {

    /** Srpski zapis datuma - sa tačkom na kraju, kao na profakturi i na sajtu. */
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd.MM.yyyy.");
    private static final DateTimeFormatter SHORT_FMT = DateTimeFormatter.ofPattern("dd.MM.");

    /**
     * Semafor: maksimalno 3 PDF-a simultano.
     * openhtmltopdf je memorijski zahtevan (~50-80 MB po generisanju),
     * pa ograničavamo paralelizam da ne potrošimo svu RAM na VPS-u.
     * Ostali zahtevi čekaju u redu - ne odbijaju se.
     */
    private static final Semaphore PDF_SEMAPHORE = new Semaphore(3, true);

    private final TemplateEngine templateEngine;
    private final QrCodeGenerator qrCodeGenerator;

    /** Bazni URL na koji vodi QR kod; kod se dodaje kao ?code=... Isti za oba vaučera. */
    @Value("${escapii.voucher.redeem-url:https://escapii.rs/poklon}")
    private String redeemBaseUrl;

    /** Javna kontakt adresa u podnožju vaučera - ista koju kupac vidi u mejlovima. */
    @Value("${app.contact-email:info@escapii.rs}")
    private String contactEmail;

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

    /** Novčani poklon vaučer kao byte[] (pogodno za prilog mejlu). */
    public byte[] generate(VoucherData data) {
        Context ctx = new Context(new Locale("sr"));
        ctx.setVariable("amount",          data.amount());
        ctx.setVariable("amountWords",     amountInWords(data.amount()));
        ctx.setVariable("voucherCode",     safe(data.voucherCode()));
        ctx.setVariable("issuedAt",        data.issuedAt().format(DATE_FMT));
        ctx.setVariable("expiresAt",       data.expiresAt().format(DATE_FMT));
        ctx.setVariable("buyerName",       safe(data.buyerName()));
        ctx.setVariable("personalMessage", wrapLongWords(data.personalMessage()));
        return render("gift-voucher", data.voucherCode(), ctx);
    }

    /**
     * Vaučer poklonjenog putovanja kao byte[]. Kupac ga štampa ili prosleđuje
     * obdarenom; QR i kod vode na istu /poklon stranicu kao novčani vaučer.
     */
    public byte[] generateTrip(TripVoucherData data) {
        Context ctx = new Context(new Locale("sr"));
        ctx.setVariable("code",           safe(data.code()));
        ctx.setVariable("departureDate",  data.departureDate().format(DATE_FMT));
        ctx.setVariable("returnDate",     data.returnDate().format(DATE_FMT));
        ctx.setVariable("departureShort", data.departureDate().format(SHORT_FMT));
        ctx.setVariable("nights",         data.nights());
        ctx.setVariable("travelers",      data.travelers());
        ctx.setVariable("airportCode",    safe(data.airportCode()));
        ctx.setVariable("airportCity",    safe(data.airportCity()));
        ctx.setVariable("airportName",    safe(data.airportName()));
        ctx.setVariable("passengersHtml", passengersHtml(data.passengers()));
        ctx.setVariable("giftMessage",    wrapLongWords(safe(data.giftMessage())));
        // Duga poruka ili vise od tri putnika: "gusto" sabija razmake i naslov da list
        // ostane JEDAN. Uobicajen slucaj (2 putnika, recenica-dve) zadrzava siri raspored.
        ctx.setVariable("gusto", safe(data.giftMessage()).length() > 110
                || (data.passengers() != null && data.passengers().size() > 3));
        return render("gift-trip-voucher", data.code(), ctx);
    }

    /**
     * Zajednički deo: QR, logo, kontakt, šablon → HTML → PDF, pod semaforom.
     * Semafor se traži PRE try/finally, da neuspelo čekanje ne oslobodi tuđi permit.
     */
    private byte[] render(String template, String code, Context ctx) {
        try {
            // Čekaj max 10 minuta - u normalnim uslovima PDF traje 2-5 sekundi,
            // pa je 10 minuta čekanja signal da je nešto pošlo po krivu
            boolean acquired = PDF_SEMAPHORE.tryAcquire(10, TimeUnit.MINUTES);
            if (!acquired) {
                log.error("[PDF] Timeout čekanja na semafor za vaučer kod={} - server je prezauzet",
                        LogUtils.maskVoucherCode(code));
                throw new RuntimeException("PDF generisanje nije moglo da počne - server prezauzet");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("PDF generisanje prekinuto dok je čekalo na semafor", e);
        }
        try {
            // QR kod -> PNG data URI; link vodi na redeem stranicu sa kodom
            String redeemUrl = redeemBaseUrl + "?code=" + urlEncode(safe(code));
            ctx.setVariable("qrDataUri",    qrCodeGenerator.pngDataUri(redeemUrl, 480)); // hi-res za štampu
            ctx.setVariable("logoDataUri",  loadImageDataUri("static/images/logo-black.png", "image/png"));
            ctx.setVariable("contactEmail", contactEmail);

            String html = templateEngine.process(template, ctx);

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
            throw new RuntimeException("Neuspelo generisanje PDF vaučera (" + template + "): " + e.getMessage(), e);
        } finally {
            PDF_SEMAPHORE.release(); // uvek oslobodi permit, čak i ako je došlo do greške
        }
    }

    /** Registruje fontove - familije, težine i stilovi moraju da prate CSS šablona. */
    private void registerFonts(PdfRendererBuilder builder) {
        // VoucherSerif = Cormorant Garamond (naslovi, IATA kodovi, veliki brojevi)
        builder.useFont(() -> classpath("fonts/CormorantGaramond-Regular.ttf"),    "VoucherSerif", 400, FontStyle.NORMAL, true);
        builder.useFont(() -> classpath("fonts/CormorantGaramond-Italic.ttf"),     "VoucherSerif", 400, FontStyle.ITALIC, true);
        builder.useFont(() -> classpath("fonts/CormorantGaramond-Bold.ttf"),       "VoucherSerif", 700, FontStyle.NORMAL, true);
        builder.useFont(() -> classpath("fonts/CormorantGaramond-BoldItalic.ttf"), "VoucherSerif", 700, FontStyle.ITALIC, true);
        // VoucherSans = Manrope (tekst, natpisi) - u CSS-u koristiti tačno 400 / 600 / 800
        builder.useFont(() -> classpath("fonts/Manrope-Regular.ttf"),   "VoucherSans", 400, FontStyle.NORMAL, true);
        builder.useFont(() -> classpath("fonts/Manrope-SemiBold.ttf"),  "VoucherSans", 600, FontStyle.NORMAL, true);
        builder.useFont(() -> classpath("fonts/Manrope-ExtraBold.ttf"), "VoucherSans", 800, FontStyle.NORMAL, true);
        // VoucherMono = JetBrains Mono (kod vaučera, datumi u meta polju)
        builder.useFont(() -> classpath("fonts/JetBrainsMono-Bold.ttf"), "VoucherMono", 700, FontStyle.NORMAL, true);
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
     * Imena putnika razdvojena tačkom u boji; imena su escape-ovana ovde jer
     * šablon ovaj deo ubacuje kao HTML ({@code th:utext}).
     */
    static String passengersHtml(List<String> passengers) {
        if (passengers == null || passengers.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String p : passengers) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append("<span>&#183;</span>");
            sb.append(escapeHtml(p.trim()));
        }
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                default  -> sb.append(c);
            }
        }
        return sb.toString();
    }

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
     * Iznos u rečima na srpskom - za podnaslov na vaučeru.
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
