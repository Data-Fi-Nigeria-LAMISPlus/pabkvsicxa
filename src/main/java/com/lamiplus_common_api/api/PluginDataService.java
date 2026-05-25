package com.lamiplus_common_api.api;

import java.util.*;

/**
 * Contract that each plugin's SERVICE registers with the common-api registry.
 *
 * KEY DESIGN RULE:
 *   The implementing class MUST be the plugin's actual service (or a thin adapter
 *   that delegates to it), NOT a raw repository wrapper. This ensures every
 *   cross-plugin call goes through validation, business rules, audit logging, etc.
 *
 * TWO WAYS TO CALL:
 *
 *   1. GENERIC CRUD — save(), update(), findByUuid(), etc.
 *      These are standard operations every entity supports.
 *
 *   2. NAMED METHOD CALLS — executeMethod("saveEncounter", params)
 *      This lets the caller invoke a SPECIFIC method on the target service
 *      by name, passing the exact parameters that method expects.
 *      The target plugin maps method names to actual service methods.
 *
 *      Example:
 *        Admission plugin calls:
 *          pluginBridge.service("Encounter")
 *              .call("saveEncounter", encounterDto)
 *
 *        Encounter plugin routes it to:
 *          encounterService.saveEncounter(data)
 */
public interface PluginDataService {

    /** The entity/service name this service manages (e.g., "Encounter", "Diagnosis") */
    String getEntityName();

    // ================================================================
    //  NAMED METHOD CALLS — the primary cross-plugin communication API
    // ================================================================

    /**
     * Execute a named service method with parameters.
     *
     * The implementing plugin maps methodName to its actual service method.
     * This is the MAIN way plugins call each other's service methods.
     *
     * @param methodName the service method to invoke
     * @param params     method parameters (serialized from caller's DTO)
     * @return result as Map<String, Object>
     */
    default Map<String, Object> executeMethod(String methodName, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                getEntityName() + " does not expose method: " + methodName);
    }

    /**
     * Execute a named method that returns a list.
     */
    default List<Map<String, Object>> executeListMethod(String methodName, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                getEntityName() + " does not expose list method: " + methodName);
    }

    /**
     * Execute a named method that returns a boolean.
     */
    default boolean executeBooleanMethod(String methodName, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                getEntityName() + " does not expose boolean method: " + methodName);
    }

    /**
     * Returns the list of method names this service exposes.
     * Useful for debugging and discovery.
     */
    default List<String> getExposedMethods() {
        return Collections.emptyList();
    }

    // ================================================================
    //  GENERIC CRUD
    // ================================================================

    default Map<String, Object> save(Map<String, Object> data) {
        throw new UnsupportedOperationException(
                getEntityName() + " does not support generic save()");
    }

    default List<Map<String, Object>> saveAll(List<Map<String, Object>> dataList) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> data : dataList) {
            results.add(save(data));
        }
        return results;
    }

    default Map<String, Object> update(UUID uuid, Map<String, Object> data) {
        throw new UnsupportedOperationException(
                getEntityName() + " does not support generic update()");
    }

    default boolean deleteByUuid(UUID uuid) {
        return false;
    }

    default Optional<Map<String, Object>> findByUuid(UUID uuid) {
        return Optional.empty();
    }

    default Optional<Object> findByObjectUuid(UUID uuid) {
        return Optional.empty();
    }

    default List<Map<String, Object>> findByUuids(List<UUID> uuids) {
        return Collections.emptyList();
    }

    default List<Map<String, Object>> findByPatientUuid(UUID patientUuid) {
        return Collections.emptyList();
    }

    default List<Map<String, Object>> findByTenantId(String tenantId) {
        return Collections.emptyList();
    }

    default List<Map<String, Object>> findByPatientUuidAndTenantId(UUID patientUuid, String tenantId) {
        return Collections.emptyList();
    }

    default List<Map<String, Object>> findByField(String fieldName, Object value) {
        return Collections.emptyList();
    }

    default List<Map<String, Object>> findByFields(Map<String, Object> criteria) {
        return Collections.emptyList();
    }

    default List<Map<String, Object>> findAll(String tenantId, int page, int size) {
        return Collections.emptyList();
    }
}