package com.escapii.service.email.impl;

import com.escapii.service.weather.DailyForecast;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Savet za pakovanje, rečenicu po rečenicu: svaki tekst koji kupac može da dobije ovde stoji
 * DOSLOVNO, pa izmena teksta u kodu ne prolazi neprimećeno. Logiku (da savet važi za svaki dan
 * puta, na svim kombinacijama) čuva ForecastPackingLogicTest.
 */
class ForecastPackingHintTest {

    private static final LocalDate DEP = LocalDate.of(2026, 10, 9);
    private static final LocalDate RET = LocalDate.of(2026, 10, 12);

    private static DailyForecast dan(int plus, int code, int max, int min, double mm) {
        return new DailyForecast(DEP.plusDays(plus), code, max, min, mm);
    }

    /** Savet za put koji traje tačno koliko ima dana u prognozi (od polaska do poslednjeg navedenog dana). */
    private static String savet(DailyForecast... dani) {
        LocalDate povratak = DEP;
        for (DailyForecast d : dani) if (d.date().isAfter(povratak)) povratak = d.date();
        return ForecastEmailServiceImpl.packingHint(List.of(dani), DEP, povratak);
    }

    // ── dani su slični: jedna rečenica po pojasu ─────────────────────────────

    @Test
    void vrelo() {
        assertEquals("Preko dana je vrelo, oko 32 stepena - lagana garderoba, naočare za sunce i krema su obavezne, "
                + "a flašica vode uvek pri ruci.",
                savet(dan(0, 0, 33, 22, 0), dan(1, 0, 31, 21, 0), dan(2, 1, 32, 23, 0)));
    }

    @Test
    void vreloSaHladnomNoci() {
        assertEquals("Preko dana je vrelo, oko 32 stepena - lagana garderoba, naočare za sunce i krema su obavezne, "
                + "a flašica vode uvek pri ruci. Noću pada na oko 9 stepeni, pa ponesi i nešto toplije za veče.",
                savet(dan(0, 0, 33, 9, 0), dan(1, 0, 31, 10, 0), dan(2, 1, 32, 11, 0)));
    }

    @Test
    void toplo_nemaJakne_iDanasnjeVremeNeUtice() {
        String s = savet(dan(-7, 3, 8, 2, 0),            // danas je hladno - ne sme da utiče
                dan(0, 0, 26, 16, 0), dan(1, 1, 27, 17, 0), dan(2, 0, 25, 16, 0), dan(3, 2, 26, 17, 0));
        assertEquals("Toplo je, oko 26 stepeni preko dana - lagana letnja garderoba je sasvim dovoljna, "
                + "uz jednu majicu dugih rukava za veče.", s);
    }

    /** Ranije: „sasvim dovoljna" pa odmah „ponesi i nešto toplije", i „za veče" dvaput. */
    @Test
    void toploSaHladnomNoci_neProtivreciSebi() {
        assertEquals("Toplo je, oko 27 stepeni preko dana - za dan je dovoljna lagana letnja garderoba. "
                + "Noću pada na oko 9 stepeni, pa za veče ponesi duks ili tanku jaknu.",
                savet(dan(0, 0, 27, 9, 0), dan(1, 0, 26, 10, 0), dan(2, 1, 27, 11, 0)));
    }

    @Test
    void prijatno() {
        assertEquals("Prijatno je, oko 21 stepen preko dana - majice i lagane pantalone, "
                + "a za jutro i veče dobro dođe tanka jakna ili duks.",
                savet(dan(0, 2, 21, 12, 0), dan(1, 1, 22, 13, 0), dan(2, 0, 20, 12, 0)));
    }

    /** Ranije: jakna za veče u prvoj rečenici, pa opet „nešto toplije za veče" u drugoj. */
    @Test
    void prijatnoSaHladnimNocimaIJednomKisom() {
        assertEquals("Prijatno je, oko 22 stepena preko dana - majice i lagane pantalone. "
                + "Noću pada na oko 9 stepeni, pa ti za jutro i veče treba jakna. "
                + "Jedan dan je najavljena kiša, pa ubaci i kišobran.",
                savet(dan(0, 2, 22, 9, 0), dan(1, 61, 20, 10, 4.2), dan(2, 1, 23, 11, 0), dan(3, 0, 23, 12, 0)));
    }

    @Test
    void sveze() {
        assertEquals("Sveže je, oko 15 stepeni preko dana - jakna ti treba, a najsigurnije je da se oblačiš u slojevima.",
                savet(dan(0, 3, 15, 7, 0), dan(1, 2, 16, 8, 0), dan(2, 3, 14, 6, 0)));
    }

