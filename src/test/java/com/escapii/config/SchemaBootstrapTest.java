package com.escapii.config;

import com.escapii.service.AppErrorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** db/schema.sql se deli po ";", komentari se izbacuju, pukla naredba ne zaustavlja ostale. */
class SchemaBootstrapTest {

    @Test
    void deliSkriptuIIzbacujeKomentare() {
        List<String> n = SchemaBootstrap.naredbe(
                "-- komentar\nCREATE TABLE IF NOT EXISTS a (\n  id BIGINT -- pk\n);\n\nALTER TABLE IF EXISTS b ADD COLUMN IF NOT EXISTS c BIGINT;\n-- kraj\n");
        assertEquals(2, n.size(), n.toString());
        assertEquals("CREATE TABLE IF NOT EXISTS a (\n  id BIGINT\n)", n.get(0));
        assertEquals("ALTER TABLE IF EXISTS b ADD COLUMN IF NOT EXISTS c BIGINT", n.get(1));
    }

    @Test
    void pravaSkriptaImaSamoIdempotentneNaredbe() throws Exception {
        String skripta = new String(new org.springframework.core.io.ClassPathResource(SchemaBootstrap.SKRIPTA)
                .getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        List<String> n = SchemaBootstrap.naredbe(skripta);
        assertFalse(n.isEmpty());
        for (String s : n) {
            String u = s.toUpperCase();
            assertTrue(u.startsWith("CREATE TABLE IF NOT EXISTS") || u.startsWith("CREATE INDEX IF NOT EXISTS")
                    || u.startsWith("ALTER TABLE IF EXISTS"), "nije idempotentna: " + s);
            // Dozvoljen je samo DROP CONSTRAINT IF EXISTS (zastarela CHECK ogranicenja); tabele i kolone se ne brisu.
            assertFalse(u.contains("DROP TABLE") || u.contains("DROP COLUMN"), "bez brisanja tabela/kolona: " + s);
            assertFalse(u.contains("DROP CONSTRAINT") && !u.contains("DROP CONSTRAINT IF EXISTS"), "DROP CONSTRAINT mora biti IF EXISTS: " + s);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void puklaNaredbaSeBeleziAOstaleIdu() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AppErrorService greske = mock(AppErrorService.class);
        ObjectProvider<AppErrorService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(greske);
        doThrow(new RuntimeException("permission denied")).when(jdbc).execute(startsWith("CREATE INDEX"));

        new SchemaBootstrap(jdbc, provider).primeni();

        verify(jdbc, atLeast(3)).execute(anyString());
        verify(jdbc).execute(startsWith("ALTER TABLE IF EXISTS bookings ADD COLUMN IF NOT EXISTS agency_invoice_id"));
        verify(jdbc, atLeastOnce()).execute(startsWith("ALTER TABLE IF EXISTS bookings DROP CONSTRAINT IF EXISTS"));
        verify(greske).record(eq("schema-bootstrap"), eq(0), any(RuntimeException.class));
    }
}
