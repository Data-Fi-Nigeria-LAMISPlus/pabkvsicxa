package com.lamiplus_common_api.api;

import com.lamiplus_common_api.common.DtoMapper;
import com.lamiplus_common_api.exception.PluginServiceUnavailableException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PluginBridge — the single gateway for cross-plugin communication.
 *
 * =====================================================================
 *  THREE WAYS TO USE:
 * =====================================================================
 *
 *  1. SERVICE METHOD CALL (recommended for most cases):
 *     Call a specific method on the target plugin's service.
 *
 *     pluginBridge.service("Encounter")
 *         .call("saveEncounter", myDto)             → Map<String, Object>
 *         .callSafe("saveEncounter", myDto)          → Optional<Map>
 *         .callAs("saveEncounter", myDto, Resp.class) → typed response
 *         .callSafeAs("saveEncounter", myDto, R.class) → Optional<typed>
 *
 *     pluginBridge.service("Encounter")
 *         .callList("getByVisit", myDto)             → List<Map>
 *         .callListAs("getByVisit", myDto, R.class)  → List<typed>
 *
 *     pluginBridge.service("Encounter")
 *         .callBoolean("hasActiveEncounter", myDto)  → boolean
 *
 *  2. GENERIC CRUD (for simple standard operations):
 *     pluginBridge.save("Encounter", dto).executeSafe()
 *     pluginBridge.find("Encounter").byPatient(patientUuid)
 *     pluginBridge.update("Encounter", uuid).field("status", "CLOSED").execute()
 *     pluginBridge.delete("Encounter").byUuid(uuid)
 *
 *  3. HYBRID (DTO + extra fields):
 *     pluginBridge.save("Encounter", dto)
 *         .field("extraField", value)    // add fields on top of DTO
 *         .executeSafe()
 */
@Slf4j
@Component
public class PluginBridge {

    private PluginDataServiceRegistry registry;

    @Autowired(required = false)
    @Qualifier("corePluginDataServiceRegistry")
    public void setRegistry(PluginDataServiceRegistry registry) {
        this.registry = registry;
        log.info("PluginBridge initialized with registry: {}", registry != null);
    }

    // ================================================================
    //  ENTRY POINTS
    // ================================================================

    /**
     * Get a ServiceCaller for a target plugin — call its methods directly.
     * This is the RECOMMENDED way to communicate between plugins.
     *
     * Example:
     *   pluginBridge.service("Encounter").call("saveEncounter", myDto);
     */
    public ServiceCaller service(String entityName) {
        return new ServiceCaller(entityName);
    }

    /** Start a find (read) operation */
    public FindBuilder find(String entityName) {
        return new FindBuilder(entityName);
    }

    /** Start a save (create) with fields or DTO */
    public SaveBuilder save(String entityName) {
        return new SaveBuilder(entityName);
    }

    /** Start a save with a pre-built DTO */
    public SaveBuilder save(String entityName, Object dto) {
        SaveBuilder builder = new SaveBuilder(entityName);
        builder.fromDto(dto);
        return builder;
    }

    /** Start an update operation */
    public UpdateBuilder update(String entityName, UUID uuid) {
        return new UpdateBuilder(entityName, uuid);
    }

    /** Start an update with a pre-built DTO */
    public UpdateBuilder update(String entityName, UUID uuid, Object dto) {
        UpdateBuilder builder = new UpdateBuilder(entityName, uuid);
        builder.fromDto(dto);
        return builder;
    }

    /** Start a delete operation */
    public DeleteBuilder delete(String entityName) {
        return new DeleteBuilder(entityName);
    }

    /** Check if a plugin's service is registered and available */
    public boolean isAvailable(String entityName) {
        return getService(entityName).isPresent();
    }

    // ================================================================
    //  SERVICE CALLER — call specific methods on target plugin's service
    // ================================================================

    public class ServiceCaller {
        private final String entityName;

        ServiceCaller(String entityName) {
            this.entityName = entityName;
        }

        /**
         * Call a named method on the target plugin's service.
         * The DTO is serialized to Map and passed as parameters.
         *
         * @param methodName the service method to call (e.g., "saveEncounter")
         * @param dto        any POJO with the method's parameters
         * @return the method's result as Map<String, Object>
         * @throws PluginServiceUnavailableException if plugin not registered
         */
        public Map<String, Object> call(String methodName, Object dto) {
            Map<String, Object> params = DtoMapper.toMap(dto);
            return getService(entityName)
                    .map(s -> {
                        log.debug("PluginBridge calling {}.{} with params: {}",
                                entityName, methodName, params.keySet());
                        Map<String, Object> result = s.executeMethod(methodName, params);
                        log.info("PluginBridge {}.{} completed", entityName, methodName);
                        return result;
                    })
                    .orElseThrow(() -> new PluginServiceUnavailableException(entityName));
        }