    @Test
    void svezeSaMrazomNocu() {
        assertEquals("Sveže je, oko 15 stepeni preko dana - jakna ti treba, a najsigurnije je da se oblačiš u slojevima. "
                + "Noću pada na oko -2 stepena, pa za veče ponesi i nešto toplije.",
                savet(dan(0, 0, 15, 1, 0), dan(1, 0, 14, -2, 0), dan(2, 1, 16, 2, 0)));
    }

    @Test
    void hladnoSaKisomSvakogDana() {
        assertEquals("Hladno je, oko 8 stepeni preko dana - topla jakna, šal i zatvorena obuća. "
                + "Kiša se očekuje svakog dana, pa ponesi kišobran ili kabanicu.",
                savet(dan(0, 61, 9, 4, 6), dan(1, 63, 8, 3, 9), dan(2, 80, 8, 4, 3), dan(3, 61, 7, 2, 5)));
    }

    @Test
    void hladnoSaNocimaIspodNule() {
        assertEquals("Hladno je, oko 8 stepeni preko dana - topla jakna, šal i zatvorena obuća. "
                + "Noću je ispod nule, pa ponesi i kapu i rukavice.",
                savet(dan(0, 0, 8, -3, 0), dan(1, 0, 9, -1, 0), dan(2, 1, 7, -2, 0)));
    }

    @Test
    void zimaSaSnegomINocimaIspodNule() {
        assertEquals("Zimski uslovi, oko 3 stepena preko dana, a noću ispod nule - topla jakna, kapa, rukavice "
                + "i obuća koja ne propušta. Ima i snega u najavi - obuj nešto što ne klizi i spakuj tople čarape.",
                savet(dan(0, 3, 4, -2, 0), dan(1, 71, 1, -4, 3), dan(2, 3, 3, -3, 0), dan(3, 1, 5, -1, 0)));
    }

    /** Kad je i dan u minusu, „a noću ispod nule" je suvišno. */
    @Test
    void zimaKadJeIDanUMinusu() {
        assertEquals("Zimski uslovi, oko -3 stepena preko dana - topla jakna, kapa, rukavice i obuća koja ne propušta.",
                savet(dan(0, 3, -2, -8, 0), dan(1, 3, -3, -9, 0), dan(2, 3, -4, -10, 0), dan(3, 3, -3, -7, 0)));
    }

    /** Ranije: „obuća koja ne propušta" pa „jakna koja ne propušta" u dve spojene rečenice. */
    @Test
    void zimaSaKisom_bezPonavljanja() {
        assertEquals("Zimski uslovi, oko 5 stepeni preko dana - topla jakna, kapa, rukavice i obuća koja ne propušta. "
                + "Kiša se očekuje 2 od 4 dana, pa ponesi kišobran ili kabanicu.",
                savet(dan(0, 61, 5, 1, 4), dan(1, 63, 4, 0, 6), dan(2, 3, 4, 1, 0), dan(3, 3, 5, 2, 0)));
    }

    // ── dani se mnogo razlikuju: oba kraja, ne prosek ─────────────────────────

    /** Prosek bi rekao „prijatno, oko 22", a jedan dan ima 5 stepeni. */
    @Test
    void vreliDaniIJedanHladan_nePisePoProseku() {
        assertEquals("Dani se dosta razlikuju: najtopliji ima oko 30, a najhladniji oko 5 stepeni preko dana. "
                + "Ponesi laganu garderobu, naočare za sunce i kremu za vrele dane, a toplu jaknu, kapu i rukavice za najhladnije.",
                savet(dan(0, 0, 30, 18, 0), dan(1, 0, 30, 17, 0), dan(2, 3, 5, 1, 0)));
    }

    @Test
    void prijatnoPaSveze() {
        assertEquals("Dani se dosta razlikuju: najtopliji ima oko 24, a najhladniji oko 15 stepeni preko dana. "
                + "Ponesi majice i lagane pantalone za toplije dane, a jaknu ili duks za svežije.",
                savet(dan(0, 0, 24, 14, 0), dan(1, 1, 22, 13, 0), dan(2, 3, 16, 9, 0), dan(3, 3, 15, 8, 0)));
    }

    @Test
    void toploPaPrijatno_saHladnomNoci() {
        assertEquals("Dani se dosta razlikuju: najtopliji ima oko 29, a najhladniji oko 21 stepen preko dana. "
                + "Ponesi laganu letnju garderobu za tople dane, a majice i lagane pantalone za prijatnije. "
                + "Noću pada na oko 9 stepeni, pa za veče dobro dođe i nešto toplije.",
                savet(dan(0, 0, 29, 10, 0), dan(1, 1, 21, 9, 0)));
    }

    @Test
    void vreloPaToplo_sveJeLetnje() {
        assertEquals("Dani se dosta razlikuju: najtopliji ima oko 35, a najhladniji oko 27 stepeni preko dana. "
                + "Ponesi laganu letnju garderobu za sve dane, uz naočare za sunce i kremu.",
                savet(dan(0, 0, 35, 24, 0), dan(1, 1, 27, 20, 0)));
    }

