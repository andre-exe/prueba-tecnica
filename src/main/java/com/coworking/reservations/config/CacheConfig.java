package com.coworking.reservations.config;

import com.coworking.reservations.config.properties.ReportCacheProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String OCCUPANCY_REPORT = "occupancyReport";

    @Bean
    CacheManager cacheManager(ReportCacheProperties properties) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(OCCUPANCY_REPORT);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(properties.ttl())
                .maximumSize(properties.maxSize()));
        return cacheManager;
    }
}
