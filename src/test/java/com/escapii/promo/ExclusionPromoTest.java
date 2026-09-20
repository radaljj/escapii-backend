package com.escapii.promo;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Promo „besplatno isključivanje": kod važi samo dok je uključen iz panela i dok datum nije
 * prošao. Dok niko ništa nije sačuvao, kod iz podešavanja postoji ali je promo UGAŠEN.
 */
class ExclusionPromoTest {

    private static final LocalDate DANAS = LocalDate.of(2026, 10, 15);

    /** app_settings u memoriji - dovoljno za dva upita koja ExclusionPromo koristi. */
    static class LaznaBaza extends JdbcTemplate {
        final Map<String, String> redovi = new HashMap<>();
        boolean pukni;
        int citanja;

        @Override
        public void query(String sql, RowCallbackHandler rch) {
            citanja++;
            if (pukni) throw new DataAccessResourceFailureException("baza nedostupna");
            for (Map.Entry<String, String> e : redovi.entrySet()) {
                try {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString(1)).thenReturn(e.getKey());
                    when(rs.getString(2)).thenReturn(e.getValue());
                    rch.processRow(rs);
                } catch (SQLException ex) { throw new IllegalStateException(ex); }
            }
        }

        @Override
        public int update(String sql, Object... args) {
            if (pukni) throw new DataAccessResourceFailureException("baza nedostupna");
            redovi.put((String) args[0], (String) args[1]);
            return 1;
        }
    }

    private final LaznaBaza baza = new LaznaBaza();
    private LocalDate sada = DANAS;
    private final ExclusionPromo promo = new ExclusionPromo(baza, " skip3 ", () -> sada);

    @Test
    void bezSacuvanihPodesavanja_kodPostojiAliJePromoUgasen() {
        assertEquals("SKIP3", promo.podesavanja().kod());
        assertFalse(promo.aktivan());
        assertFalse(promo.vazi("SKIP3"), "dok admin ne uključi promo, kod ne radi");
    }

    @Test
    void ukljucenSaDatumom_vazi_bezObziraNaVelicinuSlovaIRazmake() {
        promo.sacuvaj("SKIP3", DANAS.plusDays(10), true);

        assertTrue(promo.aktivan());
        assertTrue(promo.vazi("SKIP3"));
        assertTrue(promo.vazi("  skip3 "));
        assertFalse(promo.vazi("SKIP4"));
        assertFalse(promo.vazi(""));
        assertFalse(promo.vazi(null));
    }

    @Test
    void poslednjiDanJosVazi_sutradanNe() {
        promo.sacuvaj("SKIP3", DANAS, true);
        assertTrue(promo.vazi("SKIP3"), "datum isteka je uključiv");

        sada = DANAS.plusDays(1);
        assertFalse(promo.vazi("SKIP3"));
        assertFalse(promo.aktivan());
    }

    @Test
    void gasenjeIzPanela_vaziOdmah_bezCekanjaKesa() {
        promo.sacuvaj("SKIP3", DANAS.plusDays(10), true);
        assertTrue(promo.vazi("SKIP3"));

        promo.sacuvaj("SKIP3", DANAS.plusDays(10), false);

        assertFalse(promo.vazi("SKIP3"), "ako kod procuri, gašenje mora da deluje odmah");
    }

    @Test
    void promenaKoda_stariViseNeVazi() {
        promo.sacuvaj("SKIP3", DANAS.plusDays(10), true);
        promo.sacuvaj("prvi-2026", DANAS.plusDays(10), true);

        assertEquals("PRVI-2026", promo.podesavanja().kod());
        assertTrue(promo.vazi("PRVI-2026"));
        assertFalse(promo.vazi("SKIP3"));
    }

    @Test
    void losUnosIzPanela_seOdbijaSaPorukom_iNistaSeNeUpisuje() {
        assertThrows(IllegalArgumentException.class, () -> promo.sacuvaj("ab", DANAS, true), "prekratko");
        assertThrows(IllegalArgumentException.class, () -> promo.sacuvaj("SKIP 3", DANAS, true), "razmak");
        assertThrows(IllegalArgumentException.class, () -> promo.sacuvaj("<script>", DANAS, true));
        assertThrows(IllegalArgumentException.class, () -> promo.sacuvaj("ESC-ABCD-EFGH-JKLM", DANAS, true),
                "tako počinju poklon vaučeri - sajt po tome bira koju proveru zove");
        assertThrows(IllegalArgumentException.class, () -> promo.sacuvaj("SKIP3", null, true),
                "uključen promo bez datuma bi trajao zauvek");
        assertTrue(baza.redovi.isEmpty());
    }

    @Test
    void ugasenPromoSmeBezDatuma() {
        assertDoesNotThrow(() -> promo.sacuvaj("SKIP3", null, false));
        assertFalse(promo.aktivan());
    }

    @Test
    void podesavanjaSeKesiraju_bazaSeNeCitaNaSvakiObracunCene() {
        promo.sacuvaj("SKIP3", DANAS.plusDays(10), true);
        int posleCuvanja = baza.citanja;

        for (int i = 0; i < 20; i++) promo.vazi("SKIP3");

        assertEquals(posleCuvanja, baza.citanja, "sačuvaj() je već napunio keš");
    }

    @Test
    void padBaze_koristiPoslednjePoznato_aBezToga_promoJeUgasen() {
        ExclusionPromo svez = new ExclusionPromo(baza, "SKIP3", () -> sada);
        baza.pukni = true;
        assertFalse(svez.vazi("SKIP3"), "bez ijednog uspešnog čitanja promo se tretira kao ugašen");
        assertEquals("SKIP3", svez.podesavanja().kod());

        baza.pukni = false;
        promo.sacuvaj("SKIP3", DANAS.plusDays(10), true);
        assertTrue(promo.vazi("SKIP3"));
    }

    @Test
    void neispravanDatumUBazi_znaciDaPromoNeRadi() {
        baza.redovi.put(ExclusionPromo.K_KOD, "SKIP3");
        baza.redovi.put(ExclusionPromo.K_UKLJUCEN, "true");
        baza.redovi.put(ExclusionPromo.K_VAZI_DO, "nije-datum");

        assertFalse(promo.vazi("SKIP3"));
        assertNull(promo.podesavanja().vaziDo());
    }
}