    @Test
    void svezePaHladno_saMrazomNocu() {
        assertEquals("Dani se dosta razlikuju: najtopliji ima oko 18, a najhladniji oko 9 stepeni preko dana. "
                + "Ponesi duks ili tanju jaknu za toplije dane, a toplu jaknu i zatvorenu obuću za hladne. "
                + "Noću je ispod nule, pa ne zaboravi kapu i rukavice.",
                savet(dan(0, 0, 18, 6, 0), dan(1, 1, 9, -2, 0)));
    }

    @Test
    void hladnoPaZima() {
        assertEquals("Dani se dosta razlikuju: najtopliji ima oko 12, a najhladniji oko 4 stepena preko dana. "
                + "Ponesi toplu jaknu, šal i zatvorenu obuću, a za najhladnije dane i kapu i rukavice.",
                savet(dan(0, 0, 12, 3, 0), dan(1, 3, 4, -2, 0)));
    }

    /** Isti pojas i kad je raspon veliki: svi dani traže isto, pa ostaje jedna rečenica. */
    @Test
    void velikiRasponUIstomPojasu_ostajeJednaRecenica() {
        assertTrue(savet(dan(0, 0, 40, 25, 0), dan(1, 0, 32, 22, 0)).startsWith("Preko dana je vrelo, oko 36 stepeni"));
        assertTrue(savet(dan(0, 3, 5, -1, 0), dan(1, 3, -3, -9, 0)).startsWith("Zimski uslovi, oko 1 stepen preko dana"));
    }

    // ── sneg i kiša ───────────────────────────────────────────────────────────

    /** Kod za sneg uz topao dan je greška u podacima - „vrelo je, ima i snega" ne sme da stigne. */
    @Test
    void snegUzTopaoDanSeNePominje() {
        String s = savet(dan(0, 0, 32, 22, 0), dan(1, 71, 31, 21, 3), dan(2, 0, 33, 23, 0));
        assertFalse(s.contains("sneg"), s);
        assertFalse(s.contains("čarape"), s);
        assertTrue(s.startsWith("Preko dana je vrelo"), s);

        assertFalse(savet(dan(0, 71, 12, 1, 3), dan(1, 3, 12, 2, 0)).contains("sneg"), "12 stepeni preko dana nije dan za sneg");
        assertFalse(savet(dan(0, 71, 9, 5, 3), dan(1, 3, 9, 5, 0)).contains("sneg"), "noć od 5 stepeni nije noć za sneg");
        assertTrue(savet(dan(0, 71, 9, 3, 3), dan(1, 3, 9, 4, 0)).contains("Ima i snega u najavi"), "9 preko dana i 3 noću jeste");
    }

    /** Ranije je sneg sakrivao kišu: kišobran se nije pominjao ni kad su ostali dani kišni. */
    @Test
    void snegJednogDanaIKisaDrugog_pominjuSeOba() {
        assertEquals("Hladno je, oko 6 stepeni preko dana - topla jakna, šal i zatvorena obuća. "
                + "Ima i snega u najavi - obuj nešto što ne klizi i spakuj tople čarape. "
                + "Kiša se očekuje 2 od 4 dana, pa ponesi kišobran ili kabanicu.",
                savet(dan(0, 71, 3, 0, 3), dan(1, 61, 6, 2, 6), dan(2, 63, 7, 3, 8), dan(3, 3, 6, 2, 0)));
    }

    /** Snežan dan sa padavinama nije i kišni dan - isti dan ne donosi i čarape i kišobran. */
    @Test
    void snezanDanSeNeBrojiKaoKisni() {
        String s = savet(dan(0, 71, 2, -3, 5), dan(1, 3, 3, -2, 0), dan(2, 3, 4, -1, 0));
        assertTrue(s.contains("Ima i snega u najavi"), s);
        assertFalse(s.contains("kišobran"), s);
    }

    @Test
    void jedanKisniDan_iViseKisnihDana() {
        assertTrue(savet(dan(0, 61, 21, 12, 4), dan(1, 0, 22, 13, 0), dan(2, 0, 21, 12, 0))
                .endsWith(" Jedan dan je najavljena kiša, pa ubaci i kišobran."));
        assertTrue(savet(dan(0, 61, 21, 12, 3), dan(1, 80, 22, 13, 3), dan(2, 95, 21, 12, 3), dan(3, 0, 21, 12, 0))
                .endsWith(" Kiša se očekuje 3 od 4 dana, pa ponesi kišobran ili kabanicu."));
    }

