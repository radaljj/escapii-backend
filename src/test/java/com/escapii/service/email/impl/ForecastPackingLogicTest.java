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
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Savet za pakovanje mora da bude LOGIČAN za svaku prognozu, a kombinacija ima previše da bi
 * ih iko pregledao ručno. Zato ovaj test pravi prognoze na tri načina - sve kombinacije za
 * putovanja od 1 do 4 dana, nasumične prognoze do 15 dana i putovanja koja prognoza pokriva
 * samo delom - i na svakom dobijenom tekstu proverava pravila:
 *
 * <ul>
 *   <li>savet važi za SVAKI dan puta: dan do 24 stepena donosi bar duks, do 12 jaknu, do 5
 *       toplu jaknu, dan od 25 laganu garderobu, a noć u minusu se ne prećutkuje;</li>
 *   <li>rečenica po pojasu („Toplo je", „Hladno je") stoji samo kad su joj svi dani blizu;</li>
 *   <li>sneg se pominje samo uz hladan dan, a padavine na mrazu se ne zovu kiša;</li>
 *   <li>zimska oprema se ne pominje kad su svi dani topli, ni jakna kad je svuda leto;</li>
 *   <li>broj kišnih dana u tekstu je tačan, i ne tvrdi se ništa o danima bez prognoze;</li>
 *   <li>dve spojene rečenice ne kažu ni isto ni suprotno, i padež uz svaki broj je ispravan.</li>
 * </ul>
 *
 * Pravila su napisana po danima puta, ne po granama koda koji proveravaju.
 */
class ForecastPackingLogicTest {

    private static final LocalDate DEP = LocalDate.of(2026, 10, 9);

    /** Dnevni maksimumi: obe strane svake granice pojasa (5/6, 12/13, 18/19, 24/25, 29/30) i krajnosti. */
    private static final int[] TEMP = {-8, -3, 0, 1, 2, 5, 6, 9, 12, 13, 16, 18, 19, 22, 24, 25, 27, 29, 30, 34, 39};
    /** Koliko je noć hladnija od dana. */
    private static final int[] NOC_NIZE = {2, 6, 11, 16, 23};
    /** Vrste padavina - videti {@link #dani}. */
    private static final int PADAVINA = 9;

    private static final Set<Integer> KOD_SNEGA = Set.of(71, 73, 75, 77, 85, 86);
    private static final Set<Integer> KOD_KISE  = Set.of(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99);
    private static final int[] SVI_KODOVI = {0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67,
            71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99, 42};

    private static final Pattern STEPENI = Pattern.compile("(-?\\d+) (stepen[ai]?)(?![a-zšđčćž])");
    private static final Pattern NOCU    = Pattern.compile("Noću pada na oko (-?\\d+) ");
    private static final Pattern RASPON  = Pattern.compile(
            "^Dani se dosta razlikuju: najtopliji ima oko (-?\\d+), a najhladniji oko (-?\\d+) stepen[ai]? preko dana\\. Ponesi ");
    private static final Pattern POJAS   = Pattern.compile(
            "^(Preko dana je vrelo|Toplo je|Prijatno je|Sveže je|Hladno je|Zimski uslovi), (?:oko (-?\\d+)|od (-?\\d+) do (-?\\d+)) stepen[ai]?");

    /** Tekstovi kojima je oblik (interpunkcija, padeži, ponavljanja) već proveren - zavisi samo od teksta. */
    private final Set<String> oblikProveren = new HashSet<>();
    private final Set<String> tekstovi = new HashSet<>();
    private int provereno;

    // ── tri izvora prognoza ───────────────────────────────────────────────────

    @Test
    void sveKombinacijeDoCetiriDana() throws Exception {
        for (int n = 1; n <= 4; n++) {
            // za četiri dana svaka druga temperatura - i tako je preko sto hiljada kombinacija
            int[] temp = n < 4 ? TEMP : svakaDruga(TEMP);
            int[] idx = new int[n];
            do {
                for (int noc : NOC_NIZE) {
                    for (int p = 0; p < PADAVINA; p++) {
                        savetIProvera(dani(temp, idx, noc, p), n);
                    }
                }
            } while (sledeca(idx, temp.length));
        }
        assertTrue(provereno > 500_000, "provereno samo " + provereno + " kombinacija");
        assertTrue(tekstovi.size() > 1_000, "premalo različitih tekstova: " + tekstovi.size());
        ispisiOblike("saveti-za-pakovanje-oblici.txt");
    }

