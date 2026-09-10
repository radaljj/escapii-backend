package com.escapii.service;

import com.escapii.model.Destination;
import com.escapii.repository.DestinationRepository;
import com.escapii.service.impl.TravelAddonsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Zaključava pravila po kojima se prave partnerski linkovi na reveal stranici.
 *
 * Rizik ovde nije da danas ne radi, nego da neko kasnije doda "razuman" fallback -
 * link na početnu stranicu partnera kad nemamo slug, ili prikazivanje kartice za
 * prtljag u gradu koji Bounce ne pokriva. Oba bi vodila kupca na praznu stranicu
 * posle klika, a to je gore nego da kartice nema.
 */
@ExtendWith(MockitoExtension.class)
class TravelAddonsLinksTest {

    private static final String GYG    = "https://www.getyourguide.com/sr-rs/{slug}/?partner_id=TEST";
    private static final String AIRALO = "https://airalo.pxf.io/c/1/2/3?u=https%3A%2F%2Fwww.airalo.com%2F{slug}";
    private static final String BOUNCE = "https://bounce.com/luggage-storage/{slug}";

    @Mock DestinationRepository destinationRepository;

    private TravelAddonsService service;

    @BeforeEach
    void setUp() {
        service = new TravelAddonsService(destinationRepository);
        postaviSablone(GYG, AIRALO, BOUNCE);
    }

    private void postaviSablone(String gyg, String airalo, String bounce) {
        ReflectionTestUtils.setField(service, "gygTemplate", gyg);
        ReflectionTestUtils.setField(service, "airaloTemplate", airalo);
        ReflectionTestUtils.setField(service, "bounceTemplate", bounce);
    }

    private Destination firenca() {
        Destination d = new Destination();
        d.setId(1L);
        d.setName("Firenca");
        d.setNameEn("Florence");
        d.setGygSlug("florence-l32");
        d.setAiraloSlug("italy-esim");
        d.setBounceSlug("florence");
        d.setBounceCovered(true);
        return d;
    }

    @Test
    void sviLinkoviSadrzeIdentifikatorDestinacije() {
        when(destinationRepository.findByAnyNameIgnoreCase(anyString())).thenReturn(List.of(firenca()));

        Map<String, String> links = service.linksFor("Firenca");

        assertEquals(3, links.size(), "sve tri kartice treba da imaju link");
        assertEquals("https://www.getyourguide.com/sr-rs/florence-l32/?partner_id=TEST", links.get("tours"));
        assertEquals("https://airalo.pxf.io/c/1/2/3?u=https%3A%2F%2Fwww.airalo.com%2Fitaly-esim", links.get("esim"));
        assertEquals("https://bounce.com/luggage-storage/florence", links.get("luggage"));
    }

    /**
     * Admin je Milanu promenio IATA kod sa BGY (Bergamo) na MXP: englesko ime je sad
     * "Milan", ali slugovi izvedeni iz starog koda kažu "bergamo". Takav link je gori
     * od nikakvog - vodi u drugi grad - pa se kartica preskače dok se slug ne osveži.
     * Airalo ostaje: država je ista.
     */
    @Test
    void slugIzStarogIataKodaSeNeKoristi() {
        Destination milano = new Destination();
        milano.setId(2L);
        milano.setName("Milano");
        milano.setNameEn("Milan");
        milano.setCountryEn("Italy");
        milano.setGygSlug("bergamo-l123");
        milano.setBounceSlug("bergamo");
        milano.setBounceCovered(true);
        milano.setAiraloSlug("italy-esim");
        when(destinationRepository.findByAnyNameIgnoreCase(anyString())).thenReturn(List.of(milano));

        Map<String, String> links = service.linksFor("Milano");

        assertNull(links.get("tours"),   "ture za Bergamo ne smeju pod Milano");
        assertNull(links.get("luggage"), "prtljag u Bergamu ne sme pod Milano");
        assertEquals("https://airalo.pxf.io/c/1/2/3?u=https%3A%2F%2Fwww.airalo.com%2Fitaly-esim", links.get("esim"),
                "eSIM je za državu, a ona se nije promenila");

        // posle osvežavanja (kao što updateDestination radi) linkovi se vraćaju
        milano.setGygSlug("milan-l70");
        milano.setBounceSlug("milan");
        assertEquals("https://www.getyourguide.com/sr-rs/milan-l70/?partner_id=TEST", service.linksFor("Milano").get("tours"));
        assertEquals("https://bounce.com/luggage-storage/milan", service.linksFor("Milano").get("luggage"));
    }