    /** Prognoza pokriva dva od četiri dana puta: ne tvrdi se „svakog dana" - za ostale dane se još ne zna. */
    @Test
    void delimicnoPokrivenPut_kisaBezBrojanja() {
        String obaKisna = ForecastEmailServiceImpl.packingHint(
                List.of(dan(0, 61, 21, 12, 4), dan(1, 63, 22, 13, 6)), DEP, RET);
        assertEquals("Prijatno je, oko 22 stepena preko dana - majice i lagane pantalone, a za jutro i veče dobro dođe "
                + "tanka jakna ili duks. U prognozi je i kiša, pa ubaci i kišobran.", obaKisna);

        String jedanKisni = ForecastEmailServiceImpl.packingHint(
                List.of(dan(0, 61, 21, 12, 4), dan(1, 0, 22, 13, 0)), DEP, RET);
        assertTrue(jedanKisni.endsWith(" U prognozi je i kiša, pa ubaci i kišobran."), jedanKisni);

        String suvo = ForecastEmailServiceImpl.packingHint(List.of(dan(0, 0, 21, 12, 0)), DEP, RET);
        assertFalse(suvo.contains("kiš"), suvo);
    }

    /** Ledena kiša (kodovi 56, 57, 66, 67) je ranije padala u „Promenljivo" i nije se brojala. */
    @Test
    void ledenaKisaSeBrojiKaoKisa() {
        assertTrue(savet(dan(0, 66, 3, -1, 0.4), dan(1, 3, 4, 0, 0)).contains("Jedan dan je najavljena kiša"));
    }

    /** Isti prag kao za kapljicu u mejlu: ispod 1 mm nema ni kapljice ni kišobrana. */
    @Test
    void sitnePadavineBezKodaKiseNisuKisniDan() {
        String s = savet(dan(0, 3, 21, 12, 0.7), dan(1, 0, 22, 13, 0.9));
        assertFalse(s.contains("kiš"), s);
        assertTrue(savet(dan(0, 3, 21, 12, 1.0), dan(1, 0, 22, 13, 0)).contains("Jedan dan je najavljena kiša"));
    }

    // ── ostalo ────────────────────────────────────────────────────────────────

    @Test
    void prognozaNePokrivaPut_nemaSaveta() {
        assertEquals("", ForecastEmailServiceImpl.packingHint(List.of(dan(-7, 0, 30, 20, 0), dan(-1, 0, 30, 20, 0)), DEP, RET));
        assertEquals("", ForecastEmailServiceImpl.packingHint(List.of(), DEP, RET));
    }

    @Test
    void padeziZaStepene() {
        assertEquals("21 stepen", ForecastEmailServiceImpl.stepeni(21));
        assertEquals("22 stepena", ForecastEmailServiceImpl.stepeni(22));
        assertEquals("25 stepeni", ForecastEmailServiceImpl.stepeni(25));
        assertEquals("11 stepeni", ForecastEmailServiceImpl.stepeni(11));
        assertEquals("1 stepen", ForecastEmailServiceImpl.stepeni(1));
        assertEquals("-3 stepena", ForecastEmailServiceImpl.stepeni(-3));
        assertEquals("0 stepeni", ForecastEmailServiceImpl.stepeni(0));
    }

    @Test
    void kadJePolazak_danasSutraIliZaNDana() {
        assertArrayEquals(new String[]{"za ", "7 dana"}, ForecastEmailServiceImpl.kadJePolazak(7));
        assertArrayEquals(new String[]{"za ", "2 dana"}, ForecastEmailServiceImpl.kadJePolazak(2));
        assertArrayEquals(new String[]{"za ", "15 dana"}, ForecastEmailServiceImpl.kadJePolazak(15));
        assertArrayEquals(new String[]{"", "sutra"}, ForecastEmailServiceImpl.kadJePolazak(1));
        assertArrayEquals(new String[]{"", "danas"}, ForecastEmailServiceImpl.kadJePolazak(0));
    }

    @Test
    void poslednjiDani_padezIBroj() {
        assertEquals("poslednji dan", ForecastEmailServiceImpl.poslednjiDani(1));
        assertEquals("poslednja 2 dana", ForecastEmailServiceImpl.poslednjiDani(2));
        assertEquals("poslednja 4 dana", ForecastEmailServiceImpl.poslednjiDani(4));
        assertEquals("poslednjih 5 dana", ForecastEmailServiceImpl.poslednjiDani(5));
        assertEquals("poslednjih 11 dana", ForecastEmailServiceImpl.poslednjiDani(11));
        assertEquals("poslednjih 12 dana", ForecastEmailServiceImpl.poslednjiDani(12));
        assertEquals("poslednji 21 dan", ForecastEmailServiceImpl.poslednjiDani(21));
        assertEquals("poslednja 22 dana", ForecastEmailServiceImpl.poslednjiDani(22));
    }
}
