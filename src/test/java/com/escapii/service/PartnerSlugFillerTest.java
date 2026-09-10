package com.escapii.service;

import com.escapii.model.Destination;
import com.escapii.repository.DestinationRepository;
import com.escapii.service.impl.PartnerSlugFiller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Zaključava pravila po kojima se partnerski slugovi popunjavaju sami.
 *
 * Rizik nije da danas ne radi, nego da neko kasnije "pojednostavi" logiku tako da
 * upiše slug bez provere u spisku partnera. Tada bi kartica postojala a klik vodio
 * na 404 - a to je gore nego kartica koje nema, jer kupac pomisli da je sajt loš.
 *
 * Spiskovi partnera se podmeću kroz protected metode, da test ne zavisi od mreže.
 */
@ExtendWith(MockitoExtension.class)
class PartnerSlugFillerTest {

    @Mock DestinationRepository destinationRepository;

    /** Podmeće spiskove umesto mrežnog poziva. */
    private PartnerSlugFiller filler(Set<String> airalo, Set<String> bounce) {
        return new PartnerSlugFiller(destinationRepository) {
            @Override protected Set<String> airaloSpisak() { return airalo; }
            @Override protected Set<String> bounceSpisak() { return bounce; }
        };
    }

    private static final Set<String> AIRALO = Set.of("czech-republic-esim", "italy-esim", "germany-esim");
    private static final Set<String> BOUNCE = Set.of("prague", "florence", "berlin");

    private Destination dest(String ime, String gradEn, String drzavaEn) {
        Destination d = new Destination();
        d.setName(ime);
        d.setNameEn(gradEn);
        d.setCountryEn(drzavaEn);
        return d;
    }

    @Test
    void popunjavaObaSlugaIzEngleskihNaziva() {
        Destination d = dest("Prag", "Prague", "Czech Republic");

        filler(AIRALO, BOUNCE).popuniBrzeSlugove(d);

        assertEquals("czech-republic-esim", d.getAiraloSlug(), "razmak postaje crtica");
        assertEquals("prague", d.getBounceSlug());
        assertTrue(d.getBounceCovered());
    }

    /** Grad kog nema na Bounce spisku - kartica za prtljag mora izostati. */
    @Test
    void gradVanBounceSpiskaNijePokriven() {
        Destination d = dest("Memingen", "Memmingen", "Germany");

        filler(AIRALO, BOUNCE).popuniBrzeSlugove(d);

        assertNull(d.getBounceSlug(), "slug se ne upisuje ako grad nije na spisku");
        assertFalse(d.getBounceCovered());
        assertEquals("germany-esim", d.getAiraloSlug(), "eSIM i dalje radi, vezan je za drzavu");
    }

    /** Drzava koje nema kod Airala - ne nagadjamo, ostaje prazno. */
    @Test
    void drzavaVanAiraloSpiskaOstajePrazna() {
        Destination d = dest("Podgorica", "Podgorica", "Montenegro");

        filler(AIRALO, BOUNCE).popuniBrzeSlugove(d);

        assertNull(d.getAiraloSlug(), "bolje prazno nego slug koji vodi na 404");
    }

    /** Ono sto je admin uneo rukom ne sme da se pregazi automatikom. */
    @Test
    void rucnoUnetaVrednostSeNePregazi() {
        Destination d = dest("Prag", "Prague", "Czech Republic");
        d.setAiraloSlug("moj-rucni-slug");
        d.setBounceSlug("moj-rucni-grad");

        filler(AIRALO, BOUNCE).popuniBrzeSlugove(d);

        assertEquals("moj-rucni-slug", d.getAiraloSlug());
        assertEquals("moj-rucni-grad", d.getBounceSlug());
    }

    /**
     * Bounce vremenom otvara nove gradove. Pokrivenost je cinjenica o partneru, ne
     * podesavanje, pa se osvezava i kad je slug vec upisan.
     */
    @Test
    void pokrivenostSeOsvezavaIKadJeSlugVecUpisan() {
        Destination biviNepokriven = dest("Berlin", "Berlin", "Germany");
        biviNepokriven.setBounceSlug("berlin");
        biviNepokriven.setBounceCovered(false);

        filler(AIRALO, BOUNCE).popuniBrzeSlugove(biviNepokriven);

        assertTrue(biviNepokriven.getBounceCovered(), "Bounce ih sada ima - kartica se pali");

        Destination viseNijeNaSpisku = dest("Neki Grad", "SomeCity", "Germany");
        viseNijeNaSpisku.setBounceSlug("somecity");
        viseNijeNaSpisku.setBounceCovered(true);

        filler(AIRALO, BOUNCE).popuniBrzeSlugove(viseNijeNaSpisku);

        assertFalse(viseNijeNaSpisku.getBounceCovered(), "vise ih nema - kartica se gasi");
    }