    /** Do 15 dana, svaki dan sa svojom noći, kodom i padavinama - i besmislene prognoze (sneg na 30, kiša na -8). */
    @Test
    void nasumicnePrognozeDoPetnaestDana() {
        Random r = new Random(20260928);
        for (int i = 0; i < 250_000; i++) {
            int n = 1 + r.nextInt(15);
            int osnova = -10 + r.nextInt(50);
            int razlika = r.nextInt(3) == 0 ? r.nextInt(22) : r.nextInt(6);   // najčešće slični dani, ponekad veliki skok
            List<DailyForecast> dani = new ArrayList<>();
            for (int d = 0; d < n; d++) {
                int max = osnova + (razlika == 0 ? 0 : r.nextInt(2 * razlika + 1) - razlika);
                int min = max - 1 - r.nextInt(20);
                int kod = r.nextInt(3) == 0 ? SVI_KODOVI[r.nextInt(SVI_KODOVI.length)] : r.nextInt(4);
                double mm = r.nextInt(3) == 0 ? Math.round(r.nextDouble() * 150) / 10.0 : 0;
                dani.add(new DailyForecast(DEP.plusDays(d), kod, max, min, mm));
            }
            savetIProvera(dani, n);
        }
        assertTrue(tekstovi.size() > 5_000, "premalo različitih tekstova: " + tekstovi.size());
    }

    /** Prognoza pokriva samo prve dane puta (dugo putovanje ili ručno slanje dve nedelje unapred). */
    @Test
    void putKojiPrognozaPokrivaSamoDelom() {
        Random r = new Random(28092026);
        for (int i = 0; i < 60_000; i++) {
            int pokriveno = 1 + r.nextInt(9);
            int danaPuta = pokriveno + 1 + r.nextInt(6);
            int osnova = -5 + r.nextInt(40);
            List<DailyForecast> dani = new ArrayList<>();
            for (int d = 0; d < pokriveno; d++) {
                int max = osnova + r.nextInt(7) - 3;
                int kod = r.nextInt(2) == 0 ? SVI_KODOVI[r.nextInt(SVI_KODOVI.length)] : r.nextInt(4);
                double mm = r.nextInt(2) == 0 ? Math.round(r.nextDouble() * 100) / 10.0 : 0;
                dani.add(new DailyForecast(DEP.plusDays(d), kod, max, max - 2 - r.nextInt(12), mm));
            }
            savetIProvera(dani, danaPuta);
        }
    }

