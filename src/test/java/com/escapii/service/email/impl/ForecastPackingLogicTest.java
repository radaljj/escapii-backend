package com.escapii.service.email.impl;

import com.escapii.service.weather.DailyForecast;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Savet za pakovanje mora da bude LOGIČAN za svaku prognozu, a kombinacija ima previše da bi
 * ih iko pregledao ručno. Zato ovaj test prolazi kroz sve kombinacije temperatura, noći i
 * padavina za putovanja od 1 do 4 dana i na svakom dobijenom tekstu proverava pravila:
 *
 * <ul>
 *   <li>savet važi za SVAKI dan puta - hladan dan uvek donosi jaknu, topao laganu garderobu;</li>
 *   <li>sneg se pominje samo kad postoji stvarno hladan dan sa snegom, nikad uz vrućinu;</li>
 *   <li>zimska oprema se ne pominje kad su svi dani topli, ni jakna kad je svuda leto;</li>
 *   <li>broj kišnih dana u tekstu je tačan, a snežan dan se ne broji i kao kišni;</li>
 *   <li>dve spojene rečenice ne kažu ni isto ni suprotno, i padež uz svaki broj je ispravan.</li>
 * </ul>
 *
 * Očekivane činjenice test računa sam, nezavisno od koda koji proverava.
 */
class ForecastPackingLogicTest {

    private static final LocalDate DEP = LocalDate.of(2026, 10, 9);

    /** Dnevni maksimumi: obe strane svake granice pojasa (5/6, 12/13, 18/19, 24/25, 29/30) i krajnosti. */
    private static final int[] TEMP = {-8, -3, 0, 2, 5, 6, 9, 10, 12, 13, 16, 18, 19, 22, 24, 25, 27, 29, 30, 34, 39};
    /** Koliko je noć hladnija od dana. */
    private static final int[] NOC_NIZE = {2, 6, 11, 16, 23};
    /** Vrste padavina - videti {@link #dani}. */
    private static final int PADAVINA = 8;

    private static final Set<Integer> KOD_SNEGA = Set.of(71, 73, 75, 77, 85, 86);
    private static final Set<Integer> KOD_KISE  = Set.of(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99);

    private static final Pattern STEPENI = Pattern.compile("(-?\\d+) (stepen[ai]?)(?![a-zšđčćž])");
    private static final Pattern NOCU    = Pattern.compile("Noću pada na oko (-?\\d+) ");

    /** Tekstovi kojima je oblik (interpunkcija, padeži, ponavljanja) već proveren - zavisi samo od teksta. */
    private final Set<String> oblikProveren = new HashSet<>();

    @Test
    void svakaKombinacijaDajeLogicanSavet() throws Exception {
        int provereno = 0;
        Set<String> razlicitihTekstova = new HashSet<>();
        for (int n = 1; n <= 4; n++) {
            // za četiri dana svaka druga temperatura - i tako je preko sto hiljada kombinacija
            int[] temp = n < 4 ? TEMP : svakaDruga(TEMP);
            int[] idx = new int[n];
            do {
                for (int noc : NOC_NIZE) {
                    for (int p = 0; p < PADAVINA; p++) {
                        List<DailyForecast> dani = dani(temp, idx, noc, p);
                        String s = ForecastEmailServiceImpl.packingHint(dani, DEP, DEP.plusDays(n - 1));
                        proveri(dani, s);
                        razlicitihTekstova.add(s);
                        provereno++;
                    }
                }
            } while (sledeca(idx, temp.length));
        }
        assertTrue(provereno > 300_000, "provereno samo " + provereno + " kombinacija");
        assertTrue(razlicitihTekstova.size() > 1_000, "premalo različitih tekstova: " + razlicitihTekstova.size());

        // Za pregled očima: svaki oblik rečenice jednom, sa # umesto brojeva.
        Set<String> oblici = new TreeSet<>();
        for (String s : razlicitihTekstova) oblici.add(s.replaceAll("-?\\d+", "#"));
        Path izlaz = Path.of("target/email-preview");
        Files.createDirectories(izlaz);
        Files.writeString(izlaz.resolve("saveti-za-pakovanje-oblici.txt"),
                "Kombinacija: " + provereno + ", različitih tekstova: " + razlicitihTekstova.size()
                + ", oblika: " + oblici.size() + "\n\n" + String.join("\n\n", oblici) + "\n", StandardCharsets.UTF_8);
        System.out.println("[ForecastPackingLogicTest] kombinacija: " + provereno
                + ", različitih tekstova: " + razlicitihTekstova.size() + ", oblika: " + oblici.size());
    }

