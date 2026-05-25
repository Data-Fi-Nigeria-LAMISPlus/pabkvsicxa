package com.lamiplus_common_api.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component("corePluginDataServiceRegistry")
public class DefaultPluginDataServiceRegistry implements PluginDataServiceRegistry {

    private final ConcurrentHashMap<String, PluginDataService> services = new ConcurrentHashMap<>();

    @Override
    public void register(String pluginId, String entityName, PluginDataService service) {
        PluginDataService existing = services.put(entityName, service);
        if (existing != null) {
            log.warn("Replaced existing service for [{}/{}]: {} -> {}",
                    pluginId, entityName,
                    existing.getClass().getSimpleName(),
                    service.getClass().getSimpleName());
        } else {
            log.info("Registered plugin service [{}/{}]: {}",
                    pluginId, entityName, service.getClass().getSimpleName());
        }
    }

    @Override
    public void unregister(String entityName) {
        PluginDataService removed = services.remove(entityName);
        if (removed != null) {
            log.info("Unregistered plugin service for entity '{}'", entityName);
        }
    }

    @Override
    public Optional<PluginDataService> getServiceByEntity(String entityName) {
        return Optional.ofNullable(services.get(entityName));
    }

    @Override
    public boolean hasService(String entityName) {
        return services.containsKey(entityName);
    }
}