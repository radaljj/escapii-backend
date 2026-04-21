package com.escapii.config;

import com.escapii.service.email.core.EmailHtmlBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Inicijalizuje logo URL-ove u EmailHtmlBuilder na osnovu app.backend-url propertija.
 * Ovo omogućava da se logo URL konfigurišeputem env varijable BACKEND_URL,
 * a da EmailHtmlBuilder ostane statički utility bez Spring zavisnosti.
 */
@Slf4j
@Component
public class EmailHtmlBuilderConfig {

    @Value("${app.backend-url:https://escapii-backend.onrender.com}")
    private String backendUrl;

    @PostConstruct
    void init() {
        String base = backendUrl.stripTrailing().replaceAll("/$", "");
        EmailHtmlBuilder.LOGO_WHITE_URL = base + "/images/logo-white.png";
        EmailHtmlBuilder.LOGO_BLACK_URL = base + "/images/logo-black.png";
        log.info("[EmailHtmlBuilder] Logo URL: {}/images/logo-white.png", base);
    }
}
