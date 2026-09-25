package com.escapii.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Kod sa vaučera stiže svakako: kopiran iz PDF-a (razmak između svakog slova), sa telefona
 * (duža crta), ručno (mala slova, bez crtica). Sve to mora da nađe istu rezervaciju ili vaučer.
 */
class GiftCodeUtilsTest {

    @Test
    void kopiranIzPdfa_razmakIzmedjuSvakogSlova() {
        assertEquals("ESC-5835C929", GiftCodeUtils.normalize("E S C - 5 8 3 5 C 9 2 9"));
        assertEquals("ESC-MVV9-KZ27-SP2H", GiftCodeUtils.normalize("E S C - M V V 9 - K Z 2 7 - S P 2 H"));
    }

    @Test
    void malaSlova_drugaCrta_nevidljiviZnakovi() {
        assertEquals("ESC-A3F8B2C1", GiftCodeUtils.normalize("esc–a3f8b2c1"));          // en dash sa telefona
        assertEquals("ESC-A3F8B2C1", GiftCodeUtils.normalize(" esc‑a3f8b2c1​")); // nbsp, non-breaking hyphen, zero-width
        assertEquals("ESC-MVV9-KZ27-SP2H", GiftCodeUtils.normalize("esc-mvv9-kz27-sp2h\n"));
    }

    @Test
    void bezCrtica_crticeSeVracajuPoObliku() {
        assertEquals("ESC-5835C929", GiftCodeUtils.normalize("ESC5835C929"));
        assertEquals("ESC-MVV9-KZ27-SP2H", GiftCodeUtils.normalize("ESCMVV9KZ27SP2H"));
        assertEquals("ESC-MVV9-KZ27-SP2H", GiftCodeUtils.normalize("ESC-MVV9KZ27-SP2H"));
    }

    @Test
    void pogresnoProcitanaNulaISlovoO() {
        assertEquals("ESC-5805C919", GiftCodeUtils.normalize("ESC-58O5C9I9"), "šifra je heksadecimalna: O→0, I→1");
        assertEquals("ESC-MVV9-KZ27-SPOH", GiftCodeUtils.normalize("ESC-MVV9-KZ27-SP0H"), "vaučer nema nulu: 0→O");
    }

    @Test
    void nepoznatOblik_ostajeKakavJeste_bezRazmakaVelikimSlovima() {
        assertEquals("ESC-NEMA", GiftCodeUtils.normalize("ESC-NEMA"));
        assertEquals("ESC-PENDING", GiftCodeUtils.normalize("  esc-pending "));
        assertEquals("ESC-5835C92", GiftCodeUtils.normalize("ESC-5835C92"), "znak manjka - ne izmišljati");
        assertEquals("SKIP3", GiftCodeUtils.normalize("skip3"), "promo kod se ne dira");
        assertEquals("", GiftCodeUtils.normalize(null));
        assertEquals("", GiftCodeUtils.normalize("   "));
    }

    @Test
    void ispravanKodOstajeIsti() {
        assertEquals("ESC-5835C929", GiftCodeUtils.normalize("ESC-5835C929"));
        assertEquals("ESC-MVV9-KZ27-SP2H", GiftCodeUtils.normalize("ESC-MVV9-KZ27-SP2H"));
    }
}
