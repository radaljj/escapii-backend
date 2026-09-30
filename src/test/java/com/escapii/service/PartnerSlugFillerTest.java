package com.escapii.service;

import com.escapii.model.Destination;
import com.escapii.repository.DestinationRepository;
import com.escapii.service.impl.PartnerSlugFiller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    /** Koliko puta je pokrenut GYG prolaz (pravi bi skidao 96 MB sitemapa). */
    private final java.util.concurrent.atomic.AtomicInteger gygProlaza = new java.util.concurrent.atomic.AtomicInteger();

    /** Podmeće spiskove umesto mrežnog poziva; lookup bez airports.dat (samo tabela ispravki). */
    private PartnerSlugFiller filler(Set<String> holafly, Set<String> bounce) {
        return new PartnerSlugFiller(destinationRepository, new com.escapii.service.AirportLookupService()) {
            @Override protected Set<String> holaflySpisak() { return holafly; }
            @Override protected Set<String> bounceSpisak() { return bounce; }
            @Override public void popuniGygSlugoveUPozadini() { gygProlaza.incrementAndGet(); }
        };
    }

    private static final Set<String> HOLAFLY = Set.of("esim-czech-republic", "esim-italy", "esim-germany",
            "esim-usa", "esim-arab-emirates", "esim-north-macedonia");
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

        filler(HOLAFLY, BOUNCE).popuniBrzeSlugove(d);

        assertEquals("esim-czech-republic", d.getHolaflySlug(), "razmak postaje crtica, prefiks esim-");
        assertEquals("prague", d.getBounceSlug());
        assertTrue(d.getBounceCovered());
    }

    /** Holafly nekoliko država piše drugačije nego airports.dat - tabela izuzetaka, proverena u sitemapu. */
    @Test
    void drzaveKojeHolaflyPiseDrugacije() {
        assertEquals("esim-usa",             PartnerSlugFiller.holaflySlugZa("United States"));
        assertEquals("esim-arab-emirates",   PartnerSlugFiller.holaflySlugZa("United Arab Emirates"));
        assertEquals("esim-north-macedonia", PartnerSlugFiller.holaflySlugZa("Macedonia"));
        assertEquals("esim-north-macedonia", PartnerSlugFiller.holaflySlugZa("North Macedonia"));
        assertEquals("esim-czech-republic",  PartnerSlugFiller.holaflySlugZa("Czech Republic"));
        assertNull(PartnerSlugFiller.holaflySlugZa(null));
        assertNull(PartnerSlugFiller.holaflySlugZa("  "));

        Destination njujork = dest("Njujork", "New York", "United States");
        filler(HOLAFLY, BOUNCE).popuniBrzeSlugove(njujork);
        assertEquals("esim-usa", njujork.getHolaflySlug());

        assertTrue(PartnerSlugFiller.holaflyVaziZa("esim-usa", "United States"));
        assertFalse(PartnerSlugFiller.holaflyVaziZa("esim-united-states", "United States"), "takve stranice kod njih nema");
    }

    /** Grad kog nema na Bounce spisku - kartica za prtljag mora izostati. */
    @Test
    void gradVanBounceSpiskaNijePokriven() {
        Destination d = dest("Memingen", "Memmingen", "Germany");

        filler(HOLAFLY, BOUNCE).popuniBrzeSlugove(d);

        assertNull(d.getBounceSlug(), "slug se ne upisuje ako grad nije na spisku");
        assertFalse(d.getBounceCovered());
        assertEquals("esim-germany", d.getHolaflySlug(), "eSIM i dalje radi, vezan je za drzavu");
    }

    /** Drzava koje nema kod Holafly-ja - ne nagadjamo, ostaje prazno. */
    @Test
    void drzavaVanHolaflySpiskaOstajePrazna() {
        Destination d = dest("Havana", "Havana", "Cuba");

        assertFalse(filler(HOLAFLY, BOUNCE).popuniBrzeSlugove(d), "nista se nije promenilo");

        assertNull(d.getHolaflySlug(), "bolje prazno nego slug koji vodi na 404");
    }

    /** Ono sto je admin uneo rukom ne sme da se pregazi automatikom. */
    @Test
    void rucnoUnetaVrednostSeNePregazi() {
        Destination d = dest("Prag", "Prague", "Czech Republic");
        d.setHolaflySlug("moj-rucni-slug");
        d.setBounceSlug("moj-rucni-grad");

        filler(HOLAFLY, BOUNCE).popuniBrzeSlugove(d);

        assertEquals("moj-rucni-slug", d.getHolaflySlug());
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

        filler(HOLAFLY, BOUNCE).popuniBrzeSlugove(biviNepokriven);

        assertTrue(biviNepokriven.getBounceCovered(), "Bounce ih sada ima - kartica se pali");

        Destination viseNijeNaSpisku = dest("Neki Grad", "SomeCity", "Germany");
        viseNijeNaSpisku.setBounceSlug("somecity");
        viseNijeNaSpisku.setBounceCovered(true);

        filler(HOLAFLY, BOUNCE).popuniBrzeSlugove(viseNijeNaSpisku);

        assertFalse(viseNijeNaSpisku.getBounceCovered(), "vise ih nema - kartica se gasi");
    }

    /**
     * Ako se partnerski spisak ne skine (mreza, 403, promena formata), ne smemo ni
     * upisati nagadjanje ni obrisati ono sto vec stoji.
     */
    @Test
    void nedostupanSpisakNeDiraPostojeceVrednosti() {
        Destination d = dest("Prag", "Prague", "Czech Republic");
        d.setHolaflySlug("esim-czech-republic");
        d.setBounceSlug("prague");
        d.setBounceCovered(true);

        filler(Set.of(), Set.of()).popuniBrzeSlugove(d);

        assertEquals("esim-czech-republic", d.getHolaflySlug());
        assertEquals("prague", d.getBounceSlug());
        assertTrue(d.getBounceCovered());
    }

    @Test
    void bezEngleskihNazivaNemaSta() {
        Destination d = dest("Neka Destinacija", null, null);

        filler(HOLAFLY, BOUNCE).popuniBrzeSlugove(d);

        assertNull(d.getHolaflySlug());
        assertNull(d.getBounceSlug());
    }

    /** Dijakritika i razmaci se svode isto kako partneri prave slugove. */
    /**
     * Milano je imao BGY (Bergamo) pa su slugovi "bergamo". Admin menja kod na MXP,
     * englesko ime postaje "Milan": stari slugovi se brišu i popunjavaju iz novog
     * imena. Holafly ostaje - država je ista. Dosledni slugovi se ne diraju.
     */
    @Test
    void zastareliSlugoviSeBrisuKadSePromeniGrad() {
        Destination d = dest("Milano", "Milan", "Italy");
        d.setGygSlug("bergamo-l123");
        d.setBounceSlug("bergamo");
        d.setBounceCovered(true);
        d.setHolaflySlug("esim-italy");

        PartnerSlugFiller f = filler(HOLAFLY, Set.of("milan", "bergamo"));
        assertTrue(f.ocistiZastarele(d), "nesto je bilo zastarelo");
        assertNull(d.getGygSlug(),    "gyg iz starog grada obrisan - GYG prolaz ga popunjava u pozadini");
        assertNull(d.getBounceSlug(), "bounce iz starog grada obrisan");
        assertFalse(d.getBounceCovered());
        assertEquals("esim-italy", d.getHolaflySlug(), "drzava ista - ostaje");

        f.popuniBrzeSlugove(d);
        assertEquals("milan", d.getBounceSlug(), "popunjen iz NOVOG imena");
        assertTrue(d.getBounceCovered());
        assertFalse(f.ocistiZastarele(d), "dosledni slugovi se ne diraju");

        assertTrue(PartnerSlugFiller.gygVaziZa("milan-l70", "Milan"));
        assertFalse(PartnerSlugFiller.gygVaziZa("bergamo-l123", "Milan"));
        assertTrue(PartnerSlugFiller.gygVaziZa("bergamo-l123", null), "bez imena nema osnova za sud");
        assertFalse(PartnerSlugFiller.gygVaziZa(null, "Milan"));
        assertTrue(PartnerSlugFiller.holaflyVaziZa("esim-czech-republic", "Czech Republic"));
        assertFalse(PartnerSlugFiller.holaflyVaziZa("esim-italy", "Czech Republic"));
        assertFalse(PartnerSlugFiller.holaflyVaziZa("czech-republic-esim", "Czech Republic"), "Airalo oblik nije Holafly oblik");
    }

    /**
     * Prod slučaj: Milano preko MXP je iz airports.dat dobio "Milano" (italijanski),
     * pa partnerski slugovi ("milan") nisu mogli da se nađu, a stari "bergamo" su
     * ostali. Startni prolaz: ime iz koda (ispravka MXP -> Milan), brisanje
     * zastarelih, popunjavanje iz novog imena, GYG prolaz jer fali.
     */
    @Test
    void startniProlaz_imeIzKoda_zastareliSlugovi_gygPozadina() {
        Destination milano = dest("Milano", "Milano", "Italy");
        milano.setId(35L);
        milano.setAirportCode("MXP");
        milano.setGygSlug("bergamo-l123");
        milano.setBounceSlug("bergamo");
        milano.setBounceCovered(true);
        milano.setHolaflySlug("esim-italy");
        Destination firenca = dest("Firenca", "Florence", "Italy");
        firenca.setAirportCode("FLR");
        firenca.setGygSlug("florence-l32");
        firenca.setBounceSlug("florence");
        firenca.setBounceCovered(true);
        firenca.setHolaflySlug("esim-italy");
        when(destinationRepository.findAll()).thenReturn(List.of(milano, firenca));

        PartnerSlugFiller f = filler(HOLAFLY, Set.of("milan", "florence", "bergamo"));
        f.osveziSveNaStartu();

        assertEquals("Milan", milano.getNameEn(), "ime iz ispravke za MXP");
        assertNull(milano.getGygSlug(), "bergamo obrisan, GYG ce popuniti u pozadini");
        assertEquals("milan", milano.getBounceSlug(), "bounce popunjen iz novog imena");
        assertTrue(milano.getBounceCovered());
        assertEquals("esim-italy", milano.getHolaflySlug());
        verify(destinationRepository).save(milano);
        verify(destinationRepository, never()).save(firenca);   // dosledna - ne dira se
        assertEquals(1, gygProlaza.get(), "GYG prolaz pokrenut jer Milanu fali slug");

        // drugi prolaz: nema sta da se menja, GYG ide samo ako i dalje fali
        f.osveziSveNaStartu();
        assertEquals("Milan", milano.getNameEn());
        assertEquals(2, gygProlaza.get());
    }

    /**
     * Holafly je zamenio Airalo (2026-10): postojeće destinacije imaju sve ostale slugove,
     * a njegov ne. Startni prolaz ga popunjava bez klika u panelu. Destinacija čije
     * države nema na spisku se ne upisuje (nema šta), a dosledna se ne dira.
     */
    @Test
    void startniProlazPopunjavaSlugNovogPartnera() {
        Destination firenca = dest("Firenca", "Florence", "Italy");
        firenca.setAirportCode("FLR");
        firenca.setGygSlug("florence-l32");
        firenca.setBounceSlug("florence");
        firenca.setBounceCovered(true);
        Destination havana = dest("Havana", "Havana", "Cuba");
        havana.setAirportCode("HAV");
        havana.setGygSlug("havana-l1");
        havana.setBounceSlug("havana");
        havana.setBounceCovered(true);
        Destination prag = dest("Prag", "Prague", "Czech Republic");
        prag.setAirportCode("PRG");
        prag.setGygSlug("prague-l10");
        prag.setBounceSlug("prague");
        prag.setBounceCovered(true);
        prag.setHolaflySlug("esim-czech-republic");
        when(destinationRepository.findAll()).thenReturn(List.of(firenca, havana, prag));

        filler(HOLAFLY, Set.of("florence", "havana", "prague")).osveziSveNaStartu();

        assertEquals("esim-italy", firenca.getHolaflySlug(), "popunjen iz drzave, bez izmene u panelu");
        verify(destinationRepository).save(firenca);
        assertNull(havana.getHolaflySlug(), "Kube nema kod Holafly-ja - ostaje prazno");
        verify(destinationRepository, never()).save(havana);
        verify(destinationRepository, never()).save(prag);
        assertEquals(0, gygProlaza.get(), "GYG slugovi postoje - prolaz se ne pokrece");
    }

    /** Reveal koji naiđe na zastareo slug osvežava tu destinaciju u pozadini. */
    @Test
    void osvezavanjeJedneDestinacije() {
        Destination milano = dest("Milano", "Milano", "Italy");
        milano.setId(35L);
        milano.setAirportCode("MXP");
        milano.setGygSlug("bergamo-l123");
        when(destinationRepository.findById(35L)).thenReturn(java.util.Optional.of(milano));

        PartnerSlugFiller f = filler(HOLAFLY, Set.of("milan"));
        f.osveziDestinacijuUPozadini(35L);

        assertEquals("Milan", milano.getNameEn());
        assertNull(milano.getGygSlug());
        assertEquals("milan", milano.getBounceSlug());
        verify(destinationRepository).save(milano);
        assertEquals(1, gygProlaza.get());
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
