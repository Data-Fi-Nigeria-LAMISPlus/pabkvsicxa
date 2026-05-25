package com.lamiplus_common_api.api;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Base class for all plugin data services.
 *
 * Provides:
 *   - Auto-registration with the common-api registry on startup
 *   - Default implementations for all CRUD operations
 *   - Default routing for named method calls (executeMethod, executeListMethod, executeBooleanMethod)
 *   - Type conversion helpers for safe Map → Entity field extraction
 *
 * USAGE:
 *   Extend this class, implement getPluginId(), getEntityName(),
 *   and override the methods your plugin needs to expose.
 *
 *   For named method calls (pluginBridge.service("X").call("methodName", dto)):
 *   Override executeMethod(), executeListMethod(), or executeBooleanMethod()
 *   with a switch statement routing method names to your real service methods.
 */
@Slf4j
public abstract class AbstractPluginDataService implements PluginDataService {

    protected final PluginDataServiceRegistry dataServiceRegistry;

    /** Unique plugin identifier (e.g., "admission-plugin", "encounter-plugin") */
    protected abstract String getPluginId();

    @Override
    public abstract String getEntityName();

    protected AbstractPluginDataService(@Nullable PluginDataServiceRegistry dataServiceRegistry) {
        this.dataServiceRegistry = dataServiceRegistry;
    }

    @PostConstruct
    public void register() {
        if (dataServiceRegistry != null) {
            dataServiceRegistry.register(getPluginId(), getEntityName(), this);
            log.info("Registered [{}/{}] → {} | exposed methods: {}",
                    getPluginId(), getEntityName(),
                    getClass().getSimpleName(), getExposedMethods());
        } else {
            log.warn("Registry unavailable, could not register [{}/{}]",
                    getPluginId(), getEntityName());
        }
    }

    // ================================================================
    //  NAMED METHOD CALLS — override these in your subclass
    //
    //  Example in EncounterPluginDataService:
    //
    //  @Override
    //  public Map<String, Object> executeMethod(String methodName, Map<String, Object> params) {
    //      return switch (methodName) {
    //          case "saveEncounter"   -> encounterService.saveEncounter(params);
    //          case "cancelEncounter" -> encounterService.cancelEncounter(params);
    //          default -> super.executeMethod(methodName, params);  // throws UnsupportedOperationException
    //      };
    //  }
    // ================================================================

    @Override
    public Map<String, Object> executeMethod(String methodName, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                getEntityName() + " does not expose method: " + methodName
                        + ". Available methods: " + getExposedMethods());
    }

    @Override
    public List<Map<String, Object>> executeListMethod(String methodName, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                getEntityName() + " does not expose list method: " + methodName
                        + ". Available methods: " + getExposedMethods());
    }

    @Override
    public boolean executeBooleanMethod(String methodName, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                getEntityName() + " does not expose boolean method: " + methodName
                        + ". Available methods: " + getExposedMethods());
    }

    @Override
    public List<String> getExposedMethods() {
        return Collections.emptyList();
    }

    // ================================================================
    //  GENERIC CRUD — override as needed
    // ================================================================

    @Override
    public Optional<Map<String, Object>> findByUuid(UUID uuid) {
        return Optional.empty();
    }

    @Override
    public List<Map<String, Object>> findByUuids(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> results = new ArrayList<>();
        for (UUID uuid : uuids) {
            findByUuid(uuid).ifPresent(results::add);
        }
        return results;
    }

    @Override
    public Optional<Object> findByObjectUuid(UUID uuid) {
        return Optional.empty();
    }

    @Override
    public List<Map<String, Object>> findByPatientUuid(UUID patientUuid) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> findByTenantId(String tenantId) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> findByPatientUuidAndTenantId(UUID patientUuid, String tenantId) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> findAll(String tenantId, int page, int size) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> save(Map<String, Object> data) {
        throw new UnsupportedOperationException(
                "save() not implemented for " + getEntityName() + " in " + getClass().getSimpleName());
    }

    @Override
    public List<Map<String, Object>> saveAll(List<Map<String, Object>> dataList) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> data : dataList) {
            results.add(save(data));
        }
        return results;
    }

    @Override
    public boolean deleteByUuid(UUID uuid) {
        return false;
    }

    @Override
    public List<Map<String, Object>> findByField(String fieldName, Object value) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> findByFields(Map<String, Object> criteria) {
        return Collections.emptyList();
    }

    // ========================
    // TYPE CONVERSION HELPERS
    // ========================

    protected UUID toUuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID) return (UUID) value;
        String str = value.toString().trim();
        if (str.isEmpty()) return null;
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID: '{}'", value);
            return null;
        }
    }

    protected Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        String str = value.toString().trim();
        if (str.isEmpty()) return null;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer: '{}'", value);
            return null;
        }
    }

    protected Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        String str = value.toString().trim();
        if (str.isEmpty()) return null;
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            log.warn("Invalid long: '{}'", value);
            return null;
        }
    }

    protected String toString(Object value) {
        return value != null ? value.toString() : null;
    }

    protected Boolean toBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        String str = value.toString().trim().toLowerCase();
        if (str.isEmpty()) return null;
        return "true".equals(str) || "1".equals(str) || "yes".equals(str);
    }

    protected Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        String str = value.toString().trim();
        if (str.isEmpty()) return null;
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            log.warn("Invalid double: '{}'", value);
            return null;
        }
    }

    protected LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate) return (LocalDate) value;
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception e) {
            log.warn("Invalid date: '{}'", value);
            return null;
        }
    }

    protected LocalTime toLocalTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalTime) return (LocalTime) value;
        try {
            return LocalTime.parse(value.toString());
        } catch (Exception e) {
            log.warn("Invalid time: '{}'", value);
            return null;
        }
    }

    protected LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        try {
            return LocalDateTime.parse(value.toString());
        } catch (Exception e) {
            log.warn("Invalid datetime: '{}'", value);
            return null;
        }
    }

    // ========================
    // VALIDATION HELPERS
    // ========================

    /**
     * Validate that required fields are present in the data map.
     * Throws IllegalArgumentException listing all missing fields.
     */
    protected void validateRequired(Map<String, Object> data, String... fields) {
        List<String> missing = new ArrayList<>();
        for (String field : fields) {
            if (!data.containsKey(field) || data.get(field) == null) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    getEntityName() + " missing required fields: " + missing);
        }
    }
}