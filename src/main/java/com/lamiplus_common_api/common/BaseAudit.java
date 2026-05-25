package com.lamiplus_common_api.common;

import com.lamiplus_common_api.common.Utils;
import jakarta.persistence.*;
import jakarta.persistence.Id;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID uuid;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "archived")
    @ColumnDefault("0")
    private Integer archived = 0;

    @Column(name = "tenant_id", length = 50)
    private String tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @PrePersist
    public void prePersist() {
        if (this.tenantId == null) {
            this.tenantId = Utils.getTenantIdFromContext();
        }
        if (this.facilityId == null) {
            this.facilityId = Utils.getFacilityIdFromContext();
        }
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
        if (this.archived == null) {
            this.archived = 0;
        }
    }
}