        /** Call without a DTO (for no-arg methods or methods that only need the name) */
        public Map<String, Object> call(String methodName) {
            return call(methodName, Collections.emptyMap());
        }

        /** Safe version — returns Optional.empty() if plugin unavailable */
        public Optional<Map<String, Object>> callSafe(String methodName, Object dto) {
            try {
                return Optional.of(call(methodName, dto));
            } catch (PluginServiceUnavailableException e) {
                log.warn("Service unavailable for {}, skipping {}", entityName, methodName);
                return Optional.empty();
            }
        }

        public Optional<Map<String, Object>> callSafe(String methodName) {
            return callSafe(methodName, Collections.emptyMap());
        }

        /** Call and map result to a typed response DTO */
        public <T> T callAs(String methodName, Object dto, Class<T> responseType) {
            return DtoMapper.fromMap(call(methodName, dto), responseType);
        }

        /** Safe + typed response */
        public <T> Optional<T> callSafeAs(String methodName, Object dto, Class<T> responseType) {
            return callSafe(methodName, dto)
                    .map(map -> DtoMapper.fromMap(map, responseType));
        }

        public <T> Optional<T> callSafeAs(String methodName, Class<T> responseType) {
            return callSafeAs(methodName, Collections.emptyMap(), responseType);
        }

        // ---- LIST methods ----

        /** Call a method that returns a list */
        public List<Map<String, Object>> callList(String methodName, Object dto) {
            Map<String, Object> params = DtoMapper.toMap(dto);
            return getService(entityName)
                    .map(s -> {
                        log.debug("PluginBridge calling {}.{} (list) with params: {}",
                                entityName, methodName, params.keySet());
                        return s.executeListMethod(methodName, params);
                    })
                    .orElse(Collections.emptyList());
        }

        public List<Map<String, Object>> callList(String methodName) {
            return callList(methodName, Collections.emptyMap());
        }

        /** Call list method and map each result to a typed DTO */
        public <T> List<T> callListAs(String methodName, Object dto, Class<T> responseType) {
            return callList(methodName, dto).stream()
                    .map(map -> DtoMapper.fromMap(map, responseType))
                    .collect(Collectors.toList());
        }

        public <T> List<T> callListAs(String methodName, Class<T> responseType) {
            return callListAs(methodName, Collections.emptyMap(), responseType);
        }

        // ---- BOOLEAN methods ----

        /** Call a method that returns boolean (validation, checks, etc.) */
        public boolean callBoolean(String methodName, Object dto) {
            Map<String, Object> params = DtoMapper.toMap(dto);
            return getService(entityName)
                    .map(s -> {
                        log.debug("PluginBridge calling {}.{} (boolean) with params: {}",
                                entityName, methodName, params.keySet());
                        return s.executeBooleanMethod(methodName, params);
                    })
                    .orElse(false);
        }

        public boolean callBoolean(String methodName) {
            return callBoolean(methodName, Collections.emptyMap());
        }

        /** Get the list of methods this service exposes */
        public List<String> getExposedMethods() {
            return getService(entityName)
                    .map(PluginDataService::getExposedMethods)
                    .orElse(Collections.emptyList());
        }
    }

    // ================================================================
    //  FIND BUILDER
    // ================================================================

    public class FindBuilder {
        private final String entityName;

        FindBuilder(String entityName) {
            this.entityName = entityName;
        }

        public Optional<Map<String, Object>> byUuid(UUID uuid) {
            return getService(entityName).flatMap(s -> s.findByUuid(uuid));
        }

        public List<Map<String, Object>> byUuids(List<UUID> uuids) {
            return getService(entityName)
                    .map(s -> s.findByUuids(uuids))
                    .orElse(Collections.emptyList());
        }

        public List<Map<String, Object>> byPatient(UUID patientUuid) {
            return getService(entityName)
                    .map(s -> s.findByPatientUuid(patientUuid))
                    .orElse(Collections.emptyList());
        }

        public List<Map<String, Object>> byTenant(String tenantId) {
            return getService(entityName)
                    .map(s -> s.findByTenantId(tenantId))
                    .orElse(Collections.emptyList());
        }

        public List<Map<String, Object>> byPatientAndTenant(UUID patientUuid, String tenantId) {
            return getService(entityName)
                    .map(s -> s.findByPatientUuidAndTenantId(patientUuid, tenantId))
                    .orElse(Collections.emptyList());
        }

        public List<Map<String, Object>> byField(String fieldName, Object value) {
            return getService(entityName)
                    .map(s -> s.findByField(fieldName, value))
                    .orElse(Collections.emptyList());
        }

        public List<Map<String, Object>> paged(String tenantId, int page, int size) {
            return getService(entityName)
                    .map(s -> s.findAll(tenantId, page, size))
                    .orElse(Collections.emptyList());
        }