    /**
     * Pravila moraju da UHVATE greške zbog kojih postoje - inače bi test prolazio i kad ne proverava
     * ništa. Ovo su upravo primeri koje kupac ne sme da dobije.
     */
    @Test
    void pravilaHvatajuPogresanSavet() {
        // topao savet po proseku, a jedan dan ima 5 stepeni
        assertThrows(AssertionError.class, () -> proveri(
                List.of(dan(0, 0, 30, 18, 0), dan(1, 0, 30, 17, 0), dan(2, 3, 5, 1, 0)),
                "Prijatno je, oko 22 stepena preko dana - majice i lagane pantalone, a za jutro i veče dobro dođe tanka jakna ili duks."));
        // sneg uz vrućinu
        assertThrows(AssertionError.class, () -> proveri(
                List.of(dan(0, 0, 32, 22, 0), dan(1, 71, 32, 22, 3), dan(2, 0, 32, 22, 0)),
                "Preko dana je vrelo, oko 32 stepena - lagana garderoba, naočare za sunce i krema su obavezne, a flašica vode uvek pri ruci."
                + " Ima i snega u najavi - obuj nešto što ne klizi i spakuj tople čarape."));
        // „sasvim dovoljna" pa „nešto toplije"
        assertThrows(AssertionError.class, () -> proveri(
                List.of(dan(0, 0, 27, 9, 0), dan(1, 0, 27, 9, 0)),
                "Toplo je, oko 27 stepeni preko dana - lagana letnja garderoba je sasvim dovoljna, uz jednu majicu dugih rukava za veče."
                + " Noću pada na oko 9 stepeni, pa ponesi i nešto toplije za veče."));
        // zimska oprema usred leta
        assertThrows(AssertionError.class, () -> proveri(
                List.of(dan(0, 0, 28, 18, 0), dan(1, 0, 27, 17, 0)),
                "Hladno je, oko 28 stepeni preko dana - topla jakna, šal i zatvorena obuća."));
        // hladan dan bez jakne
        assertThrows(AssertionError.class, () -> proveri(
                List.of(dan(0, 0, 8, 2, 0), dan(1, 0, 9, 3, 0)),
                "Hladno je, oko 9 stepeni preko dana - majica i šal."));
        // pogrešan broj kišnih dana i pogrešan padež
        assertThrows(AssertionError.class, () -> proveri(
                List.of(dan(0, 61, 21, 12, 4), dan(1, 61, 21, 12, 4), dan(2, 0, 21, 12, 0)),
                "Prijatno je, oko 21 stepen preko dana - majice i lagane pantalone, a za jutro i veče dobro dođe tanka jakna ili duks."
                + " Jedan dan je najavljena kiša, pa ubaci i kišobran."));
        assertThrows(AssertionError.class, () -> proveri(
                List.of(dan(0, 0, 21, 12, 0)),
                "Prijatno je, oko 21 stepeni preko dana - majice i lagane pantalone, a za jutro i veče dobro dođe tanka jakna ili duks."));
        // ispravan savet prolazi
        assertDoesNotThrow(() -> proveri(
                List.of(dan(0, 0, 21, 12, 0)),
                "Prijatno je, oko 21 stepen preko dana - majice i lagane pantalone, a za jutro i veče dobro dođe tanka jakna ili duks."));
    }

