package com.escapii.service.weather;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Keš koordinata: memorija pa baza; greška baze nikad ne baca, samo promašaj. */
class GeoCacheTest {

    @Test
    @SuppressWarnings("unchecked")
    void pogodakIzBazeSePamtiUMemoriji_bazaSeCitaJednom() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(startsWith("SELECT lat, lon FROM geo_cache"), any(RowMapper.class), eq("prag")))
                .thenReturn(List.of(new double[]{ 50.08, 14.42 }));
        GeoCache kes = new GeoCache(jdbc);

        Optional<double[]> prvi = kes.get("  Prag ");
        Optional<double[]> drugi = kes.get("prag");

        assertTrue(prvi.isPresent() && drugi.isPresent());
        assertEquals(50.08, prvi.get()[0], 1e-9);
        assertEquals(14.42, drugi.get()[1], 1e-9);
        verify(jdbc, times(1)).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void upisIdeUMemorijuIBazu_saOnConflict() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        GeoCache kes = new GeoCache(jdbc);

        kes.put("Beč", 48.21, 16.37, "nominatim");

        verify(jdbc).update(contains("ON CONFLICT (city_query) DO UPDATE"), eq("beč"), eq(48.21), eq(16.37), eq("nominatim"), any());
        assertTrue(kes.get("BEČ").isPresent(), "posle upisa pogodak iz memorije, bez baze");
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void greskaBaze_jePromasaj_neIzuzetak() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenThrow(new RuntimeException("tabela ne postoji"));
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("tabela ne postoji"));
        GeoCache kes = new GeoCache(jdbc);

        assertTrue(kes.get("Rim").isEmpty());
        assertDoesNotThrow(() -> kes.put("Rim", 41.9, 12.5, "nominatim"));
        assertTrue(kes.get("Rim").isPresent(), "memorija radi i kad baza ne radi");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rezervniIzvor_seNePamtiTrajno() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        GeoCache kes = new GeoCache(jdbc);

        kes.put("Beč", 48.21, 16.37, "open-meteo", false);

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        assertTrue(kes.get("beč").isPresent(), "u memoriji jeste");
    }

    @Test
    void bezBaze_radiSamoMemorija() {
        GeoCache kes = new GeoCache((JdbcTemplate) null);
        assertTrue(kes.get("Pariz").isEmpty());
        kes.put("Pariz", 48.85, 2.35, "open-meteo");
        assertEquals(48.85, kes.get("pariz").get()[0], 1e-9);
        assertTrue(kes.get("").isEmpty());
        assertTrue(kes.get(null).isEmpty());
    }

    @Test
    void kljucJeNormalizovanISkracen() {
        assertEquals("santa cruz de tenerife, spain", GeoCache.kljuc("  Santa Cruz de Tenerife, Spain "));
        assertEquals(GeoCache.MAX_KLJUC, GeoCache.kljuc("x".repeat(500)).length());
    }
}