    /** Nepoznat IATA kod (nema ga u airports.dat): nema imena, nema ni osnova da se slug odbaci. */
    @Test
    void bezEngleskogImenaSlugOstaje() {
        Destination d = firenca();
        d.setNameEn(null);
        d.setCountryEn(null);
        when(destinationRepository.findByAnyNameIgnoreCase(anyString())).thenReturn(List.of(d));
        assertEquals(3, service.linksFor("Firenca").size());
    }
    /** Kad partnerski nalog jos nije odobren, env varijabla je prazna - kartica izostaje. */
    @Test
    void prazanSablonZnaciDaKarticeNema() {
        when(destinationRepository.findByAnyNameIgnoreCase(anyString())).thenReturn(List.of(firenca()));
        postaviSablone(GYG, "", "");

        Map<String, String> links = service.linksFor("Firenca");

        assertEquals(1, links.size());
        assertTrue(links.containsKey("tours"));
        assertFalse(links.containsKey("esim"),    "bez sablona nema linka, ni zamenskog");
        assertFalse(links.containsKey("luggage"), "bez sablona nema linka, ni zamenskog");
    }

    /** Bounce ne pokriva svaki grad (Memingen, Fridrihshafen, Kipar) - tada kartica izostaje. */
    @Test
    void nepokrivenGradNemaKarticuZaPrtljag() {
        Destination memingen = firenca();
        memingen.setName("Memingen");
        memingen.setBounceSlug("memmingen");
        memingen.setBounceCovered(false);
        when(destinationRepository.findByAnyNameIgnoreCase(anyString())).thenReturn(List.of(memingen));

        Map<String, String> links = service.linksFor("Memingen");

        assertFalse(links.containsKey("luggage"),
                "Bounce nema lokacije u tom gradu - klik bi vodio na praznu stranicu");
        assertTrue(links.containsKey("tours"), "ostale kartice ostaju");
        assertTrue(links.containsKey("esim"));
    }

    /** Slug koji skript jos nije popunio ne sme da proizvede polovican link. */
    @Test
    void praznSlugNeProizvodiLink() {
        Destination bezSluga = firenca();
        bezSluga.setGygSlug(null);
        bezSluga.setAiraloSlug("  ");
        when(destinationRepository.findByAnyNameIgnoreCase(anyString())).thenReturn(List.of(bezSluga));

        Map<String, String> links = service.linksFor("Firenca");

        assertFalse(links.containsKey("tours"));
        assertFalse(links.containsKey("esim"));
        assertTrue(links.containsKey("luggage"));
    }

    /**
     * Destinacija na rezervaciji je slobodan tekst koji admin kuca rukom. Ako se ne
     * poklopi ni sa jednim redom, popup mora izostati u celini - nikako link na
     * pocetnu stranicu partnera.
     */
    @Test
    void nepoznatoImeNeVracaNista() {
        when(destinationRepository.findByAnyNameIgnoreCase(anyString())).thenReturn(List.of());

        assertTrue(service.linksFor("Nepostojeci Grad").isEmpty());
    }

    @Test
    void praznaDestinacijaNeDiraBazu() {
        assertTrue(service.linksFor(null).isEmpty());
        assertTrue(service.linksFor("   ").isEmpty());
    }

    /** Admin sme otkucati i englesko ime - upit poredi oba, pa link mora izaci isti. */
    @Test
    void engleskoImeDajeIsteLinkove() {
        when(destinationRepository.findByAnyNameIgnoreCase(anyString())).thenReturn(List.of(firenca()));

        assertEquals(service.linksFor("Firenca"), service.linksFor("Florence"));
    }

    /** Sablon bez {slug} rupe je greska u konfiguraciji - bolje bez linka nego pogresan. */
    @Test
    void sablonBezRupeSePreskace() {
        when(destinationRepository.findByAnyNameIgnoreCase(anyString())).thenReturn(List.of(firenca()));
        postaviSablone("https://www.getyourguide.com/?partner_id=TEST", AIRALO, BOUNCE);

        Map<String, String> links = service.linksFor("Firenca");

        assertFalse(links.containsKey("tours"),
                "sablon bez {slug} bi vodio na pocetnu stranicu umesto na grad");
    }
}
