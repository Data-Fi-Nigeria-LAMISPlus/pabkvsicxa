package com.lamiplus_common_api.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * A generic service to manage cache operations dynamically.
 * Caches are created on-the-fly when first accessed.
 * Any module can inject this service to perform caching.
 */
@Service
public class GenericCacheService {

    private static final Logger log = LoggerFactory.getLogger(GenericCacheService.class);
    private final CacheManager cacheManager;

    public GenericCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Retrieves a value from the specified cache.
     *
     * @param cacheName The name of the cache.
     * @param key       The key of the entry to retrieve.
     * @param <T>       The expected type of the cached value.
     * @return An Optional containing the value if found, otherwise an empty Optional.
     */
    public <T> Optional<T> getValue(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            Cache.ValueWrapper valueWrapper = cache.get(key);
            if (valueWrapper != null) {
                // This is an unchecked cast. The caller is responsible for knowing
                // the type of object they expect to retrieve from the cache.
                @SuppressWarnings("unchecked")
                T value = (T) valueWrapper.get();
                return Optional.ofNullable(value);
            }
        }
        return Optional.empty();
    }

    /**
     * Puts a value into the specified cache. If the cache does not exist, it will be created.
     *
     * @param cacheName The name of the cache.
     * @param key       The key of the entry.
     * @param value     The value to be cached.
     */
    public void putValue(String cacheName, Object key, Object value) {
        log.debug("Caching value for key '{}' in cache '{}'", key, cacheName);
        Cache cache = Objects.requireNonNull(cacheManager.getCache(cacheName), "Cache not found and could not be created: " + cacheName);
        cache.put(key, value);
    }

    /**
     * Evicts a single entry from the specified cache.
     *
     * @param cacheName The name of the cache.
     * @param key       The key of the entry to evict.
     */
    public void evictValue(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            log.info("Evicting key '{}' from cache '{}'", key, cacheName);
            cache.evict(key);
        }
    }

    /**
     * Clears all entries from the specified cache.
     *
     * @param cacheName The name of the cache to clear.
     */
    public void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            log.info("Clearing all entries from cache '{}'", cacheName);
            cache.clear();
        }
    }
}