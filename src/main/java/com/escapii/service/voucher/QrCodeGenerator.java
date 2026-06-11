package com.escapii.service.voucher;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

/**
 * Generiše QR kod kao PNG data URI, spreman za {@code <img src>} u HTML/PDF-u.
 * Koristi ZXing. Boje su usklađene sa brendom (tamna #1a1410 na beloj).
 */
@Component
public class QrCodeGenerator {

    // Brand boje QR koda
    private static final int DARK  = 0xFF0F2D35; // ARGB - tamnoplava Escapii
    private static final int LIGHT = 0xFFFFFFFF;

    /**
     * @param content tekst/URL koji QR sadrži
     * @param sizePx  širina/visina u pikselima (480 = oštro za štampu)
     * @return {@code "data:image/png;base64,...."}
     */
    public String pngDataUri(String content, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 1,
                EncodeHintType.CHARACTER_SET, "UTF-8"
            );
            BitMatrix matrix = new MultiFormatWriter()
                .encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            MatrixToImageConfig config = new MatrixToImageConfig(DARK, LIGHT);
            BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix, config);

            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                ImageIO.write(img, "PNG", os);
                String b64 = Base64.getEncoder().encodeToString(os.toByteArray());
                return "data:image/png;base64," + b64;
            }
        } catch (Exception e) {
            throw new RuntimeException("Neuspelo generisanje QR koda: " + e.getMessage(), e);
        }
    }
}
