package com.escapii.passport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Šifrat ne sme da otkrije broj, stari otvoren zapis mora da prođe, verzije ključa se smenjuju bez gubitka. */
class PassportCryptoTest {

    private static final String BROJ = "AB1234567";
    private static final String KLJUC2 = Base64.getEncoder().encodeToString(new byte[32]);

    @AfterEach
    void reset() {
        PassportCrypto.configure("");
        PassportCrypto.setEnabled(false);
    }

    @Test
    void sifrujeIDesifrujeUgradjenimKljucem() {
        String s = PassportCrypto.encrypt(BROJ);
        assertTrue(s.startsWith("v1:"), s);
        assertFalse(s.contains(BROJ), "šifrat ne sme da sadrži broj");
        assertTrue(PassportCrypto.isEncrypted(s));
        assertEquals("v1", PassportCrypto.versionOf(s));
        assertEquals(BROJ, PassportCrypto.decrypt(s));
    }

    @Test
    void istiBrojDajeRazlicitSifrat_slucajanIv() {
        assertNotEquals(PassportCrypto.encrypt(BROJ), PassportCrypto.encrypt(BROJ));
    }

    @Test
    void stariOtvorenZapisProlaziKakavJeste() {
        assertEquals(BROJ, PassportCrypto.decrypt(BROJ));
        assertNull(PassportCrypto.decrypt(null));
        assertFalse(PassportCrypto.isEncrypted(BROJ));
        assertFalse(PassportCrypto.isCurrent(BROJ), "otvoren zapis treba prešifrovati");
        assertTrue(PassportCrypto.isCurrent(null));
        assertTrue(PassportCrypto.canDecrypt(BROJ));
    }

    @Test
    void konverterNeSifrujeDokKolonaNijeSpremna() {
        assertEquals(BROJ, PassportCrypto.toStored(BROJ));
        PassportCrypto.setEnabled(true);
        assertTrue(PassportCrypto.toStored(BROJ).startsWith("v1:"));
        assertNull(PassportCrypto.toStored(null));
    }

    @Test
    void envKljucPrebacujeNaV2_aV1OstajeCitljiv() {
        String v1 = PassportCrypto.encrypt(BROJ);

        PassportCrypto.configure(KLJUC2);
        assertEquals("v2", PassportCrypto.writeVersion());
        String v2 = PassportCrypto.encrypt(BROJ);
        assertTrue(v2.startsWith("v2:"), v2);
        assertEquals(BROJ, PassportCrypto.decrypt(v2));
        assertEquals(BROJ, PassportCrypto.decrypt(v1), "v1 ostaje čitljiv dok se ne prešifruje");
        assertFalse(PassportCrypto.isCurrent(v1));
        assertTrue(PassportCrypto.isCurrent(v2));

        // ključ uklonjen: v2 redovi nemaju ključ - greška, ne tiha zamena
        PassportCrypto.configure("");
        assertFalse(PassportCrypto.canDecrypt(v2));
        assertThrows(IllegalStateException.class, () -> PassportCrypto.decrypt(v2));
        assertEquals(BROJ, PassportCrypto.decrypt(v1));
    }

    @Test
    void neispravanEnvKljucSeOdbijaBezPromeneStanja() {
        assertThrows(IllegalArgumentException.class, () -> PassportCrypto.configure("nije-base64!!"));
        assertThrows(IllegalArgumentException.class,
            () -> PassportCrypto.configure(Base64.getEncoder().encodeToString(new byte[16])));
        assertEquals("v1", PassportCrypto.writeVersion());
    }

    @Test
    void ostecenSifratNeProlaziTiho() {
        String s = PassportCrypto.encrypt(BROJ);
        char last = s.charAt(s.length() - 1);
        String pokvaren = s.substring(0, s.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertThrows(IllegalStateException.class, () -> PassportCrypto.decrypt(pokvaren));
        assertThrows(IllegalStateException.class, () -> PassportCrypto.decrypt("v1:kratko"));
    }
}
