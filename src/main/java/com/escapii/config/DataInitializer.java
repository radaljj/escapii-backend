package com.escapii.config;

import com.escapii.model.AvailableDate;
import com.escapii.model.Destination;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Puni bazu inicijalnim podacima pri prvom pokretanju aplikacije.
 *
 * DESTINACIJE:
 *   Svi direktni letovi ka leisure destinacijama iz BEG i INI.
 *   Izvor: Air Serbia, Ryanair, Wizz Air, Eurowings rutne mape (mart 2026).
 *   Isključeno: domaći letovi (BEG↔INI), čisto poslovni hubovi.
 *
 * DOSTUPNI DATUMI (Option B iz specifikacije):
 *   Predefinisani vikend i produženi termini kreirati ručno od strane tima.
 *   Tim Escapii dodaje nove termine direktno u bazu.
 *
 * Podaci se insertuju SAMO ako tabela vec nije popunjena (idempotentno).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final DestinationRepository   destinationRepository;
    private final AvailableDateRepository availableDateRepository;

    @Override
    public void run(ApplicationArguments args) {
        initDestinations();
    }

    // ─── Destinacije ──────────────────────────────────────────────────────────
    //
    // Format: dest(srpski naziv, IATA odredišta, država, region, aerodroми polaska...)
    //
    // airportCode = IATA kod ODREDIŠNOG aerodroma (kuda se leti).
    // departureAirports = sa kog aerodroma postoji let ka toj destinaciji.

    private void initDestinations() {
        if (destinationRepository.count() > 0) return;

        List<Destination> destinations = List.of(

            // ── BEG - Air Serbia ───────────────────────────────────────────────

            dest("Atina",         "ATH", "Grčka",               "Jugoistočna Evropa",  "BEG"),
                dest("Beč",           "VIE", "Austrija",            "Centralna Evropa",    "BEG", "INI"),
                dest("Brisel",        "BRU", "Belgija",             "Zapadna Evropa",      "BEG"),
                dest("Budimpešta",    "BUD", "Mađarska",            "Centralna Evropa",    "BEG"),
                dest("Cirih",         "ZRH", "Švajcarska",          "Centralna Evropa",    "BEG"),
                dest("Frankfurt",     "FRA", "Nemačka",             "Centralna Evropa",    "BEG", "INI"),
                dest("Istanbul",      "IST", "Turska",              "Bliski istok",        "BEG", "INI"),
                dest("Krakow",        "KRK", "Poljska",             "Centralna Evropa",    "BEG"),
                dest("Larnaka",       "LCA", "Kipar",               "Mediteran",           "BEG"),
                dest("Lisabon",       "LIS", "Portugal",            "Zapadna Evropa",      "BEG"),
                dest("Ljubljana",     "LJU", "Slovenija",           "Centralna Evropa",    "BEG", "INI"),
                dest("London",        "LHR", "Velika Britanija",    "Zapadna Evropa",      "BEG"),
                dest("Madrid",        "MAD", "Španija",             "Zapadna Evropa",      "BEG"),
                dest("Minhen",        "MUC", "Nemačka",             "Centralna Evropa",    "BEG"),
                dest("Pariz",         "CDG", "Francuska",           "Zapadna Evropa",      "BEG"),
                dest("Prag",          "PRG", "Češka",               "Centralna Evropa",    "BEG"),
                dest("Rim",           "FCO", "Italija",             "Zapadna Evropa",      "BEG"),
                //dest("Rodos",         "RHO", "Grčka",               "Mediteran",           "BEG"),
                dest("Podgorica",     "TGD", "Crna Gora",           "Jugoistočna Evropa",  "BEG"),
                dest("Sofija",        "SOF", "Bugarska",            "Jugoistočna Evropa",  "BEG"),
                dest("Tivat",         "TIV", "Crna Gora",           "Mediteran",           "BEG"),
                dest("Solun",         "SKG", "Grčka",               "Jugoistočna Evropa",  "BEG"),
                dest("Bukurešt",      "OTP", "Rumunija",            "Jugoistočna Evropa",  "BEG"),
                dest("Varšava",       "WAW", "Poljska",             "Centralna Evropa",    "BEG"),
                dest("Venecija",      "VCE", "Italija",             "Zapadna Evropa",      "BEG"),
                dest("Zagreb",        "ZAG", "Hrvatska",            "Centralna Evropa",    "BEG"),
                dest("Ženeva",        "GVA", "Švajcarska",          "Centralna Evropa",    "BEG"),

                // ── BEG - Wizz Air (bez duplikata gradova) ─────────────────────────

            dest("Alikante",      "ALC", "Španija",             "Mediteran",           "BEG"),
            dest("Fridrihshafen", "FDH", "Nemačka",             "Centralna Evropa",    "BEG"),
            dest("Geteborg",      "GOT", "Švedska",             "Severna Evropa",      "BEG"),
            dest("Hamburg",       "HAM", "Nemačka",             "Centralna Evropa",    "BEG"),
            dest("Karlsrue",      "FKB", "Nemačka",             "Centralna Evropa",    "BEG"),  // Baden-Baden
            dest("Krit",          "HER", "Grčka",               "Mediteran",           "BEG"),
            dest("Malme",         "MMX", "Švedska",             "Severna Evropa",      "BEG"),
            dest("Memingen",      "FMM", "Nemačka",             "Centralna Evropa",    "BEG", "INI"),  // Munich West
            dest("Milano", "BGY", "Italija",             "Zapadna Evropa",      "BEG"),
            dest("Sardinija",     "AHO", "Italija",             "Mediteran",           "BEG"),
            dest("Sicilija",      "PMO", "Italija",             "Mediteran",           "BEG"),
            dest("Firenca",          "FLR", "Italija",             "Zapadna Evropa",      "BEG"),  // Toskana (Amerigo Vespucci)

            // ── BEG only ───────────────────────────────────────────────────────

            dest("Barcelona",     "BCN", "Španija",             "Zapadna Evropa",      "BEG"),
            dest("Berlin",        "BER", "Nemačka",             "Centralna Evropa",    "BEG"),
            dest("Nica",          "NCE", "Francuska",           "Zapadna Evropa",      "BEG"),
            dest("Stokholm",      "ARN", "Švedska",             "Severna Evropa",      "BEG"),

            // ── BEG only ───────────────────────────────────────────────────────

            dest("Amsterdam",         "AMS", "Holandija",           "Zapadna Evropa",      "BEG"),
            dest("Bazel-Muluz",        "MLH", "Švajcarska/Francuska","Centralna Evropa",    "BEG"),

            // ── BEG + INI ──────────────────────────────────────────────────────

            dest("Dortmund",      "DTM", "Nemačka",             "Centralna Evropa",    "BEG", "INI"),
            dest("Keln",          "CGN", "Nemačka",             "Centralna Evropa",    "BEG", "INI"),
            dest("Malta",         "MLA", "Malta",               "Mediteran",           "BEG", "INI")
        );

        destinationRepository.saveAll(destinations);
        log.info("[DataInitializer] Ucitano {} destinacija.", destinations.size());
    }


    // ─── Factory helpers ──────────────────────────────────────────────────────

    private Destination dest(String name, String airportCode, String country, String region,
                             String... departureAirports) {
        Destination d = new Destination();
        d.setName(name);
        d.setAirportCode(airportCode);
        d.setCountry(country);
        d.setRegion(region);
        d.setActive(true);
        d.setDepartureAirports(new HashSet<>(Arrays.asList(departureAirports)));
        return d;
    }

    private AvailableDate date(
            String airport,
            int depY, int depM, int depD,
            int retY, int retM, int retD,
            int nights, int basePrice, int slots
    ) {
        AvailableDate ad = new AvailableDate();
        ad.setDepartureAirport(airport);
        ad.setDepartureDate(LocalDate.of(depY, depM, depD));
        ad.setReturnDate(LocalDate.of(retY, retM, retD));
        ad.setNumberOfNights(nights);
        ad.setBasePrice(basePrice);
        ad.setAvailableSlots(slots);
        ad.setActive(true);
        return ad;
    }
}
