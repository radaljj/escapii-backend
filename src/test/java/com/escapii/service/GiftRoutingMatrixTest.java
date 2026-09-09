package com.escapii.service;

import com.escapii.model.Booking;
import com.escapii.model.PassengerInfo;
import com.escapii.service.email.core.EmailHtmlBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ispisuje CELU matricu: ko dobija koji mejl i kako mu se obraca, u svakom
 * slucaju koji rezervacija moze da ima.
 *
 * <p>Ovo je i test i dokumentacija. Tvrdnje su na dnu svakog slucaja, ali izlaz
 * je namerno citljiv, jer je pitanje "ko sta dobija" bilo postavljeno vise puta
 * i svaki put se odgovaralo iz glave. Sada odgovara program.
 *
 * <p>Pokreni sam:
 * {@code ./mvnw -o test -Dtest=GiftRoutingMatrixTest}
 */
class GiftRoutingMatrixTest {

    // ── Mejlovi, po redu kojim se desavaju ──────────────────────────────────
    private record Mejl(String naziv, boolean putni, boolean imaObracanje) {}

    private static final List<Mejl> MEJLOVI = List.of(
        new Mejl("1. Potvrda upita",      false, true),
        new Mejl("2. Profaktura",         false, true),
        new Mejl("3. Potvrda rezervacije",false, true),
        new Mejl("4. Otkazivanje",        false, true),
        new Mejl("5. Prognoza",           true,  false),
        new Mejl("6. Reveal",             true,  false),
        new Mejl("7. Putni dokumenti",    true,  true)
    );

    private Booking rez(boolean poklon, String imeObdarenog, String mejlObdarenog,
                        String polPrvog, List<PassengerInfo> putnici) {
        Booking b = new Booking();
        b.setBookingRef("ESC-primer01");
        b.setFirstName("Marko");
        b.setLastName("Marković");
        b.setEmail("marko@primer.rs");
        b.setLeadPassengerGender(polPrvog);
        b.setPassengers(putnici);
        b.setIsGift(poklon);
        b.setGiftRecipientName(imeObdarenog);
        b.setGiftRecipientEmail(mejlObdarenog);
        return b;
    }

    private PassengerInfo p(String ime, String pol) {
        return new PassengerInfo(ime, pol, null, null, true, null, null);
    }

    /** Jedan red matrice za jedan mejl. */
    private String red(Booking b, Mejl m) {
        String prima = m.putni() ? b.travellerEmail() : b.getEmail();
        String obracanje;
        if (!m.imaObracanje()) {
            obracanje = "(bez pozdrava)";
        } else if (m.putni()) {
            obracanje = EmailHtmlBuilder.salutation() + " " + b.travellerFirstName() + ",";
        } else {
            obracanje = EmailHtmlBuilder.salutation() + " " + b.getFirstName() + ",";
        }
        return String.format("  %-24s %-22s %s", m.naziv(), prima, obracanje);
    }

    private void ispisi(String naslov, String opis, Booking b) {
        System.out.println();
        System.out.println("=== " + naslov + " ===");
        System.out.println("  " + opis);
        System.out.println(String.format("  %-24s %-22s %s", "MEJL", "PRIMA", "OBRACANJE"));
        System.out.println("  " + "-".repeat(70));
        for (Mejl m : MEJLOVI) System.out.println(red(b, m));
        System.out.println("  reveal stranica -> naslov glasi na: " + b.travellerFirstName()
                + "   |   cena: "
                + (Boolean.TRUE.equals(b.getIsGift()) ? "SAKRIVENA" : "prikazana"));
    }