        public List<Map<String, Object>> byFieldFiltered(
                String fieldName, Object fieldValue,
                String filterField, String filterValue) {
            return byField(fieldName, fieldValue).stream()
                    .filter(d -> filterValue.equalsIgnoreCase(
                            String.valueOf(d.get(filterField))))
                    .collect(Collectors.toList());
        }

        public <T> List<T> byFieldAs(String fieldName, Object value, Class<T> responseType) {
            return byField(fieldName, value).stream()
                    .map(map -> DtoMapper.fromMap(map, responseType))
                    .collect(Collectors.toList());
        }

        public <T> Optional<T> byUuidAs(UUID uuid, Class<T> responseType) {
            return byUuid(uuid).map(map -> DtoMapper.fromMap(map, responseType));
        }
    }

    // ================================================================
    //  SAVE BUILDER
    // ================================================================

    public class SaveBuilder {
        private final String entityName;
        private final Map<String, Object> data = new LinkedHashMap<>();

        SaveBuilder(String entityName) {
            this.entityName = entityName;
        }

        public SaveBuilder fromDto(Object dto) {
            data.putAll(DtoMapper.toMap(dto));
            return this;
        }

        public SaveBuilder field(String key, Object value) {
            if (value != null) {
                data.put(key, value instanceof UUID ? value.toString() : value);
            }
            return this;
        }

        public SaveBuilder fields(Map<String, Object> fields) {
            if (fields != null) {
                fields.forEach(this::field);
            }
            return this;
        }

        public Map<String, Object> execute() {
            return getService(entityName)
                    .map(s -> {
                        log.debug("PluginBridge saving {} with fields: {}", entityName, data.keySet());
                        Map<String, Object> saved = s.save(data);
                        log.info("PluginBridge saved {}: {}", entityName, saved.get("uuid"));
                        return saved;
                    })
                    .orElseThrow(() -> new PluginServiceUnavailableException(entityName));
        }

        public Optional<Map<String, Object>> executeSafe() {
            try {
                return Optional.of(execute());
            } catch (PluginServiceUnavailableException e) {
                log.warn("Service unavailable for {}, skipping save", entityName);
                return Optional.empty();
            }
        }

        public <T> T executeAs(Class<T> responseType) {
            return DtoMapper.fromMap(execute(), responseType);
        }

        public <T> Optional<T> executeSafeAs(Class<T> responseType) {
            return executeSafe().map(map -> DtoMapper.fromMap(map, responseType));
        }
    }

    // ================================================================
    //  UPDATE BUILDER
    // ================================================================

    public class UpdateBuilder {
        private final String entityName;
        private final UUID uuid;
        private final Map<String, Object> data = new LinkedHashMap<>();

        UpdateBuilder(String entityName, UUID uuid) {
            this.entityName = entityName;
            this.uuid = uuid;
        }

        public UpdateBuilder fromDto(Object dto) {
            data.putAll(DtoMapper.toMap(dto));
            return this;
        }

        public UpdateBuilder field(String key, Object value) {
            if (value != null) {
                data.put(key, value instanceof UUID ? value.toString() : value);
            }
            return this;
        }

        public Map<String, Object> execute() {
            return getService(entityName)
                    .map(s -> {
                        log.debug("PluginBridge updating {} [{}] with fields: {}",
                                entityName, uuid, data.keySet());
                        Map<String, Object> updated = s.update(uuid, data);
                        log.info("PluginBridge updated {}: {}", entityName, uuid);
                        return updated;
                    })
                    .orElseThrow(() -> new PluginServiceUnavailableException(entityName));
        }

        public Optional<Map<String, Object>> executeSafe() {
            try {
                return Optional.of(execute());
            } catch (PluginServiceUnavailableException e) {
                log.warn("Service unavailable for {}, skipping update", entityName);
                return Optional.empty();
            }
        }

        public <T> Optional<T> executeSafeAs(Class<T> responseType) {
            return executeSafe().map(map -> DtoMapper.fromMap(map, responseType));
        }
    }

    // ================================================================
    //  DELETE BUILDER
    // ================================================================

    public class DeleteBuilder {
        private final String entityName;

        DeleteBuilder(String entityName) {
            this.entityName = entityName;
        }

        public boolean byUuid(UUID uuid) {
            return getService(entityName)
                    .map(s -> s.deleteByUuid(uuid))
                    .orElse(false);
        }
    }

    // ========================
    //  INTERNAL
    // ========================

    private Optional<PluginDataService> getService(String entityName) {
        if (registry == null) {
            log.warn("PluginBridge: registry not available, cannot access {}", entityName);
            return Optional.empty();
        }
        return registry.getServiceByEntity(entityName);
    }
}