    /**
     * Pravila moraju da UHVATE greške zbog kojih postoje - inače bi test prolazio i kad ne proverava
     * ništa. Ovo su upravo saveti koje kupac ne sme da dobije.
     */
    @Test
    void pravilaHvatajuPogresanSavet() {
        String prijatno = "Prijatno je, oko 22 stepena preko dana - majice i lagane pantalone, a za jutro i veče dobro dođe tanka jakna ili duks.";
        // topao savet po proseku, a jedan dan ima 5 stepeni
        pada(List.of(dan(0, 0, 30, 18, 0), dan(1, 0, 30, 17, 0), dan(2, 3, 5, 1, 0)), prijatno);
        // sneg uz vrućinu
        pada(List.of(dan(0, 0, 32, 22, 0), dan(1, 71, 32, 22, 3), dan(2, 0, 32, 22, 0)),
                "Preko dana je vrelo, oko 32 stepena - lagana garderoba, naočare za sunce i krema su obavezne, a flašica vode uvek pri ruci."
                + " Ima i snega u najavi - obuj nešto što ne klizi i spakuj tople čarape.");
        // „sasvim dovoljna" pa „nešto toplije"
        pada(List.of(dan(0, 0, 27, 9, 0), dan(1, 0, 27, 9, 0)),
                "Toplo je, oko 27 stepeni preko dana - lagana letnja garderoba je sasvim dovoljna, uz jednu majicu dugih rukava za veče."
                + " Noću pada na oko 9 stepeni, pa ponesi i nešto toplije za veče.");
        // zimska oprema usred leta
        pada(List.of(dan(0, 0, 28, 18, 0), dan(1, 0, 27, 17, 0)),
                "Hladno je, oko 28 stepeni preko dana - topla jakna, šal i zatvorena obuća.");
        // hladan dan bez jakne
        pada(List.of(dan(0, 0, 8, 2, 0), dan(1, 0, 9, 3, 0)), "Hladno je, oko 9 stepeni preko dana - majica i šal.");
        // dan od 19 stepeni sa noći od 11, bez ičeg dugih rukava (nalaz nezavisnog pregleda)
        pada(List.of(dan(0, 0, 28, 16, 0), dan(1, 3, 20, 12, 0), dan(2, 3, 19, 11, 0)),
                "Dani se dosta razlikuju: najtopliji ima oko 28, a najhladniji oko 19 stepeni preko dana."
                + " Ponesi laganu letnju garderobu za tople dane, a majice i lagane pantalone za prijatnije.");
        // padavine na mrazu nazvane kišom
        pada(List.of(dan(0, 3, -1, -6, 2.0), dan(1, 3, 0, -5, 0), dan(2, 2, 1, -4, 0)),
                "Zimski uslovi, oko 0 stepeni preko dana - topla jakna, kapa, rukavice i obuća koja ne propušta."
                + " Jedan dan je najavljena kiša, pa ubaci i kišobran.");
        // dan sa padavinama koji savet prećuti
        pada(List.of(dan(0, 85, 13, 4, 6.0), dan(1, 3, 14, 5, 0), dan(2, 2, 14, 6, 0)),
                "Sveže je, oko 14 stepeni preko dana - jakna ti treba, a najsigurnije je da se oblačiš u slojevima.");
        // pogrešan broj kišnih dana, pogrešan padež, noć u minusu koja se ne pominje
        pada(List.of(dan(0, 61, 21, 12, 4), dan(1, 61, 21, 12, 4), dan(2, 0, 21, 12, 0)),
                "Prijatno je, oko 21 stepen preko dana - majice i lagane pantalone, a za jutro i veče dobro dođe tanka jakna ili duks."
                + " Jedan dan je najavljena kiša, pa ubaci i kišobran.");
        pada(List.of(dan(0, 0, 21, 12, 0)),
                "Prijatno je, oko 21 stepeni preko dana - majice i lagane pantalone, a za jutro i veče dobro dođe tanka jakna ili duks.");
        pada(List.of(dan(0, 0, 15, -4, 0)),
                "Sveže je, oko 15 stepeni preko dana - jakna ti treba, a najsigurnije je da se oblačiš u slojevima.");
        // „svakog dana", a prognoza pokriva dva od četiri dana puta
        assertThrows(AssertionError.class, () -> proveri(
                List.of(dan(0, 61, 15, 8, 5), dan(1, 63, 14, 7, 8)), 4,
                "Sveže je, oko 15 stepeni preko dana - jakna ti treba, a najsigurnije je da se oblačiš u slojevima."
                + " Kiša se očekuje svakog dana, pa ponesi kišobran ili kabanicu."));
        // ispravan savet prolazi
        assertDoesNotThrow(() -> proveri(List.of(dan(0, 0, 21, 12, 0)), 1,
                "Prijatno je, oko 21 stepen preko dana - majice i lagane pantalone, a za jutro i veče dobro dođe tanka jakna ili duks."));
    }

    private void pada(List<DailyForecast> dani, String pogresanSavet) {
        assertThrows(AssertionError.class, () -> proveri(dani, dani.size(), pogresanSavet),
                "pravila su propustila pogrešan savet: " + pogresanSavet);
    }

    // ── pravila ───────────────────────────────────────────────────────────────

    private void savetIProvera(List<DailyForecast> dani, int danaPuta) {
        String s = ForecastEmailServiceImpl.packingHint(dani, DEP, DEP.plusDays(danaPuta - 1));
        proveri(dani, danaPuta, s);
        tekstovi.add(s);
        provereno++;
    }

