package com.escapii.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
            // statična lista pasosa - ne menja se nikad
            buildCache("countries",                24, TimeUnit.HOURS),
            // sve destinacije sortirane po imenu
            buildCache("destinations",             30, TimeUnit.MINUTES),
            // destinacije po aerodromu polaska (per-termin logika)
            buildCache("destinations-by-airport",  30, TimeUnit.MINUTES),
            // aktivni termini - admin menja retko, @CacheEvict čisti odmah kad se promeni
            buildCache("active-dates",             15, TimeUnit.MINUTES),
            // vokativ imena za "Zdravo, Uroše," - odgovor tudjeg servisa. Vokativ se
            // ne menja nikad, pa dug rok; promasaji (nepoznato ime -> nominativ) se
            // takodje cuvaju da isto ime ne ide na mrezu pri svakom mejlu.
            buildCache("vocatives",                 7, TimeUnit.DAYS, 5000)
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, long duration, TimeUnit unit) {
        return buildCache(name, duration, unit, 200);
    }

    private CaffeineCache buildCache(String name, long duration, TimeUnit unit, long maximumSize) {
        return new CaffeineCache(name,
            Caffeine.newBuilder()
                .expireAfterWrite(duration, unit)
                .maximumSize(maximumSize)
                .build());
    }
}
