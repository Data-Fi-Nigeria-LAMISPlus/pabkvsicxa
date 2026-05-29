package com.lamiplus_common_api.audit;

import com.lamiplus_common_api.common.Utils;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@SuppressWarnings("java:S3011") // Reflection access is intentional for auditing
public class UniversalAuditListener {

    @PrePersist
    public void prePersist(Object entity) {
        applyAudit(entity, true);
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        applyAudit(entity, false);
    }

    private void applyAudit(Object entity, boolean isCreate) {
        String currentUser = getCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        for (Field field : entity.getClass().getDeclaredFields()) {
            field.setAccessible(true);

            try {
                handleUUID(entity, field, isCreate);
                handleCreatedBy(entity, field, isCreate, currentUser);
                handleCreatedDate(entity, field, isCreate, now);
                handleUpdatedBy(entity, field, isCreate, currentUser);
                handleUpdatedDate(entity, field, isCreate, now);
                handleArchived(entity, field, isCreate);
                handleTenant(entity, field, isCreate);
                handleFacility(entity, field, isCreate);

            } catch (IllegalAccessException e) {
                throw new AuditProcessingException(
                        "Audit processing failed for field: " + field.getName(),
                        e
                );
            }
        }
    }

    // ---------------- HANDLERS ----------------

    private void handleUUID(Object entity, Field field, boolean isCreate) throws IllegalAccessException {
        if (!field.isAnnotationPresent(AuditUUID.class)) return;

        AuditUUID ann = field.getAnnotation(AuditUUID.class);
        Object value = field.get(entity);

        if (isCreate) {
            if (value == null && ann.required()) {
                throw new IllegalStateException("UUID field '" + field.getName() + "' is required but null");
            }
            if (value == null) {
                field.set(entity, UUID.randomUUID());
            }
        }
    }

    private void handleCreatedBy(Object entity, Field field, boolean isCreate, String currentUser) throws IllegalAccessException {
        if (!field.isAnnotationPresent(AuditCreatedBy.class)) return;

        AuditCreatedBy ann = field.getAnnotation(AuditCreatedBy.class);
        Object value = field.get(entity);

        if (isCreate) {
            if (value == null && ann.required()) {
                throw new IllegalStateException("CreatedBy field '" + field.getName() + "' is required but null");
            }
            if (value == null) {
                field.set(entity, currentUser);
            }
        }
    }

    private void handleCreatedDate(Object entity, Field field, boolean isCreate, LocalDateTime now) throws IllegalAccessException {
        if (!field.isAnnotationPresent(AuditCreatedDate.class)) return;

        AuditCreatedDate ann = field.getAnnotation(AuditCreatedDate.class);
        Object value = field.get(entity);

        if (isCreate) {
            if (value == null && ann.required()) {
                throw new IllegalStateException("CreatedDate field '" + field.getName() + "' is required but null");
            }
            if (value == null) {
                field.set(entity, now);
            }
        }
    }

    private void handleUpdatedBy(Object entity, Field field, boolean isCreate, String currentUser) throws IllegalAccessException {
        if (!field.isAnnotationPresent(AuditUpdatedBy.class)) return;

        AuditUpdatedBy ann = field.getAnnotation(AuditUpdatedBy.class);
        Object value = field.get(entity);

        if (!isCreate && ann.required() && value == null) {
            throw new IllegalStateException("UpdatedBy field '" + field.getName() + "' is required on update");
        }

        if (!isCreate || ann.updatable()) {
            field.set(entity, currentUser);
        }
    }

    private void handleUpdatedDate(Object entity, Field field, boolean isCreate, LocalDateTime now) throws IllegalAccessException {
        if (!field.isAnnotationPresent(AuditUpdatedDate.class)) return;

        AuditUpdatedDate ann = field.getAnnotation(AuditUpdatedDate.class);
        Object value = field.get(entity);
        if (!isCreate && ann.required() && value == null) {
            throw new IllegalStateException("UpdatedDate field '" + field.getName() + "' is required on update");
        }

        if (!isCreate || ann.updatable()) {
            field.set(entity, now);
        }
    }

    private void handleArchived(Object entity, Field field, boolean isCreate) throws IllegalAccessException {
        if (!field.isAnnotationPresent(AuditArchived.class)) return;

        AuditArchived ann = field.getAnnotation(AuditArchived.class);
        Object value = field.get(entity);

        if (isCreate) {
            if (value == null && ann.required()) {
                throw new IllegalStateException("Archived field '" + field.getName() + "' is required but null");
            }
            //use default on creation
            if (value == null) {
                field.set(entity, ann.defaultValue());
            }
        } else {
            if (ann.updatable() && value == null) {
                throw new IllegalStateException("Archived field '" + field.getName() + "' must not be null on update");
            }
        }
    }

    private void handleTenant(Object entity, Field field, boolean isCreate) throws IllegalAccessException {
        if (!field.isAnnotationPresent(AuditTenant.class) || !isCreate) return;

        AuditTenant ann = field.getAnnotation(AuditTenant.class);
        Object value = field.get(entity);

        if (value == null && ann.required()) {
            throw new IllegalStateException("Tenant field '" + field.getName() + "' is required but null");
        }
        if (value == null) {
            field.set(entity, getTenantId());
        }
    }

    private void handleFacility(Object entity, Field field, boolean isCreate) throws IllegalAccessException {
        if (!field.isAnnotationPresent(AuditFacility.class) || !isCreate) return;

        AuditFacility ann = field.getAnnotation(AuditFacility.class);
        Object value = field.get(entity);

        if (value == null && ann.required()) {
            throw new IllegalStateException("Facility field '" + field.getName() + "' is required but null");
        }
        if (value == null) {
            field.set(entity, getFacilityId());
        }
    }

    private String getCurrentUser() {
        return Utils.getCurrentUserEmail() != null && !Utils.getCurrentUserEmail().isEmpty()
                ? Utils.getCurrentUserEmail()
                : null;
    }

    private String getTenantId() {
        return Utils.getTenantIdFromContext();
    }

    private UUID getFacilityId() {
        return Utils.getFacilityIdFromContext();
    }
}