    private static DailyForecast dan(int plus, int kod, int max, int min, double mm) {
        return new DailyForecast(DEP.plusDays(plus), kod, max, min, mm);
    }

    // ── pravila ───────────────────────────────────────────────────────────────

    private void proveri(List<DailyForecast> dani, String s) {
        String opis = "\n  dani: " + dani + "\n  tekst: " + s + "\n";
        int n = dani.size();
        int najtopliji  = dani.stream().mapToInt(DailyForecast::maxTemp).max().orElseThrow();
        int najhladniji = dani.stream().mapToInt(DailyForecast::maxTemp).min().orElseThrow();
        int noc         = dani.stream().mapToInt(DailyForecast::minTemp).min().orElseThrow();
        int prosek      = (int) Math.round(dani.stream().mapToInt(DailyForecast::maxTemp).average().orElseThrow());
        boolean hladanSneg = dani.stream().anyMatch(d ->
                KOD_SNEGA.contains(d.weatherCode()) && d.maxTemp() <= 10 && d.minTemp() <= 3);
        long kisnih = dani.stream().filter(d -> !KOD_SNEGA.contains(d.weatherCode())
                && (d.precipitation() >= 1.0 || KOD_KISE.contains(d.weatherCode()))).count();
        boolean raspon = najtopliji - najhladniji >= 8 && pojas(najtopliji) != pojas(najhladniji);

        // 1, 2 i 9 zavise samo od teksta, pa se rade jednom po tekstu
        if (oblikProveren.add(s)) proveriOblik(s, opis);

        // 3. početak odgovara podacima: prosek kad su dani slični, oba kraja kad nisu
        if (raspon) {
            assertTrue(s.startsWith("Dani se dosta razlikuju: najtopliji ima oko " + najtopliji
                    + ", a najhladniji oko " + ForecastEmailServiceImpl.stepeni(najhladniji) + " preko dana. Ponesi "),
                    "raspon nije naveden" + opis);
        } else {
            assertTrue(s.startsWith(pocetak(pojas(prosek)) + " oko " + ForecastEmailServiceImpl.stepeni(prosek))
                    || s.startsWith(pocetak(pojas(prosek)) + ", oko " + ForecastEmailServiceImpl.stepeni(prosek)),
                    "pojas ne odgovara proseku " + prosek + opis);
        }

        // 4. savet važi za svaki dan puta
        if (najhladniji <= 12) {
            assertTrue(s.contains("jakn"), "dan od " + najhladniji + " stepeni bez jakne" + opis);
            assertFalse(s.startsWith("Preko dana je vrelo") || s.startsWith("Toplo je") || s.startsWith("Prijatno je"),
                    "topao savet, a jedan dan ima " + najhladniji + opis);
        }
        if (najhladniji <= 5) {
            assertTrue(s.contains("topla jakna") || s.contains("toplu jaknu"),
                    "dan od " + najhladniji + " stepeni bez tople jakne" + opis);
        }
        if (najtopliji >= 25) {
            assertTrue(s.contains("lagan"), "dan od " + najtopliji + " stepeni bez lagane garderobe" + opis);
            assertFalse(s.startsWith("Sveže je") || s.startsWith("Hladno je") || s.startsWith("Zimski uslovi"),
                    "hladan savet, a jedan dan ima " + najtopliji + opis);
        }
        if (prosek >= 30 || (raspon && najtopliji >= 30)) {
            assertTrue(s.contains("naočare za sunce"), "vreli dani bez zaštite od sunca" + opis);
        }

        // 5. bez zimske opreme kad je svuda toplo, bez jakne kad je svuda leto
        if (najhladniji >= 19) {
            assertFalse(s.contains("šal") || s.contains("rukavic") || s.contains("kapa") || s.contains("kapu")
                    || s.contains("topla jakna") || s.contains("toplu jaknu") || s.contains("čarape"),
                    "zimska oprema, a najhladniji dan ima " + najhladniji + opis);
        }
        if (najhladniji >= 25 && noc > 10) {
            assertFalse(s.contains("jakn") || s.contains("duks"), "jakna usred leta" + opis);
        }

        // 6. sneg samo uz stvarno hladan dan sa snegom
        assertEquals(hladanSneg, s.contains("sneg"), "sneg" + opis);
        if (s.contains("sneg")) {
            assertTrue(najhladniji <= 10, "sneg, a najhladniji dan ima " + najhladniji + opis);
            assertTrue(s.contains("jakn"), "sneg bez jakne" + opis);
        }

        // 7. kiša: tačan broj dana
        if (kisnih == 0) {
            assertFalse(s.toLowerCase(Locale.ROOT).contains("kiš"), "kiša bez kišnog dana" + opis);
            assertFalse(s.contains("kabanic"), "kabanica bez kišnog dana" + opis);
        } else if (kisnih == 1) {
            assertTrue(s.endsWith(" Jedan dan je najavljena kiša, pa ubaci i kišobran."), "jedan kišni dan" + opis);
        } else if (kisnih == n) {
            assertTrue(s.endsWith(" Kiša se očekuje svakog dana, pa ponesi kišobran ili kabanicu."), "kiša svakog dana" + opis);
        } else {
            assertTrue(s.endsWith(" Kiša se očekuje " + kisnih + " od " + n + " dana, pa ponesi kišobran ili kabanicu."),
                    kisnih + " kišnih dana od " + n + opis);
        }

        // 8. noć: pominje se samo kad je stvarno hladna, i brojem koji jeste najhladnija noć
        Matcher nocu = NOCU.matcher(s);
        if (nocu.find()) {
            assertEquals(noc, Integer.parseInt(nocu.group(1)), "pogrešna noćna temperatura" + opis);
            assertTrue(noc <= 10, "noć od " + noc + " stepeni nije hladna" + opis);
        }
        if (s.contains("ispod nule")) {
            assertTrue(noc < 0, "ispod nule, a najhladnija noć ima " + noc + opis);
        }

    }

