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
            // statična lista pasosa — ne menja se nikad
            buildCache("countries",           24, TimeUnit.HOURS),
            // sve destinacije — admin menja retko, keš duži da prvi korisnik ne čeka
            buildCache("destinations",        30, TimeUnit.MINUTES),
            // aktivne destinacije po aerodromu
            buildCache("active-destinations", 30, TimeUnit.MINUTES),
            // aktivni termini — admin menja retko, @CacheEvict čisti odmah kad se promeni
            buildCache("active-dates",        15, TimeUnit.MINUTES)
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, long duration, TimeUnit unit) {
        return new CaffeineCache(name,
            Caffeine.newBuilder()
                .expireAfterWrite(duration, unit)
                .maximumSize(200)
                .build());
    }
}
