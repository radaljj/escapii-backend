package com.escapii.service.impl;

import com.escapii.dto.CountryDto;
import com.escapii.model.Destination;
import com.escapii.repository.DestinationRepository;
import com.escapii.service.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DestinationServiceImpl implements DestinationService {

    private final DestinationRepository destinationRepository;

    /**
     * Final passport list - only sovereign states and special territories
     * that issue their own passports. Territories using the passport of their
     * administering country (e.g. French overseas, British overseas, US territories)
     * are excluded.
     *
     * Special cases kept:
     *   HK - HKSAR passport (distinct from CN)
     *   MO - MSAR passport (distinct from CN)
     *   TW - ROC passport
     *   XK - Kosovo passport
     *   PS - Palestinian Authority passport
     *   FO - Faroese passport (distinct from DK)
     *   GG - Guernsey passport
     *   IM - Isle of Man passport
     *   JE - Jersey passport
     *   VA - Vatican passport
     */
    private static final List<CountryDto> PASSPORTS = List.of(
        new CountryDto("AD", "Andorra",                   "Andora"),
        new CountryDto("AE", "United Arab Emirates",      "Ujedinjeni Arapski Emirati"),
        new CountryDto("AF", "Afghanistan",               "Avganistan"),
        new CountryDto("AG", "Antigua and Barbuda",       "Antigva i Barbuda"),
        new CountryDto("AL", "Albania",                   "Albanija"),
        new CountryDto("AM", "Armenia",                   "Jermenija"),
        new CountryDto("AO", "Angola",                    "Angola"),
        new CountryDto("AR", "Argentina",                 "Argentina"),
        new CountryDto("AT", "Austria",                   "Austrija"),
        new CountryDto("AU", "Australia",                 "Australija"),
        new CountryDto("AZ", "Azerbaijan",                "Azerbejdžan"),
        new CountryDto("BA", "Bosnia and Herzegovina",    "Bosna i Hercegovina"),
        new CountryDto("BB", "Barbados",                  "Barbados"),
        new CountryDto("BD", "Bangladesh",                "Bangladeš"),
        new CountryDto("BE", "Belgium",                   "Belgija"),
        new CountryDto("BF", "Burkina Faso",              "Burkina Faso"),
        new CountryDto("BG", "Bulgaria",                  "Bugarska"),
        new CountryDto("BH", "Bahrain",                   "Bahrein"),
        new CountryDto("BI", "Burundi",                   "Burundi"),
        new CountryDto("BJ", "Benin",                     "Benin"),
        new CountryDto("BN", "Brunei",                    "Brunej"),
        new CountryDto("BO", "Bolivia",                   "Bolivija"),
        new CountryDto("BR", "Brazil",                    "Brazil"),
        new CountryDto("BS", "Bahamas",                   "Bahami"),
        new CountryDto("BT", "Bhutan",                    "Butan"),
        new CountryDto("BW", "Botswana",                  "Bocvana"),
        new CountryDto("BY", "Belarus",                   "Belorusija"),
        new CountryDto("BZ", "Belize",                    "Belize"),
        new CountryDto("CA", "Canada",                    "Kanada"),
        new CountryDto("CD", "DR Congo",                  "DR Kongo"),
        new CountryDto("CF", "Central African Republic",  "Centralnoafrička Republika"),
        new CountryDto("CG", "Republic of the Congo",     "Republika Kongo"),
        new CountryDto("CH", "Switzerland",               "Švajcarska"),
        new CountryDto("CI", "Ivory Coast",               "Obala Slonovače"),
        new CountryDto("CL", "Chile",                     "Čile"),
        new CountryDto("CM", "Cameroon",                  "Kamerun"),
        new CountryDto("CN", "China",                     "Kina"),
        new CountryDto("CO", "Colombia",                  "Kolumbija"),
        new CountryDto("CR", "Costa Rica",                "Kostarika"),
        new CountryDto("CU", "Cuba",                      "Kuba"),
        new CountryDto("CV", "Cape Verde",                "Zelenortska Ostrva"),
        new CountryDto("CY", "Cyprus",                    "Kipar"),
        new CountryDto("CZ", "Czechia",                   "Češka"),
        new CountryDto("DE", "Germany",                   "Nemačka"),
        new CountryDto("DJ", "Djibouti",                  "Džibuti"),
        new CountryDto("DK", "Denmark",                   "Danska"),
        new CountryDto("DM", "Dominica",                  "Dominika"),
        new CountryDto("DO", "Dominican Republic",        "Dominikanska Republika"),
        new CountryDto("DZ", "Algeria",                   "Alžir"),
        new CountryDto("EC", "Ecuador",                   "Ekvador"),
        new CountryDto("EE", "Estonia",                   "Estonija"),
        new CountryDto("EG", "Egypt",                     "Egipat"),
        new CountryDto("ER", "Eritrea",                   "Eritreja"),
        new CountryDto("ES", "Spain",                     "Španija"),
        new CountryDto("ET", "Ethiopia",                  "Etiopija"),
        new CountryDto("FI", "Finland",                   "Finska"),
        new CountryDto("FJ", "Fiji",                      "Fidži"),
        new CountryDto("FO", "Faroe Islands",             "Farska Ostrva"),
        new CountryDto("FR", "France",                    "Francuska"),
        new CountryDto("GA", "Gabon",                     "Gabon"),
        new CountryDto("GB", "United Kingdom",            "Velika Britanija"),
        new CountryDto("GD", "Grenada",                   "Grenada"),
        new CountryDto("GE", "Georgia",                   "Gruzija"),
        new CountryDto("GG", "Guernsey",                  "Gernzi"),
        new CountryDto("GH", "Ghana",                     "Gana"),
        new CountryDto("GM", "Gambia",                    "Gambija"),
        new CountryDto("GN", "Guinea",                    "Gvineja"),
        new CountryDto("GQ", "Equatorial Guinea",         "Ekvatorijalna Gvineja"),
        new CountryDto("GR", "Greece",                    "Grčka"),
        new CountryDto("GT", "Guatemala",                 "Gvatemala"),
        new CountryDto("GW", "Guinea-Bissau",             "Gvineja Bisao"),
        new CountryDto("GY", "Guyana",                    "Gvajana"),
        new CountryDto("HK", "Hong Kong",                 "Hong Kong"),
        new CountryDto("HN", "Honduras",                  "Honduras"),
        new CountryDto("HR", "Croatia",                   "Hrvatska"),
        new CountryDto("HT", "Haiti",                     "Haiti"),
        new CountryDto("HU", "Hungary",                   "Mađarska"),
        new CountryDto("ID", "Indonesia",                 "Indonezija"),
        new CountryDto("IE", "Ireland",                   "Irska"),
        new CountryDto("IL", "Israel",                    "Izrael"),
        new CountryDto("IM", "Isle of Man",               "Ostrvo Man"),
        new CountryDto("IN", "India",                     "Indija"),
        new CountryDto("IQ", "Iraq",                      "Irak"),
        new CountryDto("IR", "Iran",                      "Iran"),
        new CountryDto("IS", "Iceland",                   "Island"),
        new CountryDto("IT", "Italy",                     "Italija"),
        new CountryDto("JE", "Jersey",                    "Džerzi"),
        new CountryDto("JM", "Jamaica",                   "Jamajka"),
        new CountryDto("JO", "Jordan",                    "Jordan"),
        new CountryDto("JP", "Japan",                     "Japan"),
        new CountryDto("KE", "Kenya",                     "Kenija"),
        new CountryDto("KG", "Kyrgyzstan",                "Kirgistan"),
        new CountryDto("KH", "Cambodia",                  "Kambodža"),
        new CountryDto("KI", "Kiribati",                  "Kiribati"),
        new CountryDto("KM", "Comoros",                   "Komori"),
        new CountryDto("KN", "Saint Kitts and Nevis",     "Sent Kits i Nevis"),
        new CountryDto("KP", "North Korea",               "Severna Koreja"),
        new CountryDto("KR", "South Korea",               "Južna Koreja"),
        new CountryDto("KW", "Kuwait",                    "Kuvajt"),
        new CountryDto("KZ", "Kazakhstan",                "Kazahstan"),
        new CountryDto("LA", "Laos",                      "Laos"),
        new CountryDto("LB", "Lebanon",                   "Liban"),
        new CountryDto("LC", "Saint Lucia",               "Sveta Lucija"),
        new CountryDto("LI", "Liechtenstein",             "Lihtenštajn"),
        new CountryDto("LK", "Sri Lanka",                 "Šri Lanka"),
        new CountryDto("LR", "Liberia",                   "Liberija"),
        new CountryDto("LS", "Lesotho",                   "Lesoto"),
        new CountryDto("LT", "Lithuania",                 "Litvanija"),
        new CountryDto("LU", "Luxembourg",                "Luksemburg"),
        new CountryDto("LV", "Latvia",                    "Letonija"),
        new CountryDto("LY", "Libya",                     "Libija"),
        new CountryDto("MA", "Morocco",                   "Maroko"),
        new CountryDto("MC", "Monaco",                    "Monako"),
        new CountryDto("MD", "Moldova",                   "Moldavija"),
        new CountryDto("ME", "Montenegro",                "Crna Gora"),
        new CountryDto("MG", "Madagascar",                "Madagaskar"),
        new CountryDto("MH", "Marshall Islands",          "Maršalova Ostrva"),
        new CountryDto("MK", "North Macedonia",           "Severna Makedonija"),
        new CountryDto("ML", "Mali",                      "Mali"),
        new CountryDto("MM", "Myanmar",                   "Mjanmar"),
        new CountryDto("MN", "Mongolia",                  "Mongolija"),
        new CountryDto("MO", "Macau",                     "Makao"),
        new CountryDto("MR", "Mauritania",                "Mauritanija"),
        new CountryDto("MT", "Malta",                     "Malta"),
        new CountryDto("MU", "Mauritius",                 "Mauricijus"),
        new CountryDto("MV", "Maldives",                  "Maldivi"),
        new CountryDto("MW", "Malawi",                    "Malavi"),
        new CountryDto("MX", "Mexico",                    "Meksiko"),
        new CountryDto("MY", "Malaysia",                  "Malezija"),
        new CountryDto("MZ", "Mozambique",                "Mozambik"),
        new CountryDto("NA", "Namibia",                   "Namibija"),
        new CountryDto("NE", "Niger",                     "Niger"),
        new CountryDto("NG", "Nigeria",                   "Nigerija"),
        new CountryDto("NI", "Nicaragua",                 "Nikaragva"),
        new CountryDto("NL", "Netherlands",               "Holandija"),
        new CountryDto("NO", "Norway",                    "Norveška"),
        new CountryDto("NP", "Nepal",                     "Nepal"),
        new CountryDto("NR", "Nauru",                     "Nauru"),
        new CountryDto("NZ", "New Zealand",               "Novi Zeland"),
        new CountryDto("OM", "Oman",                      "Oman"),
        new CountryDto("PA", "Panama",                    "Panama"),
        new CountryDto("PE", "Peru",                      "Peru"),
        new CountryDto("PG", "Papua New Guinea",          "Papua Nova Gvineja"),
        new CountryDto("PH", "Philippines",               "Filipini"),
        new CountryDto("PK", "Pakistan",                  "Pakistan"),
        new CountryDto("PL", "Poland",                    "Poljska"),
        new CountryDto("PS", "Palestine",                 "Palestina"),
        new CountryDto("PT", "Portugal",                  "Portugalija"),
        new CountryDto("PW", "Palau",                     "Palau"),
        new CountryDto("PY", "Paraguay",                  "Paragvaj"),
        new CountryDto("QA", "Qatar",                     "Katar"),
        new CountryDto("RO", "Romania",                   "Rumunija"),
        new CountryDto("RS", "Serbia",                    "Srbija"),
        new CountryDto("RU", "Russia",                    "Rusija"),
        new CountryDto("RW", "Rwanda",                    "Ruanda"),
        new CountryDto("SA", "Saudi Arabia",              "Saudijska Arabija"),
        new CountryDto("SB", "Solomon Islands",           "Solomonova Ostrva"),
        new CountryDto("SC", "Seychelles",                "Sejšeli"),
        new CountryDto("SD", "Sudan",                     "Sudan"),
        new CountryDto("SE", "Sweden",                    "Švedska"),
        new CountryDto("SG", "Singapore",                 "Singapur"),
        new CountryDto("SI", "Slovenia",                  "Slovenija"),
        new CountryDto("SK", "Slovakia",                  "Slovačka"),
        new CountryDto("SL", "Sierra Leone",              "Sijera Leone"),
        new CountryDto("SM", "San Marino",                "San Marino"),
        new CountryDto("SN", "Senegal",                   "Senegal"),
        new CountryDto("SO", "Somalia",                   "Somalija"),
        new CountryDto("SR", "Suriname",                  "Surinam"),
        new CountryDto("SS", "South Sudan",               "Južni Sudan"),
        new CountryDto("ST", "São Tomé and Príncipe",     "Sao Tome i Principe"),
        new CountryDto("SV", "El Salvador",               "El Salvador"),
        new CountryDto("SY", "Syria",                     "Sirija"),
        new CountryDto("SZ", "Eswatini",                  "Esvatini"),
        new CountryDto("TD", "Chad",                      "Čad"),
        new CountryDto("TG", "Togo",                      "Togo"),
        new CountryDto("TH", "Thailand",                  "Tajland"),
        new CountryDto("TJ", "Tajikistan",                "Tadžikistan"),
        new CountryDto("TL", "Timor-Leste",               "Timor-Leste"),
        new CountryDto("TM", "Turkmenistan",              "Turkmenistan"),
        new CountryDto("TN", "Tunisia",                   "Tunis"),
        new CountryDto("TO", "Tonga",                     "Tonga"),
        new CountryDto("TR", "Turkey",                    "Turska"),
        new CountryDto("TT", "Trinidad and Tobago",       "Trinidad i Tobago"),
        new CountryDto("TV", "Tuvalu",                    "Tuvalu"),
        new CountryDto("TW", "Taiwan",                    "Tajvan"),
        new CountryDto("TZ", "Tanzania",                  "Tanzanija"),
        new CountryDto("UA", "Ukraine",                   "Ukrajina"),
        new CountryDto("UG", "Uganda",                    "Uganda"),
        new CountryDto("US", "United States",             "SAD"),
        new CountryDto("UY", "Uruguay",                   "Urugvaj"),
        new CountryDto("UZ", "Uzbekistan",                "Uzbekistan"),
        new CountryDto("VA", "Vatican City",              "Vatikan"),
        new CountryDto("VC", "Saint Vincent and the Grenadines", "Sveti Vinsent i Grenadini"),
        new CountryDto("VE", "Venezuela",                 "Venecuela"),
        new CountryDto("VN", "Vietnam",                   "Vijetnam"),
        new CountryDto("VU", "Vanuatu",                   "Vanuatu"),
        new CountryDto("WS", "Samoa",                     "Samoa"),
        new CountryDto("XK", "Kosovo",                    "Kosovo"),
        new CountryDto("YE", "Yemen",                     "Jemen"),
        new CountryDto("ZA", "South Africa",              "Južna Afrika"),
        new CountryDto("ZM", "Zambia",                    "Zambija"),
        new CountryDto("ZW", "Zimbabwe",                  "Zimbabve")
    );

    @Override
    @Cacheable("destinations")
    public List<Destination> getAllDestinations() {
        return destinationRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Cacheable(value = "destinations-by-airport", key = "#airport ?: 'all'")
    public List<Destination> getDestinationsByAirport(String airport) {
        if (airport != null && !airport.isBlank()) {
            return destinationRepository.findByDepartureAirportOrderByNameAsc(airport.trim().toUpperCase());
        }
        return destinationRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Cacheable("countries")
    public List<CountryDto> fetchCountries() {
        return PASSPORTS;
    }
}
