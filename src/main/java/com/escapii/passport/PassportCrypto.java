package com.escapii.passport;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Šifrovanje broja pasoša u bazi (AES-256-GCM). Zapis u koloni izgleda ovako:
 * {@code v1:base64(iv || šifrat || tag)}. Sve što nema takav prefiks je stari,
 * otvoren zapis i čita se kakav jeste (broj pasoša je {@code [A-Z0-9]{5,20}}, pa
 * nikad ne počinje malim "v" niti sadrži ":").
 *
 * <p>Verzije ključa:
 * <ul>
 *   <li><b>v1</b> - ključ ugrađen u kod ({@link #BUILT_IN_V1}). Privremeno rešenje dok se na
 *       hostingu ne postavi env varijabla: štiti dump baze, backup i procureli pristup bazi,
 *       ali ne i onoga ko ima i repo.</li>
 *   <li><b>v2</b> - ključ iz {@code PASSPORT_KEY} (base64, 32 bajta: {@code openssl rand -base64 32}).
 *       Čim postoji, novi upisi idu v2, a {@link PassportStorageInitializer} na startu prešifruje
 *       stare redove. Ključ v1 ostaje u kodu da bi stari redovi bili čitljivi do tada.</li>
 * </ul>
 *
 * <p>Konverter šifruje samo kad je {@link #isEnabled()} - to uključuje initializer tek kad
 * proveri da je kolona dovoljno široka. Do tada upis ostaje otvoren tekst (ništa ne puca),
 * a čitanje uvek dešifruje ono što je šifrovano.
 */
public final class PassportCrypto {

    /** Ugrađeni ključ verzije 1 - vidi opis klase. NE menjati: postojeći redovi bi postali nečitljivi. */
    static final String BUILT_IN_V1 = "r/ZvR9j/6n4fbS+QBwN1o1hvkjRoxdZbPBRjbtJGApI=";

    public static final String V1 = "v1";
    public static final String V2 = "v2";

    private static final int IV_BYTES  = 12;
    private static final int TAG_BITS  = 128;
    private static final int KEY_BYTES = 32;
    private static final SecureRandom RNG = new SecureRandom();

    private static volatile Map<String, SecretKey> keys = Map.of(V1, key(BUILT_IN_V1));
    private static volatile String writeVersion = V1;
    private static volatile boolean enabled = false;

    private PassportCrypto() {}

    /**
     * Poziva se jednom na startu ({@link PassportKeyConfig}). Prazno = samo ugrađeni v1.
     * Neispravan ključ baca {@link IllegalArgumentException} i NE menja stanje.
     */
    public static synchronized void configure(String envKeyBase64) {
        if (envKeyBase64 == null || envKeyBase64.isBlank()) {
            keys = Map.of(V1, key(BUILT_IN_V1));
            writeVersion = V1;
            return;
        }
        SecretKey v2 = key(envKeyBase64.trim());
        keys = Map.of(V1, key(BUILT_IN_V1), V2, v2);
        writeVersion = V2;
    }

    static SecretKey key(String base64) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ključ za pasoše nije ispravan base64", e);
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalArgumentException("Ključ za pasoše mora imati 32 bajta (openssl rand -base64 32), ima " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }

    public static void setEnabled(boolean on) { enabled = on; }
    public static boolean isEnabled()         { return enabled; }
    public static String writeVersion()       { return writeVersion; }

    /** Da li je zapis iz baze šifrovan (ima prefiks {@code vN:}). */
    public static boolean isEncrypted(String stored) {
        return stored != null && stored.length() > 3
            && stored.charAt(0) == 'v' && Character.isDigit(stored.charAt(1)) && stored.indexOf(':') == 2;
    }

    public static String versionOf(String stored) {
        return isEncrypted(stored) ? stored.substring(0, 2) : null;
    }

    /** Da li je zapis već šifrovan aktuelnom verzijom (null se ne dira). */
    public static boolean isCurrent(String stored) {
        return stored == null || writeVersion.equals(versionOf(stored));
    }

    /** Da li za zapis postoji ključ (otvoren zapis uvek može). */
    public static boolean canDecrypt(String stored) {
        String v = versionOf(stored);
        return v == null || keys.containsKey(v);
    }

    /** Za konverter: šifruje kad je uključeno, inače prosleđuje otvoren tekst. */
    public static String toStored(String plain) {
        if (plain == null) return null;
        return enabled ? encrypt(plain) : plain;
    }

    public static String encrypt(String plain) {
        String version = writeVersion;
        SecretKey k = keys.get(version);
        byte[] iv = new byte[IV_BYTES];
        RNG.nextBytes(iv);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(version.getBytes(StandardCharsets.US_ASCII));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return version + ":" + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Šifrovanje broja pasoša nije uspelo", e);
        }
    }

    /**
     * Otvoren (stari) zapis vraća kakav jeste. Šifrovan zapis bez ključa, oštećen ili sa
     * pogrešnim ključem baca {@link IllegalStateException} - namerno ne vraća zamenu, da
     * kasniji upis ne bi pregazio pravi šifrat.
     */
    public static String decrypt(String stored) {
        if (!isEncrypted(stored)) return stored;
        String version = versionOf(stored);
        SecretKey k = keys.get(version);
        if (k == null) {
            throw new IllegalStateException("Broj pasoša je šifrovan verzijom " + version
                + " za koju nema ključa (PASSPORT_KEY uklonjen ili promenjen?)");
        }
        byte[] all;
        try {
            all = Base64.getDecoder().decode(stored.substring(3));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Šifrovan broj pasoša nije ispravan base64", e);
        }
        if (all.length < IV_BYTES + TAG_BITS / 8) {
            throw new IllegalStateException("Šifrovan broj pasoša je prekratak");
        }
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            c.updateAAD(version.getBytes(StandardCharsets.US_ASCII));
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Broj pasoša se ne može dešifrovati (pogrešan ključ ili oštećen zapis)", e);
        }
    }
}
