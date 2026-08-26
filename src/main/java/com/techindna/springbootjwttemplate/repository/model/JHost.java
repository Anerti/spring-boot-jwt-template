package com.techindna.springbootjwttemplate.repository.model;

import com.techindna.springbootjwttemplate.entity.enums.HostStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "host")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JHost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ip_address", nullable = false, length = 255)
    private String ipAddress;

    @Column(name = "user_agent", nullable = false, length = 512)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private HostStatus status = HostStatus.INACTIVE;

    @Column(name = "login_failed", nullable = false)
    @Builder.Default
    private Boolean loginFailed = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