    private void proveri(List<DailyForecast> dani, int danaPuta, String s) {
        String opis = "\n  dana puta: " + danaPuta + "\n  prognoza: " + dani + "\n  tekst: " + s + "\n";
        int n = dani.size();
        int najtopliji  = dani.stream().mapToInt(DailyForecast::maxTemp).max().orElseThrow();
        int najhladniji = dani.stream().mapToInt(DailyForecast::maxTemp).min().orElseThrow();
        int noc         = dani.stream().mapToInt(DailyForecast::minTemp).min().orElseThrow();

        // oblik, padeži i ponavljanja zavise samo od teksta, pa se rade jednom po tekstu
        if (oblikProveren.add(s)) proveriOblik(s, opis);

        // 1. savet važi za svaki dan puta
        for (DailyForecast d : dani) {
            String dan = "dan " + d.maxTemp() + "/" + d.minTemp();
            if (d.maxTemp() <= 24) assertTrue(s.contains("jakn") || s.contains("duks"), dan + " bez jakne i duksa" + opis);
            if (d.maxTemp() <= 12) assertTrue(s.contains("jakn"), dan + " bez jakne" + opis);
            if (d.maxTemp() <= 5)  assertTrue(s.contains("topla jakna") || s.contains("toplu jaknu"), dan + " bez tople jakne" + opis);
            if (d.maxTemp() >= 25) assertTrue(s.contains("lagan"), dan + " bez lagane garderobe" + opis);
            if (d.maxTemp() >= 19 && d.minTemp() <= 10) {
                assertTrue(s.contains("jakn") || s.contains("duks") || s.contains("toplije"), dan + ": hladna noć se ne pominje" + opis);
            }
            if (d.minTemp() < 0) {
                assertTrue(s.contains("ispod nule") || s.contains("Noću pada na oko -") || s.contains("rukavic"),
                        dan + ": noć u minusu se ne pominje" + opis);
            }
        }

        // 2. početak odgovara danima: rečenica po pojasu samo kad su joj svi dani blizu
        Matcher raspon = RASPON.matcher(s);
        Matcher pojas = POJAS.matcher(s);
        if (raspon.find()) {
            assertEquals(najtopliji, Integer.parseInt(raspon.group(1)), "najtopliji dan" + opis);
            assertEquals(najhladniji, Integer.parseInt(raspon.group(2)), "najhladniji dan" + opis);
            assertTrue(najtopliji - najhladniji >= 6, "„dosta se razlikuju“ za " + (najtopliji - najhladniji) + " stepeni razlike" + opis);
            assertNotEquals(pojas(najtopliji), pojas(najhladniji), "raspon u istom pojasu" + opis);
        } else {
            assertTrue(pojas.find(), "savet ne počinje ni pojasom ni rasponom" + opis);
            int p = pojasPoImenu(pojas.group(1));
            if (pojas.group(2) != null) {
                int broj = Integer.parseInt(pojas.group(2));
                assertEquals(p, pojas(broj), "ime pojasa ne odgovara broju " + broj + opis);
                assertTrue(najhladniji <= broj && broj <= najtopliji, "broj " + broj + " van dana puta" + opis);
                assertTrue(najtopliji - najhladniji < 8, "„oko " + broj + "“ za dane od " + najhladniji + " do " + najtopliji + opis);
            } else {
                assertEquals(najhladniji, Integer.parseInt(pojas.group(3)), "donja granica" + opis);
                assertEquals(najtopliji, Integer.parseInt(pojas.group(4)), "gornja granica" + opis);
            }
            assertTrue(Math.abs(pojas(najtopliji) - p) <= 1 && Math.abs(pojas(najhladniji) - p) <= 1,
                    "rečenica pojasa, a dan je dva pojasa dalje (" + najhladniji + " do " + najtopliji + ")" + opis);
        }

        // 3. bez zimske opreme kad je svuda toplo, bez jakne kad je svuda leto
        if (najhladniji >= 19) {
            assertFalse(s.contains("šal") || s.contains("rukavic") || s.contains("kapa") || s.contains("kapu")
                    || s.contains("topla jakna") || s.contains("toplu jaknu") || s.contains("čarape") || s.contains("sneg"),
                    "zimska oprema, a najhladniji dan ima " + najhladniji + opis);
        }
        if (najhladniji >= 25 && noc > 10) {
            assertFalse(s.contains("jakn") || s.contains("duks"), "jakna usred leta" + opis);
        }
        if (najtopliji <= 18) {
            assertFalse(s.contains("lagan") || s.contains("naočare") || s.contains("majic"),
                    "letnja garderoba, a najtopliji dan ima " + najtopliji + opis);
        }

        // 4. sneg samo uz hladan dan; padavine na mrazu su sneg, ne kiša
        boolean hladanSneg = dani.stream().anyMatch(ForecastPackingLogicTest::snegZaSavet);
        assertEquals(hladanSneg, s.contains("sneg"), "sneg" + opis);
        assertEquals(hladanSneg, s.contains("čarape"), "čarape idu samo uz sneg" + opis);

        // 5. kiša: svaki dan sa padavinama se vidi u savetu, broj dana je tačan, o danima bez prognoze ni reči
        List<DailyForecast> mokri = dani.stream().filter(d -> !snegZaSavet(d) && padavine(d)).toList();
        boolean samoKisa = mokri.stream().noneMatch(d -> KOD_SNEGA.contains(d.weatherCode()));
        String mala = s.toLowerCase(Locale.ROOT);
        if (mokri.isEmpty()) {
            assertFalse(mala.contains("kiš") || mala.contains("padavin") || mala.contains("kabanic"), "kiša bez kišnog dana" + opis);
        } else {
            assertEquals(samoKisa, !mala.contains("padavin"), "kiša ili padavine" + opis);
            assertEquals(samoKisa, s.contains("kiša") || s.contains("Kiša"), "kiša ili padavine" + opis);
            String kraj = s.substring(s.lastIndexOf(". ") + 2);
            if (n < danaPuta) {
                assertTrue(kraj.startsWith("U prognozi "), "put je pokriven samo delom" + opis);
                assertFalse(kraj.contains("svakog dana") || kraj.contains("oba dana") || kraj.contains(" od "), "tvrdnja o danima bez prognoze" + opis);
            } else if (mokri.size() == 1) {
                assertTrue(kraj.startsWith("Jedan dan "), "jedan dan sa padavinama" + opis);
            } else if (mokri.size() < n) {
                assertTrue(kraj.contains(" " + mokri.size() + " od " + n + " dana, "), mokri.size() + " od " + n + opis);
            } else {
                assertTrue(kraj.contains(n == 2 ? " oba dana, " : " svakog dana, "), "svi dani" + opis);
            }
            assertTrue(kraj.contains("kišobran"), "padavine bez kišobrana" + opis);
        }

        // 6. noć: brojem koji jeste najhladnija noć, i samo kad je stvarno hladna
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
        assertFalse(s.isEmpty(), "prazan savet" + opis);
        assertTrue(s.endsWith("."), "ne završava tačkom" + opis);
        assertFalse(s.contains("  ") || s.contains(" .") || s.contains(" ,") || s.contains("..") || s.contains(",,"),
                "razmak ili interpunkcija" + opis);
        String[] recenice = s.split("(?<=\\.) ");
        for (String recenica : recenice) {
            assertTrue(Character.isUpperCase(recenica.charAt(0)), "rečenica ne počinje velikim slovom: " + recenica + opis);
        }
        assertTrue(recenice.length <= 5, "više od pet rečenica" + opis);
        assertTrue(s.length() <= 460, "predugačak savet: " + s.length() + " znakova" + opis);

        Matcher m = STEPENI.matcher(s);
        boolean imaStepene = false;
        while (m.find()) {
            imaStepene = true;
            assertEquals(ForecastEmailServiceImpl.stepeni(Integer.parseInt(m.group(1))), m.group(1) + " " + m.group(2),
                    "pogrešan padež" + opis);
        }
        assertTrue(imaStepene, "savet bez ijedne temperature" + opis);

        assertFalse(s.contains("sasvim dovoljna") && (s.contains("toplije") || s.contains("jakn") || s.contains("duks")),
                "„sasvim dovoljna“ pa nešto toplije" + opis);
        for (String fraza : new String[]{"za veče", "ne propušta", "preko dana", "obuć", "rukavice", "u najavi", "kišobran"}) {
            assertTrue(broj(s, fraza) <= 1, "„" + fraza + "“ više puta" + opis);
        }
        assertTrue(broj(s, "topla jakna") + broj(s, "toplu jaknu") <= 1, "topla jakna više puta" + opis);
        assertTrue(broj(s.toLowerCase(Locale.ROOT), "ponesi") <= 2, "„ponesi“ više od dva puta" + opis);
        String ponovljeno = ponovljenNiz(s, 3);
        assertNull(ponovljeno, "isti niz od tri reči dva puta: „" + ponovljeno + "“" + opis);
    }