    /**
     * Ako se partnerski spisak ne skine (mreza, 403, promena formata), ne smemo ni
     * upisati nagadjanje ni obrisati ono sto vec stoji.
     */
    @Test
    void nedostupanSpisakNeDiraPostojeceVrednosti() {
        Destination d = dest("Prag", "Prague", "Czech Republic");
        d.setAiraloSlug("czech-republic-esim");
        d.setBounceSlug("prague");
        d.setBounceCovered(true);

        filler(Set.of(), Set.of()).popuniBrzeSlugove(d);

        assertEquals("czech-republic-esim", d.getAiraloSlug());
        assertEquals("prague", d.getBounceSlug());
        assertTrue(d.getBounceCovered());
    }

    @Test
    void bezEngleskihNazivaNemaSta() {
        Destination d = dest("Neka Destinacija", null, null);

        filler(AIRALO, BOUNCE).popuniBrzeSlugove(d);

        assertNull(d.getAiraloSlug());
        assertNull(d.getBounceSlug());
    }

    /** Dijakritika i razmaci se svode isto kako partneri prave slugove. */
    /**
     * Milano je imao BGY (Bergamo) pa su slugovi "bergamo". Admin menja kod na MXP,
     * englesko ime postaje "Milan": stari slugovi se brišu i popunjavaju iz novog
     * imena. Airalo ostaje - država je ista. Dosledni slugovi se ne diraju.
     */
    @Test
    void zastareliSlugoviSeBrisuKadSePromeniGrad() {
        Destination d = dest("Milano", "Milan", "Italy");
        d.setGygSlug("bergamo-l123");
        d.setBounceSlug("bergamo");
        d.setBounceCovered(true);
        d.setAiraloSlug("italy-esim");

        PartnerSlugFiller f = filler(AIRALO, Set.of("milan", "bergamo"));
        assertTrue(f.ocistiZastarele(d), "nesto je bilo zastarelo");
        assertNull(d.getGygSlug(),    "gyg iz starog grada obrisan - GYG prolaz ga popunjava u pozadini");
        assertNull(d.getBounceSlug(), "bounce iz starog grada obrisan");
        assertFalse(d.getBounceCovered());
        assertEquals("italy-esim", d.getAiraloSlug(), "drzava ista - ostaje");

        f.popuniBrzeSlugove(d);
        assertEquals("milan", d.getBounceSlug(), "popunjen iz NOVOG imena");
        assertTrue(d.getBounceCovered());
        assertFalse(f.ocistiZastarele(d), "dosledni slugovi se ne diraju");

        assertTrue(PartnerSlugFiller.gygVaziZa("milan-l70", "Milan"));
        assertFalse(PartnerSlugFiller.gygVaziZa("bergamo-l123", "Milan"));
        assertTrue(PartnerSlugFiller.gygVaziZa("bergamo-l123", null), "bez imena nema osnova za sud");
        assertFalse(PartnerSlugFiller.gygVaziZa(null, "Milan"));
        assertTrue(PartnerSlugFiller.airaloVaziZa("czech-republic-esim", "Czech Republic"));
        assertFalse(PartnerSlugFiller.airaloVaziZa("italy-esim", "Czech Republic"));
    }

    @Test
    void normalizacijaImena() {
        assertEquals("prague", PartnerSlugFiller.kljuc("Prague"));
        assertEquals("czech-republic", PartnerSlugFiller.kljuc("Czech Republic"));
        assertEquals("malmo", PartnerSlugFiller.kljuc("Malmö"));
        assertEquals("zurich", PartnerSlugFiller.kljuc("Zürich"));
        assertEquals("united-kingdom", PartnerSlugFiller.kljuc("United Kingdom"));
        assertEquals("", PartnerSlugFiller.kljuc(null));
    }
}
