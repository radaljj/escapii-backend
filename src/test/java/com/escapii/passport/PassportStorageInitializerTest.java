package com.escapii.passport;

import com.escapii.service.AppErrorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Start: kolona se proširi samo kad je uska, šifrovanje se uključi tek kad je kolona spremna,
 * zastareli redovi se prešifruju SQL-om, a nijedna greška ne obara aplikaciju.
 */
class PassportStorageInitializerTest {

    private static final String INFO = "SELECT data_type";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AppErrorService errors = mock(AppErrorService.class);
    private final PassportStorageInitializer init = new PassportStorageInitializer(jdbc, errors);

    @AfterEach
    void reset() {
        PassportCrypto.configure("");
        PassportCrypto.setEnabled(false);
    }

    private static List<Map<String, Object>> kolona(Integer duzina) {
        Map<String, Object> red = new java.util.HashMap<>();
        red.put("data_type", duzina == null ? "text" : "character varying");
        red.put("character_maximum_length", duzina);
        return List.of(red);
    }

    private void redovi(String... vrednosti) {
        when(jdbc.queryForList(startsWith("SELECT DISTINCT"), eq(String.class))).thenReturn(List.of(vrednosti));
    }

    @Test
    void uskaKolonaSeProsiriPaSeUkljuciSifrovanje() {
        when(jdbc.queryForList(startsWith(INFO), eq("booking_passengers"), eq("passport_serial_number")))
            .thenReturn(kolona(50)).thenReturn(kolona(255));
        redovi();

        init.naStartu();

        verify(jdbc).execute("ALTER TABLE booking_passengers ALTER COLUMN passport_serial_number TYPE VARCHAR(255)");
        assertTrue(PassportCrypto.isEnabled());
        verifyNoInteractions(errors);
    }

    @Test
    void sirokaKolonaSeNeDira() {
        when(jdbc.queryForList(startsWith(INFO), eq("booking_passengers"), eq("passport_serial_number")))
            .thenReturn(kolona(255));
        redovi();

        init.naStartu();

        verify(jdbc, never()).execute(anyString());
        assertTrue(PassportCrypto.isEnabled());
    }

    @Test
    void textKolonaJeDovoljna() {
        when(jdbc.queryForList(startsWith(INFO), eq("booking_passengers"), eq("passport_serial_number")))
            .thenReturn(kolona(null));
        redovi();

        init.naStartu();

        verify(jdbc, never()).execute(anyString());
        assertTrue(PassportCrypto.isEnabled());
    }

    @Test
    void otvoreniRedoviSePresifrujuSamoOni() {
        when(jdbc.queryForList(startsWith(INFO), eq("booking_passengers"), eq("passport_serial_number")))
            .thenReturn(kolona(255));
        String vecSifrovan = PassportCrypto.encrypt("CD7654321");
        redovi("AB1234567", vecSifrovan);
        when(jdbc.update(startsWith("UPDATE booking_passengers"), any(), any())).thenReturn(2);

        init.naStartu();

        ArgumentCaptor<Object> novi = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, times(1)).update(startsWith("UPDATE booking_passengers SET passport_serial_number = ?"),
                                      novi.capture(), eq("AB1234567"));
        String upisano = (String) novi.getValue();
        assertTrue(upisano.startsWith("v1:"), upisano);
        assertEquals("AB1234567", PassportCrypto.decrypt(upisano));
        verifyNoInteractions(errors);
    }

    @Test
    void redoviBezKljucaNeRuseStart_aliStizeAppError() {
        when(jdbc.queryForList(startsWith(INFO), eq("booking_passengers"), eq("passport_serial_number")))
            .thenReturn(kolona(255));
        PassportCrypto.configure(Base64.getEncoder().encodeToString(new byte[32]));
        String v2 = PassportCrypto.encrypt("AB1234567");
        PassportCrypto.configure("");
        redovi(v2, "EF1122334");

        init.naStartu();

        verify(jdbc, times(1)).update(anyString(), any(), eq("EF1122334"));
        verify(errors).record(eq("passport-reencrypt"), eq(0), any(IllegalStateException.class));
        assertTrue(PassportCrypto.isEnabled(), "ostali redovi su i dalje čitljivi i šifrovanje radi");
    }

    @Test
    void kolonaNePostoji_sifrovanjeOstajeIskljuceno() {
        when(jdbc.queryForList(startsWith(INFO), eq("booking_passengers"), eq("passport_serial_number")))
            .thenReturn(List.of());

        init.naStartu();

        verify(jdbc, never()).execute(anyString());
        assertFalse(PassportCrypto.isEnabled());
    }

    @Test
    void greskaBazeNeObaraAplikaciju() {
        when(jdbc.queryForList(startsWith(INFO), eq("booking_passengers"), eq("passport_serial_number")))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("baza nedostupna"));

        assertDoesNotThrow(init::naStartu);

        assertFalse(PassportCrypto.isEnabled());
        verify(errors).record(eq("passport-storage-init"), eq(0), any(Exception.class));
    }
}