    // ── činjenice o danu, napisane nezavisno od koda ─────────────────────────

    /** Sneg zbog kog se pakuje drugačije: kod snega uz dan do 12 stepeni, ili padavine na mrazu. */
    private static boolean snegZaSavet(DailyForecast d) {
        boolean kodSnega = KOD_SNEGA.contains(d.weatherCode());
        return (kodSnega && d.maxTemp() <= 12) || (d.maxTemp() <= 1 && padavine(d));
    }

    /** Dan sa padavinama koje savet mora da pomene: bar 1 mm, ili kod kiše. */
    private static boolean padavine(DailyForecast d) {
        return d.precipitation() >= 1.0 || KOD_KISE.contains(d.weatherCode());
    }

    /** Pojas po granicama iz dogovora sa Markom: 0 vrelo (30+), 1 toplo, 2 prijatno, 3 sveže, 4 hladno, 5 zima (do 5). */
    private static int pojas(int max) {
        if (max >= 30) return 0;
        if (max >= 25) return 1;
        if (max >= 19) return 2;
        if (max >= 13) return 3;
        if (max >= 6)  return 4;
        return 5;
    }

    private static int pojasPoImenu(String pocetak) {
        return switch (pocetak) {
            case "Preko dana je vrelo" -> 0;
            case "Toplo je" -> 1;
            case "Prijatno je" -> 2;
            case "Sveže je" -> 3;
            case "Hladno je" -> 4;
            default -> 5;
        };
    }

