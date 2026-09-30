package com.escapii.service;

import com.escapii.model.Destination;
import com.escapii.repository.DestinationRepository;
import com.escapii.service.impl.PartnerSlugFiller;
import com.escapii.service.impl.TravelAddonsService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Podrazumevani Holafly šablon stoji u application.properties (link je javan, pa ne mora
 * na server kao env). Zamka: Spring rešava {@code ${ENV:podrazumevano}}, a u podrazumevanoj
 * vrednosti stoji {@code {slug}} sa vitičastim zagradama - ovde se proverava da ga Spring
 * ne poždere i da iz šablona izađe link koji Impact preusmerava na stranicu države.
 */
class HolaflyTemplatePropertyTest {

    /** Isti mehanizam kojim Spring Boot rešava placeholder-e, bez env varijabli. */
    private static String sablonIzProperties() throws Exception {
        Properties p = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        MutablePropertySources izvori = new MutablePropertySources();
        izvori.addLast(new PropertiesPropertySource("application", p));
        return new PropertySourcesPropertyResolver(izvori).getProperty("app.affiliate.holafly-url-template");
    }

    @Test
    void podrazumevaniSablonPrezivljavaSpringovoResavanje() throws Exception {
        String sablon = sablonIzProperties();

        assertNotNull(sablon);
        assertTrue(sablon.startsWith("https://holafly.sjv.io/c/"), sablon);
        assertTrue(sablon.contains("{slug}"), "rupa za slug mora ostati: " + sablon);
        assertFalse(sablon.contains("${"), "nerešen placeholder: " + sablon);
    }

    @Test
    void linkVodiNaStranicuDrzaveSaNasimImpactNalogom() throws Exception {
        DestinationRepository repo = mock(DestinationRepository.class);
        TravelAddonsService service = new TravelAddonsService(repo, mock(PartnerSlugFiller.class));
        ReflectionTestUtils.setField(service, "holaflyTemplate", sablonIzProperties());
        Destination d = new Destination();
        d.setId(1L);
        d.setName("Firenca");
        d.setNameEn("Florence");
        d.setCountryEn("Italy");
        d.setHolaflySlug("esim-italy");
        when(repo.findByAnyNameIgnoreCase(anyString())).thenReturn(List.of(d));

        String link = service.linksFor("Firenca").get("esim");

        assertNotNull(link, "bez linka nema kartice");
        URI uri = URI.create(link);
        assertEquals("holafly.sjv.io", uri.getHost());
        assertTrue(uri.getPath().startsWith("/c/7733733/"), "naš Impact nalog: " + uri.getPath());
        String u = uri.getRawQuery().replaceFirst("^u=", "");
        assertEquals("https://esim.holafly.com/esim-italy/", URLDecoder.decode(u, StandardCharsets.UTF_8),
                "odredište je stranica države, sa kosom crtom kao u njihovom sitemapu");
    }
}