    /** Provere koje zavise samo od teksta: oblik, padeži, protivrečnost i ponavljanje. */
    private static void proveriOblik(String s, String opis) {
        // 1. oblik
        assertFalse(s.isEmpty(), "prazan savet" + opis);
        assertTrue(s.endsWith("."), "ne završava tačkom" + opis);
        assertFalse(s.contains("  ") || s.contains(" .") || s.contains(" ,") || s.contains("..") || s.contains(",,"),
                "razmak ili interpunkcija" + opis);
        String[] recenice = s.split("(?<=\\.) ");
        for (String recenica : recenice) {
            assertTrue(Character.isUpperCase(recenica.charAt(0)), "rečenica ne počinje velikim slovom: " + recenica + opis);
        }
        assertTrue(recenice.length <= 5, "više od pet rečenica" + opis);

        // 2. padež uz svaki broj
        Matcher m = STEPENI.matcher(s);
        boolean imaStepene = false;
        while (m.find()) {
            imaStepene = true;
            assertEquals(ForecastEmailServiceImpl.stepeni(Integer.parseInt(m.group(1))), m.group(1) + " " + m.group(2),
                    "pogrešan padež" + opis);
        }
        assertTrue(imaStepene, "savet bez ijedne temperature" + opis);

        // 9. rečenice ne kažu ni suprotno ni isto
        assertFalse(s.contains("sasvim dovoljna") && (s.contains("toplije") || s.contains("jakn")),
                "„sasvim dovoljna“ pa nešto toplije" + opis);
        for (String fraza : new String[]{"za veče", "ne propušta", "preko dana", "obuć", "rukavice", "u najavi"}) {
            assertTrue(broj(s, fraza) <= 1, "„" + fraza + "“ više puta" + opis);
        }
        assertTrue(broj(s, "topla jakna") + broj(s, "toplu jaknu") <= 1, "topla jakna više puta" + opis);
        assertTrue(broj(s.toLowerCase(Locale.ROOT), "ponesi") <= 2, "„ponesi“ više od dva puta" + opis);
        String ponovljeno = ponovljenNiz(s, 3);
        assertNull(ponovljeno, "isti niz od tri reči dva puta: „" + ponovljeno + "“" + opis);
    }

