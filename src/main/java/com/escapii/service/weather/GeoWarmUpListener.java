package com.escapii.service.weather;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Geokodira destinaciju čim je admin unese - u pozadini (@Async), pa admin ne čeka
 * geokoder, a jutarnji krug nađe koordinate u kešu i ne zavisi od Nominatima.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeoWarmUpListener {

    private final WeatherService weatherService;

    @Async
    @EventListener
    public void naDodeluDestinacije(DestinationAssignedEvent event) {
        if (event.weatherQuery() == null || event.weatherQuery().isBlank()) return;
        try {
            weatherService.warmUp(event.weatherQuery());
        } catch (Exception e) {
            log.warn("[Weather] Predgrevanje koordinata za '{}' nije uspelo: {}", event.weatherQuery(), e.toString());
        }
    }
}
