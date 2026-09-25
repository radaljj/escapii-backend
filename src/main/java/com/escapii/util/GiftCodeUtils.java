package com.escapii.util;

import java.util.Locale;

/**
 * Kod poklona onako kako ga korisnik unese → oblik u kom stoji u bazi.
 *
 * <p>Dva oblika: šifra rezervacije {@code ESC-XXXXXXXX} (poklonjeno putovanje, heksadecimalno)
 * i novčani vaučer {@code ESC-XXXX-XXXX-XXXX}. Kopiranje sa PDF vaučera ume da ubaci razmak
 * između svakog slova ("E S C - 5 8 3 5 C 9 2 9"), telefon zameni crticu dužom crtom, a ruka
 * ukuca mala slova ili izostavi crtice. Zato se prvo zadrže samo slova i cifre, pa se crtice
 * vrate po obliku. Nepoznat oblik ostaje kakav jeste (bez razmaka, velikim slovima) i pada na
 * "nije validan". Ista pravila ima {@code normalizujKod()} na /poklon stranici.
 */
public final class GiftCodeUtils {

    private GiftCodeUtils() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (s.startsWith("ESC")) {
            String telo = s.substring(3);
            if (telo.length() == 8) {
                // šifra rezervacije je heksadecimalna: O i I su sigurno pogrešno pročitani 0 i 1
                return "ESC-" + telo.replace('O', '0').replace('I', '1');
            }
            if (telo.length() == 12) {
                // azbuka vaučera nema 0 (liči na O) - isto pravilo kao polje za vaučer na rezervaciji
                telo = telo.replace('0', 'O');
                return "ESC-" + telo.substring(0, 4) + "-" + telo.substring(4, 8) + "-" + telo.substring(8);
            }
        }
        return raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
