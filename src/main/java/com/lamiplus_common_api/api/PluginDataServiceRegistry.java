package com.lamiplus_common_api.api;

import java.util.Optional;

/**
 * Registry that holds all PluginDataService implementations.
 * Each plugin registers its service(s) at startup with pluginId + entityName.
 */
public interface PluginDataServiceRegistry {

    void register(String pluginId, String entityName, PluginDataService service);

    void unregister(String entityName);

    Optional<PluginDataService> getServiceByEntity(String entityName);

    boolean hasService(String entityName);
}