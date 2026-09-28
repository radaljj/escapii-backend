package com.escapii.service.email.impl;

import com.escapii.service.weather.DailyForecast;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Savet za pakovanje na prognozama kakve se stvarno dešavaju, ispisan u
 * {@code target/email-preview/saveti-za-pakovanje-primeri.txt} - da se posle svake izmene teksta
 * rezultat pročita očima, bez slanja mejla. Logiku na svim kombinacijama čuva
 * ForecastPackingLogicTest, doslovne rečenice ForecastPackingHintTest.
 */
class ForecastPackingExamplesTest {

    private static final LocalDate DEP = LocalDate.of(2026, 10, 9);

    /** {kod, max, min, mm×10} po danu. */
    private static List<DailyForecast> put(int[]... dani) {
        List<DailyForecast> f = new ArrayList<>();
        for (int i = 0; i < dani.length; i++) {
            int[] d = dani[i];
            f.add(new DailyForecast(DEP.plusDays(i), d[0], d[1], d[2], d[3] / 10.0));
        }
        return f;
    }

    private static int[] d(int kod, int max, int min, int mmPuta10) {
        return new int[]{kod, max, min, mmPuta10};
    }

    @Test
    void primeriZaPregled() throws Exception {
        Map<String, List<DailyForecast>> primeri = new LinkedHashMap<>();
        primeri.put("Leto na moru",                     put(d(0, 33, 24, 0), d(0, 32, 23, 0), d(1, 34, 25, 0), d(0, 33, 24, 0)));
        primeri.put("Toplotni talas",                   put(d(0, 38, 26, 0), d(0, 39, 27, 0), d(1, 37, 25, 0), d(0, 36, 24, 0)));
        primeri.put("Leto sa jednim pljuskom",          put(d(1, 29, 20, 0), d(80, 27, 19, 60), d(2, 28, 20, 0), d(0, 30, 21, 0)));
        primeri.put("Leto sa grmljavinom dva dana",     put(d(0, 31, 21, 0), d(95, 30, 20, 120), d(96, 29, 19, 90), d(1, 31, 21, 0)));
        primeri.put("Rano leto, sveže noći",            put(d(0, 26, 10, 0), d(1, 27, 11, 0), d(0, 25, 9, 0), d(2, 26, 10, 0)));
        primeri.put("Proleće u gradu",                  put(d(2, 21, 11, 0), d(1, 22, 12, 0), d(3, 20, 11, 0), d(1, 23, 13, 0)));
        primeri.put("Proleće, hladne noći i kiša",      put(d(2, 20, 7, 0), d(61, 19, 8, 50), d(3, 21, 9, 0), d(1, 22, 9, 0)));
        primeri.put("Sitna kiša ispod 1 mm",            put(d(3, 22, 13, 6), d(2, 23, 14, 0), d(3, 21, 12, 0), d(1, 22, 13, 0)));
        primeri.put("Vikend od dva dana",               put(d(1, 24, 14, 0), d(2, 23, 13, 0)));
        primeri.put("Jesen",                            put(d(2, 16, 8, 0), d(3, 15, 7, 0), d(61, 14, 7, 30), d(2, 17, 9, 0)));
        primeri.put("Jesen sa mrazom noću",             put(d(0, 15, 1, 0), d(0, 14, -1, 0), d(1, 16, 2, 0), d(2, 15, 0, 0)));
        primeri.put("Kasna jesen, kiša svakog dana",    put(d(61, 9, 4, 60), d(63, 8, 3, 90), d(80, 8, 4, 30), d(61, 7, 2, 50)));
        primeri.put("Zima bez snega",                   put(d(3, 7, -2, 0), d(2, 8, -1, 0), d(3, 6, -3, 0), d(1, 7, -2, 0)));
        primeri.put("Zima sa snegom",                   put(d(3, 3, -3, 0), d(71, 1, -5, 40), d(73, 2, -4, 60), d(2, 4, -2, 0)));
        primeri.put("Jak mraz",                         put(d(3, -4, -11, 0), d(71, -6, -13, 20), d(3, -5, -12, 0), d(1, -3, -9, 0)));
        primeri.put("Ledena kiša",                      put(d(66, 2, -2, 20), d(3, 4, -1, 0), d(3, 5, 0, 0), d(2, 3, -2, 0)));
        primeri.put("Planina: kiša pa sneg",            put(d(61, 9, 1, 40), d(71, 5, -1, 50), d(3, 8, 0, 0), d(2, 11, 2, 0)));
        primeri.put("Zahlađenje usred puta",            put(d(0, 27, 17, 0), d(1, 26, 16, 0), d(61, 17, 9, 80), d(3, 15, 8, 0)));
        primeri.put("Otopljenje usred puta",            put(d(3, 8, 1, 0), d(2, 10, 2, 0), d(1, 18, 7, 0), d(0, 20, 9, 0)));
        primeri.put("Osam dana, promenljivo",           put(d(1, 24, 14, 0), d(0, 25, 15, 0), d(61, 23, 13, 30), d(2, 22, 12, 0),
                                                            d(80, 21, 12, 50), d(1, 24, 14, 0), d(0, 26, 15, 0), d(1, 25, 14, 0)));

        StringBuilder o = new StringBuilder("Savet za pakovanje na stvarnim prognozama (dan: max/min, opis, padavine)\n\n");
        for (var e : primeri.entrySet()) {
            List<DailyForecast> dani = e.getValue();
            String savet = ForecastEmailServiceImpl.packingHint(dani, DEP, DEP.plusDays(dani.size() - 1));
            assertFalse(savet.isBlank(), e.getKey());
            o.append(e.getKey()).append("\n  ");
            for (DailyForecast dan : dani) {
                o.append(dan.maxTemp()).append("/").append(dan.minTemp()).append(" ").append(dan.description());
                if (dan.imaPadavina()) o.append(" ").append(String.format("%.0f", dan.precipitation())).append(" mm");
                o.append(" · ");
            }
            o.setLength(o.length() - 3);
            o.append("\n  → ").append(savet).append("\n\n");
        }
        Path izlaz = Path.of("target/email-preview");
        Files.createDirectories(izlaz);
        Files.writeString(izlaz.resolve("saveti-za-pakovanje-primeri.txt"), o.toString(), StandardCharsets.UTF_8);
    }
}
