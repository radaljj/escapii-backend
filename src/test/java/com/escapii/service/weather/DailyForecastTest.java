package com.escapii.service.weather;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Opis, ikona i „kišni/snežni dan" po kodu vremena - od njih zavise i redovi u mejlu i savet za pakovanje. */
class DailyForecastTest {

    private static DailyForecast dan(int kod, int max, int min, double mm) {
        return new DailyForecast(LocalDate.of(2026, 10, 9), kod, max, min, mm);
    }

    /** Ledena kiša i ledena rosulja su ranije padale u „Promenljivo" sa termometrom i nisu se brojale kao kiša. */
    @Test
    void ledenaKisaImaSvojOpis() {
        for (int kod : new int[]{56, 57, 66, 67}) {
            DailyForecast d = dan(kod, 2, -1, 0.0);
            assertEquals("Ledena kiša", d.description(), "kod " + kod);
            assertEquals("🌧️", d.emoji(), "kod " + kod);
            assertTrue(d.rainy(), "kod " + kod + " je kišni dan i bez milimetara");
            assertFalse(d.snowy(), "kod " + kod);
        }
    }

    /** Svaki kod koji servis vraća ima svoj opis - „Promenljivo" ostaje samo za nepoznat kod. */
    @Test
    void sviPoznatiKodoviImajuOpis() {
        int[] kodovi = {0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99};
        for (int kod : kodovi) {
            assertNotEquals("Promenljivo", dan(kod, 15, 8, 0).description(), "kod " + kod);
            assertNotEquals("🌡️", dan(kod, 15, 8, 0).emoji(), "kod " + kod);
        }
        assertEquals("Promenljivo", dan(42, 15, 8, 0).description());
    }

    /** Jedan prag za padavine: od 1 mm. Isti važi za kapljicu u mejlu i za kišni dan u savetu. */
    @Test
    void pragZaPadavine() {
        assertFalse(dan(3, 15, 8, 0.0).imaPadavina());
        assertFalse(dan(3, 15, 8, 0.7).imaPadavina());
        assertFalse(dan(3, 15, 8, 0.99).imaPadavina());
        assertTrue(dan(3, 15, 8, 1.0).imaPadavina());
        assertTrue(dan(3, 15, 8, 12.4).imaPadavina());

        assertFalse(dan(3, 15, 8, 0.7).rainy(), "oblačno sa 0,7 mm nije kišni dan");
        assertTrue(dan(3, 15, 8, 1.0).rainy(), "oblačno sa 1 mm jeste");
        assertTrue(dan(61, 15, 8, 0.0).rainy(), "kod kiše je kišni dan i bez milimetara");
    }

    /** Sneg zbog kog se drugačije pakuje traži i hladan dan - kod snega uz toplo vreme je greška u podacima. */
    @Test
    void snegZaPakovanjeTraziHladanDan() {
        assertTrue(dan(71, 2, -3, 3).snegZaPakovanje());
        assertTrue(dan(85, 12, 4, 1).snegZaPakovanje(), "granica: 12 preko dana, kao u planinskim prognozama");
        assertFalse(dan(71, 13, 3, 3).snegZaPakovanje(), "13 preko dana je pretoplo za savet o snegu");
        assertFalse(dan(71, 30, 20, 3).snegZaPakovanje(), "sneg na 30 stepeni ne postoji");
        assertFalse(dan(61, 2, -3, 3).snegZaPakovanje(), "kiša na 2 stepena je kiša");
        assertTrue(dan(61, 1, -3, 3).snegZaPakovanje(), "padavine na mrazu su sneg i kad je kod kiše");
        assertTrue(dan(3, -1, -6, 2.0).snegZaPakovanje(), "padavine na mrazu su sneg i bez koda padavina");
        assertFalse(dan(3, -1, -6, 0.0).snegZaPakovanje(), "mraz bez padavina nije sneg");
        assertTrue(dan(71, 30, 20, 3).snowy(), "opis dana i dalje prati kod koji je servis poslao");
    }
}
