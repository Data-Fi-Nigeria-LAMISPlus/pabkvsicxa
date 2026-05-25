package com.lamiplus_common_api.exception;

public class PluginServiceUnavailableException extends RuntimeException {

    private final String entityName;

    public PluginServiceUnavailableException(String entityName) {
        super("Plugin service unavailable for entity: " + entityName);
        this.entityName = entityName;
    }

    public String getEntityName() {
        return entityName;
    }
}
