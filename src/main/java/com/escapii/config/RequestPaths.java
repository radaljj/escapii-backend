package com.escapii.config;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Putanja zahteva u obliku u kome je filteri smeju porediti.
 *
 * <p><b>Zasto ovo postoji.</b> {@code request.getRequestURI()} vraca putanju
 * NEDEKODIRANU, a Spring rutira po DEKODIRANOJ. Zbog te razlike je
 * {@code /api/%61dmin/bookings} ({@code %61} je slovo "a") prolazio pored
 * {@link AdminKeyFilter}, jer sirova putanja ne pocinje sa "/api/admin/", ali ga
 * je DispatcherServlet svejedno rutirao na AdminController. Rezultat je bio
 * neautentikovan pristup svim rezervacijama sa licnim podacima kupaca i
 * dodeljenim destinacijama. Isti trik je obarao i sve rate limite.
 *
 * <p><b>Pravilo:</b> nijedan filter ne sme da poredi {@code getRequestURI()}
 * direktno. Poredi se iskljucivo rezultat ove klase.
 *
 * <p>Poredi se i dekodirana i sirova putanja, a pogodak bilo koje je dovoljan da
 * se zastita primeni. Namerno asimetricno: greska na strani "primeni filter" je
 * bezazlena (zahtev samo trazi kljuc), greska na strani "preskoci filter" je
 * upravo ova rupa.
 */
public final class RequestPaths {

    private RequestPaths() {}

    /**
     * Dekodirana putanja zahteva, ili sirova ako dekodiranje pukne.
     * Dekodira se jednom - tacno onoliko puta koliko dekodira i Spring pri
     * rutiranju, pa filter i ruter uvek vide istu stvar. Dvostruko kodiranje
     * ({@code %2561}) se time ne razresi u "admin", ali se ne razresi ni kod
     * rutera, pa takav zahtev zavrsi kao 404 umesto da pogodi kontroler.
     */
    public static String decodedPath(HttpServletRequest request) {
        String raw = request.getRequestURI();
        if (raw == null) return "";
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Neispravna procentna sekvenca - vrati sirovo. Zahtev ce ionako pasti
            // dalje u lancu, a ovde ne smemo da bacimo izuzetak.
            return raw;
        }
    }

    /**
     * Da li putanja pocinje datim prefiksom, gledano i dekodirano i sirovo.
     * Koristi se za odluku "da li se zastita primenjuje na ovaj zahtev".
     */
    public static boolean startsWith(HttpServletRequest request, String prefix) {
        String raw = request.getRequestURI();
        return decodedPath(request).startsWith(prefix)
                || (raw != null && raw.startsWith(prefix));
    }

    /** Kao {@link #startsWith}, samo za kraj putanje. */
    public static boolean endsWith(HttpServletRequest request, String suffix) {
        String raw = request.getRequestURI();
        return decodedPath(request).endsWith(suffix)
                || (raw != null && raw.endsWith(suffix));
    }

    /** Tacno poklapanje putanje, gledano i dekodirano i sirovo. */
    public static boolean equals(HttpServletRequest request, String path) {
        String raw = request.getRequestURI();
        return decodedPath(request).equals(path)
                || (raw != null && raw.equals(path));
    }

    /** Sadrzi li putanja dati isecak, gledano i dekodirano i sirovo. */
    public static boolean contains(HttpServletRequest request, String fragment) {
        String raw = request.getRequestURI();
        return decodedPath(request).contains(fragment)
                || (raw != null && raw.contains(fragment));
    }
}