    @Test
    void celaMatrica() {
        // ── A: obicna rezervacija, nosilac muskarac ─────────────────────────
        Booking a = rez(false, null, null, "M",
                List.of(p("Marko Marković", "M"), p("Dragan Radalj", "M")));
        ispisi("A - BEZ POKLONA, nosilac muskarac",
               "Marko rezervise za sebe i Dragana.", a);
        assertEquals("marko@primer.rs", a.travellerEmail());
        assertEquals("Marko", a.travellerFirstName());

        // ── B: obicna rezervacija, nosilac zena ────────────────────────────
        Booking b = rez(false, null, null, "F",
                List.of(p("Ana Anić", "F")));
        b.setFirstName("Ana");
        b.setEmail("ana@primer.rs");
        ispisi("B - BEZ POKLONA, nosilac zena",
               "Ana rezervise za sebe. Sve ide njoj, kao i pre ove izmene.", b);
        assertEquals("ana@primer.rs", b.travellerEmail());

        // ── C: poklon, obdareni je PRVI putnik ─────────────────────────────
        Booking c = rez(true, "Marko Marković", "marko.putnik@primer.rs", "M",
                List.of(p("Marko Marković", "M"), p("Dragan Radalj", "M")));
        c.setFirstName("Jelena");
        c.setEmail("jelena@primer.rs");
        c.setLeadPassengerGender("M");
        ispisi("C - POKLON, obdareni je prvi putnik",
               "Jelena placa, putuje Marko (prvi na listi).", c);
        assertEquals("marko.putnik@primer.rs", c.travellerEmail());
        assertEquals("jelena@primer.rs", c.getEmail());
        assertEquals("Marko", c.travellerFirstName());

        // ── D: poklon, obdareni je DRUGI putnik i drugog pola ──────────────
        Booking d = rez(true, "Ana Anić", "ana@primer.rs", "M",
                List.of(p("Marko Marković", "M"), p("Ana Anić", "F")));
        ispisi("D - POKLON, obdareni je drugi putnik, drugog pola",
               "Marko placa, putuje Ana. Ranije bi joj dokumenti stigli sa kupcevim imenom.", d);
        assertEquals("ana@primer.rs", d.travellerEmail());
        assertEquals("Ana", d.travellerFirstName());

        // ── E: poklon, ime obdarenog nije na listi putnika ─────────────────
        Booking e = rez(true, "Neko Drugi", "neko@primer.rs", "M",
                List.of(p("Marko Marković", "M")));
        ispisi("E - POKLON, ime obdarenog nije na listi putnika",
               "Lista je izmenjena posle izbora. Ime se svejedno koristi kako je uneto.", e);
        assertEquals("neko@primer.rs", e.travellerEmail());
        assertEquals("Neko", e.travellerFirstName());

        // ── F: poklon bez mejla obdarenog ──────────────────────────────────
        Booking f = rez(true, "Ana Anić", null, "M",
                List.of(p("Marko Marković", "M"), p("Ana Anić", "F")));
        ispisi("F - POKLON bez mejla obdarenog (forma i baza to sprecavaju)",
               "Ako ipak prodje: putni mejl pada nazad na kupca umesto da nestane.", f);
        assertEquals("marko@primer.rs", f.travellerEmail(),
                "mejl koji nema kome da ode je gori ishod od mejla koji ode kupcu");
        assertEquals("Marko", f.travellerFirstName(),
                "kad adresa pada nazad na kupca, ime mora sa njom");

        // ── G: poklon, obdareni ima samo ime bez prezimena ─────────────────
        Booking g = rez(true, "Dragan", "dragan@primer.rs", "M",
                List.of(p("Dragan", "M")));
        ispisi("G - POKLON, obdareni bez prezimena",
               "Ime ostaje celo, ne skracuje se u prazno.", g);
        assertEquals("Dragan", g.travellerFirstName());

        System.out.println();
        System.out.println("=== PRAVILO ===");
        System.out.println("  Mejlovi 1-4 (novac i stanje rezervacije) uvek idu KUPCU.");
        System.out.println("  Mejlovi 5-7 (put) idu OBDARENOM kad je poklon, inace kupcu.");
        System.out.println("  Kad nije poklon, sve pada na kupca i ponasa se kao pre izmene.");
        System.out.println();
    }
}
