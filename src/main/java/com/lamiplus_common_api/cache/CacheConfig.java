package com.lamiplus_common_api.cache;


import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

/**
 * Configures the application's caching mechanism.
 * <p>
 * This configuration is conditional and allows switching between an in-memory cache (Caffeine)
 * and a distributed cache (Redis) by setting the {@code caching.provider} property
 * in {@code application.properties}.
 * <ul>
 *     <li><b>caching.provider=caffeine</b> (default): Uses a fast, in-memory cache.</li>
 *     <li><b>caching.provider=redis</b>: Uses a Redis server for distributed caching.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configures Caffeine as the cache provider.
     * This configuration is active when 'caching.provider=caffeine' or if the property is not set.
     */
    @Configuration
    @ConditionalOnProperty(name = "caching.provider", havingValue = "caffeine", matchIfMissing = true)
    static class CaffeineCacheConfiguration {

        @Value("${caching.caffeine.expire-minutes:60}")
        private long expireMinutes;

        @Value("${caching.caffeine.initial-capacity:100}")
        private int initialCapacity;

        @Value("${caching.caffeine.max-size:500}")
        private long maxSize;

        @Bean
        public Caffeine<Object, Object> caffeineConfig() {
            return Caffeine.newBuilder()
                    .expireAfterWrite(expireMinutes, TimeUnit.MINUTES)
                    .initialCapacity(initialCapacity)
                    .maximumSize(maxSize);
        }

        @Bean
        public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
            CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
            caffeineCacheManager.setCaffeine(caffeine);
            // Allows dynamic creation of caches
            return caffeineCacheManager;
        }
    }

    /**
     * Configures Redis as the cache provider.
     * This configuration is active only when 'caching.provider=redis'.
     */
}