    // ── pomoćno ───────────────────────────────────────────────────────────────

    private static DailyForecast dan(int plus, int kod, int max, int min, double mm) {
        return new DailyForecast(DEP.plusDays(plus), kod, max, min, mm);
    }

    /**
     * Padavine: 0 suvo · 1 kiša prvog dana · 2 kiša svakog dana · 3 sneg prvog dana ·
     * 4 sitne padavine (0,7 mm) bez koda kiše · 5 ledena kiša prvog dana · 6 sneg prvog i kiša poslednjeg dana ·
     * 7 kiša prva dva dana · 8 grmljavina prvog i 2 mm uz vedro nebo poslednjeg dana.
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
                case 8 -> { if (prvi) { kod = 95; mm = 12; } else if (poslednji) { kod = 0; mm = 2; } }
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

    /** Za pregled očima: svaki oblik rečenice jednom, sa # umesto brojeva. */
    private void ispisiOblike(String ime) throws Exception {
        Set<String> oblici = new TreeSet<>();
        for (String s : tekstovi) oblici.add(s.replaceAll("-?\\d+", "#"));
        Path izlaz = Path.of("target/email-preview");
        Files.createDirectories(izlaz);
        Files.writeString(izlaz.resolve(ime),
                "Kombinacija: " + provereno + ", različitih tekstova: " + tekstovi.size()
                + ", oblika: " + oblici.size() + "\n\n" + String.join("\n\n", oblici) + "\n", StandardCharsets.UTF_8);
        System.out.println("[ForecastPackingLogicTest] kombinacija: " + provereno
                + ", različitih tekstova: " + tekstovi.size() + ", oblika: " + oblici.size());
    }
}
