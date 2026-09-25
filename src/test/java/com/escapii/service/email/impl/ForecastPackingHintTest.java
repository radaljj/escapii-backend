package com.escapii.service.email.impl;

import com.escapii.service.weather.DailyForecast;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Savet za pakovanje u prognozi mora da prati temperature DANA PUTA: na 25 stepeni nema
 * jakne, na 8 ima, kiša donosi kišobran, sneg obuću - i ništa od toga kad prognoza ne
 * pokriva put.
 */
class ForecastPackingHintTest {

    private static final LocalDate DEP = LocalDate.of(2026, 10, 9);
    private static final LocalDate RET = LocalDate.of(2026, 10, 12);

    private static DailyForecast dan(int plus, int code, int max, int min, double mm) {
        return new DailyForecast(DEP.plusDays(plus), code, max, min, mm);
    }

    @Test
    void toploBezKise_nemaJakne() {
        String s = ForecastEmailServiceImpl.packingHint(List.of(
                dan(-7, 3, 8, 2, 0),            // danas je hladno - ne sme da utiče
                dan(0, 0, 26, 16, 0), dan(1, 1, 27, 17, 0), dan(2, 0, 25, 16, 0), dan(3, 2, 26, 17, 0)), DEP, RET);
        assertTrue(s.startsWith("Toplo je, oko 26 stepeni preko dana"), s);
        assertFalse(s.contains("jakn"), "na 26 stepeni nema jakne: " + s);
        assertFalse(s.contains("kišobran"), s);
        assertFalse(s.contains("Noću pada"), "noći od 16 nisu hladne: " + s);
    }

    @Test
    void prijatnoSaHladnimNocimaIJednomKisom() {
        String s = ForecastEmailServiceImpl.packingHint(List.of(
                dan(0, 2, 22, 9, 0), dan(1, 61, 20, 10, 4.2), dan(2, 1, 23, 11, 0), dan(3, 0, 23, 12, 0)), DEP, RET);
        assertTrue(s.startsWith("Prijatno je, oko 22 stepena preko dana"), s);
        assertTrue(s.contains("tanka jakna ili duks"), s);
        assertTrue(s.contains("Noću pada na oko 9 stepeni"), s);
        assertTrue(s.contains("Jedan dan je najavljena kiša, pa ubaci i kišobran."), s);
    }

    @Test
    void hladnoSaKisomSvakogDana() {
        String s = ForecastEmailServiceImpl.packingHint(List.of(
                dan(0, 61, 9, 4, 6), dan(1, 63, 8, 3, 9), dan(2, 80, 8, 4, 3), dan(3, 61, 7, 2, 5)), DEP, RET);
        assertTrue(s.startsWith("Hladno je, oko 8 stepeni preko dana - topla jakna"), s);
        assertTrue(s.contains("Kiša je najavljena svakog dana"), s);
    }

    @Test
    void zimaSaSnegomINocimaIspodNule() {
        String s = ForecastEmailServiceImpl.packingHint(List.of(
                dan(0, 3, 4, -2, 0), dan(1, 71, 1, -4, 3), dan(2, 3, 3, -3, 0), dan(3, 1, 5, -1, 0)), DEP, RET);
        assertTrue(s.startsWith("Zimski uslovi, oko 3 stepena preko dana, a noću ispod nule"), s);
        assertTrue(s.contains("snega u najavi"), s);
        assertFalse(s.contains("kišobran"), "sneg ima prednost nad kišom: " + s);
    }

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
}
