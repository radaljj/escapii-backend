package com.escapii.service.impl;

import com.escapii.model.Destination;
import com.escapii.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sklapa partnerske linkove za popup koji se nudi kupcu odmah posle otkrivanja
 * destinacije (ture, eSIM, čuvanje prtljaga).
 *
 * <p><b>Zašto šabloni a ne sklapanje URL-a u kodu.</b> Sva tri partnera prosleđuju
 * partnerski ID drugačije: GetYourGuide kao običan query parametar, Airalo kroz
 * Impact redirekciju sa odredištem u {@code ?u=}, a Bounce svojim oblikom koji
 * uopšte nije javno dokumentovan - dobija se tek posle odobrenja. Da je sklapanje
 * zakucano u kodu, treći partner bi tražio izmenu i deploy. Ovako je svaki
 * partner jedna env varijabla sa {@code {slug}} rupom, pa se menja na serveru.
 *
 * <p><b>Prazan šablon = nema kartice.</b> Dok partnerski nalog nije odobren, env
 * varijabla je prazna i ta kartica se ne prikazuje. Time smemo da pustimo popup
 * u produkciju pre nego što ijedan program bude odobren.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelAddonsService {

    /** Rupa u šablonu koju menja slug destinacije. */
    private static final String SLUG = "{slug}";

    private final DestinationRepository destinationRepository;

    /** npr. https://www.getyourguide.com/sr-rs/{slug}/?partner_id=XXXX */
    @Value("${app.affiliate.gyg-url-template:}")
    private String gygTemplate;

    /** npr. https://airalo.pxf.io/c/A/B/C?u=https%3A%2F%2Fwww.airalo.com%2F{slug} */
    @Value("${app.affiliate.airalo-url-template:}")
    private String airaloTemplate;

    /** npr. https://bounce.com/luggage-storage/{slug} (+ njihov ref parametar kad ga dobijemo) */
    @Value("${app.affiliate.bounce-url-template:}")
    private String bounceTemplate;

    /**
     * Vraća linkove za destinaciju koju je admin rukom upisao na rezervaciji.
     * Ključevi su {@code tours}, {@code esim}, {@code luggage} - isti kao
     * {@code data-ao-slot} atributi na kkarticama u popupu.
     *
     * <p>Mapa sadrži SAMO linkove koje stvarno umemo da napravimo. Prazna mapa
     * znači da se popup ne prikazuje uopšte; frontend ne sme da izmišlja
     * zamensku vrednost niti da vodi na početnu stranicu partnera.
     */
    @Transactional(readOnly = true)
    public Map<String, String> linksFor(String assignedDestination) {
        Map<String, String> links = new LinkedHashMap<>();
        if (assignedDestination == null || assignedDestination.isBlank()) {
            return links;
        }

        Destination dest = resolve(assignedDestination);
        if (dest == null) {
            // Nije greška nego očekivano stanje: admin je otkucao ime koje ne
            // postoji u tabeli destinacija (drugačiji zapis, novi grad, tipfeler).
            // Popup jednostavno izostane - bolje nego pogrešan link.
            log.info("[Dodaci] Destinacija '{}' nema odgovarajući red u bazi - popup se preskače",
                    assignedDestination);
            return links;
        }

        addIfPossible(links, "tours",   gygTemplate,    dest.getGygSlug());
        addIfPossible(links, "esim",    airaloTemplate, dest.getAiraloSlug());

        // Prtljag ima uslov više: slug ume biti tačan a grad nepokriven.
        if (Boolean.TRUE.equals(dest.getBounceCovered())) {
            addIfPossible(links, "luggage", bounceTemplate, dest.getBounceSlug());
        }

        return links;
    }

    /**
     * Traži destinaciju po imenu. Ako se poklopi više redova (isto ime na dva
     * aerodroma), uzima prvi i to zapisuje - podatak je dvosmislen i vredi da se
     * vidi u logu, ali nije razlog da kupac ostane bez popupa.
     */
    private Destination resolve(String naziv) {
        List<Destination> found = destinationRepository.findByAnyNameIgnoreCase(naziv);
        if (found.isEmpty()) return null;
        if (found.size() > 1) {
            log.warn("[Dodaci] Ime '{}' poklapa se sa {} destinacija - uzimam id={}",
                    naziv, found.size(), found.get(0).getId());
        }
        return found.get(0);
    }

    /** Ubacuje link samo ako imamo i šablon (partner odobren) i slug (skript pušten). */
    private void addIfPossible(Map<String, String> links, String kljuc, String sablon, String slug) {
        if (sablon == null || sablon.isBlank()) return;
        if (slug == null || slug.isBlank()) return;
        if (!sablon.contains(SLUG)) {
            log.warn("[Dodaci] Šablon za '{}' nema {} rupu - preskačem da ne bih poslao pogrešan link",
                    kljuc, SLUG);
            return;
        }
        links.put(kljuc, sablon.replace(SLUG, slug.trim()));
    }
}