    // ── pomoćno ───────────────────────────────────────────────────────────────

    /**
     * Padavine: 0 suvo · 1 kiša prvog dana · 2 kiša svakog dana · 3 kod snega prvog dana ·
     * 4 sitne padavine (0,7 mm) bez koda kiše · 5 ledena kiša prvog dana · 6 sneg prvog i kiša poslednjeg dana ·
     * 7 kiša prva dva dana.
     */
    private static List<DailyForecast> dani(int[] temp, int[] idx, int nocNize, int padavine) {
        List<DailyForecast> dani = new ArrayList<>();
        for (int i = 0; i < idx.length; i++) {
            int max = temp[idx[i]];
            int kod = 0;
            double mm = 0;
            boolean prvi = i == 0, poslednji = i == idx.length - 1;
            switch (padavine) {
                case 1 -> { if (prvi) { kod = 61; mm = 4; } }
                case 2 -> { kod = 63; mm = 6; }
                case 3 -> { if (prvi) { kod = 71; mm = 3; } }
                case 4 -> { if (prvi) { kod = 3; mm = 0.7; } }
                case 5 -> { if (prvi) { kod = 66; mm = 0.4; } }
                case 6 -> { if (prvi) { kod = 73; mm = 3; } else if (poslednji) { kod = 80; mm = 5; } }
                case 7 -> { if (i < 2) { kod = 81; mm = 2; } }
                default -> { }
            }
            dani.add(new DailyForecast(DEP.plusDays(i), kod, max, max - nocNize, mm));
        }
        return dani;
    }

    /** Sledeća kombinacija indeksa (brojač u bazi {@code osnova}); false kad su sve prošle. */
    private static boolean sledeca(int[] idx, int osnova) {
        for (int i = idx.length - 1; i >= 0; i--) {
            if (++idx[i] < osnova) return true;
            idx[i] = 0;
        }
        return false;
    }

    private static int[] svakaDruga(int[] a) {
        int[] r = new int[(a.length + 1) / 2];
        for (int i = 0; i < r.length; i++) r[i] = a[i * 2];
        return r;
    }

    /** Pojas po istim granicama kao u kodu, ali napisan ovde nezavisno: 0 vrelo … 5 zima. */
    private static int pojas(int max) {
        if (max >= 30) return 0;
        if (max >= 25) return 1;
        if (max >= 19) return 2;
        if (max >= 13) return 3;
        if (max >= 6)  return 4;
        return 5;
    }

    private static String pocetak(int pojas) {
        return switch (pojas) {
            case 0 -> "Preko dana je vrelo";
            case 1 -> "Toplo je";
            case 2 -> "Prijatno je";
            case 3 -> "Sveže je";
            case 4 -> "Hladno je";
            default -> "Zimski uslovi";
        };
    }

    private static int broj(String s, String fraza) {
        int n = 0;
        for (int i = s.indexOf(fraza); i >= 0; i = s.indexOf(fraza, i + fraza.length())) n++;
        return n;
    }

    /** Prvi niz od {@code duzina} uzastopnih reči koji se u tekstu javlja dva puta; null ako takvog nema. */
    private static String ponovljenNiz(String s, int duzina) {
        String[] reci = s.toLowerCase(Locale.ROOT).replaceAll("[^a-zšđčćž0-9 -]", " ").trim().split("\\s+");
        Set<String> vidjeno = new HashSet<>();
        for (int i = 0; i + duzina <= reci.length; i++) {
            String niz = String.join(" ", List.of(reci).subList(i, i + duzina));
            if (!vidjeno.add(niz)) return niz;
        }
        return null;
    }
}
